package com.github.brcosta.cljstuffplugin.extensions

import clojure.java.api.Clojure
import clojure.lang.ClojureLoaderHolder
import clojure.lang.DynamicClassLoader
import clojure.lang.IFn
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.brcosta.cljstuffplugin.util.AppSettingsState
import com.github.brcosta.cljstuffplugin.util.kondo.Diagnostics
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.*
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import java.io.File
import java.io.IOException
import java.lang.reflect.Method
import java.net.URL

@Suppress("UnstableApiUsage")
class CljKondoAnnotator : ExternalAnnotator<ExternalLintAnnotationInput, ExternalLintAnnotationResult<List<String>>>() {

    init {
        initKondoDependencies()
    }

    private val mapper: ObjectMapper =
        ObjectMapper().registerKotlinModule().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    override fun collectInformation(file: PsiFile): ExternalLintAnnotationInput? {
        return collectInformation(file, null)
    }

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): ExternalLintAnnotationInput? {
        return collectInformation(file, editor)
    }

    override fun doAnnotate(collectedInfo: ExternalLintAnnotationInput): ExternalLintAnnotationResult<List<String>> {

        val settings = AppSettingsState.instance
        val cljkondoPath = settings.cljkondoPath
        val cljkondoEnabled = settings.cljkondoEnabled

        return when {
            !cljkondoEnabled -> ExternalLintAnnotationResult(collectedInfo, emptyList())
            else -> {
                when {
                    FileUtil.exists(cljkondoPath) -> lintWithExecutableLinter(collectedInfo, cljkondoPath)
                    else -> lintWithBuiltinLinter(collectedInfo)
                }
            }
        }

    }

    private fun lintWithExecutableLinter(
        collectedInfo: ExternalLintAnnotationInput, cljkondoPath: String
    ): ExternalLintAnnotationResult<List<String>> {

        val commandLine = GeneralCommandLine()

        commandLine.workDirectory = File(collectedInfo.psiFile.project.basePath!!)
        commandLine.withExePath(cljkondoPath).withParameters(
            "--lint", collectedInfo.psiFile.virtualFile.path, "--config", "{:output {:format :json}}"
        )

        val process = commandLine.createProcess()
        val processHandler: OSProcessHandler =
            ColoredProcessHandler(process, commandLine.commandLineString, Charsets.UTF_8)

        val output = ProcessOutput()
        processHandler.addProcessListener(object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                if (outputType == ProcessOutputTypes.STDERR) {
                    output.appendStderr(event.text)
                } else if (outputType != ProcessOutputTypes.SYSTEM) {
                    output.appendStdout(event.text)
                }
            }
        })

        processHandler.startNotify()
        if (processHandler.waitFor(60000)) {
            output.exitCode = process.exitValue()
        } else {
            processHandler.destroyProcess()
            output.setTimeout()
        }

        if (output.isTimeout) {
            throw ExecutionException("Command '" + commandLine.commandLineString + "' is timed out.")
        }

        return ExternalLintAnnotationResult(collectedInfo, arrayListOf(output.stdout))
    }

    private fun lintWithBuiltinLinter(collectedInfo: ExternalLintAnnotationInput): ExternalLintAnnotationResult<List<String>> {
        val current = Thread.currentThread().contextClassLoader

        try {
            Thread.currentThread().contextClassLoader = ClojureLoaderHolder.loader.get()

            val require: IFn = Clojure.`var`("clojure.core", "require")
            require.invoke(Clojure.read("clj-kondo.core"))
            require.invoke(Clojure.read("cheshire.core"))

            val run: IFn = Clojure.`var`("clj-kondo.core", "run!")
            val params =
                Clojure.read("{:config {:output {:format :json}} :config-dir \"${collectedInfo.psiFile.project.basePath}/.clj-kondo\" :lint [\"${collectedInfo.psiFile.virtualFile.path}\"]}")
            val findings = run.invoke(params)
            val print = Clojure.`var`("cheshire.core", "generate-string")
            val results = print.invoke(findings)

            return ExternalLintAnnotationResult(collectedInfo, arrayListOf(results.toString()))

        } catch (e: Exception) {
            return ExternalLintAnnotationResult(collectedInfo, emptyList())
        } finally {
            Thread.currentThread().contextClassLoader = current
        }
    }

    private fun collectInformation(
        psiFile: PsiFile, @Suppress("UNUSED_PARAMETER") editor: Editor?
    ): ExternalLintAnnotationInput? {
        if (psiFile.context != null) {
            return null
        }
        val virtualFile = psiFile.virtualFile
        if (virtualFile == null || !virtualFile.isInLocalFileSystem) {
            return null
        }
        if (psiFile.viewProvider is MultiplePsiFilesPerDocumentFileViewProvider) {
            return null
        }
        return ExternalLintAnnotationInput(psiFile)
    }

    override fun apply(
        file: PsiFile, annotationResult: ExternalLintAnnotationResult<List<String>>?, holder: AnnotationHolder
    ) {
        if (annotationResult?.result != null && annotationResult.result.isNotEmpty()) {
            val result = annotationResult.result.first()

            val documentManager = PsiDocumentManager.getInstance(file.project)
            val document: Document? = documentManager.getDocument(file)

            if (document != null) {
                mapper.readValue(result, Diagnostics::class.java).findings.forEach {
                    holder.newAnnotation(convertLevelToSeverity(it.level), "clj-kondo: ${it.message}").range(
                        TextRange.create(
                            calculateOffset(document, it.row, it.col), calculateOffset(document, it.endRow, it.endCol)
                        )
                    ).highlightType(
                        convertLevelToHighlight(it.level)
                    ).create()
                }
            }
        }
        super.apply(file, annotationResult, holder)
    }

    private fun convertLevelToSeverity(level: String): HighlightSeverity {
        return when (level) {
            "error" -> HighlightSeverity.ERROR
            "warning" -> HighlightSeverity.WARNING
            else -> HighlightSeverity.INFORMATION
        }
    }

    private fun convertLevelToHighlight(level: String): ProblemHighlightType {
        return when (level) {
            "error" -> ProblemHighlightType.GENERIC_ERROR_OR_WARNING
            "warning" -> ProblemHighlightType.WARNING
            else -> ProblemHighlightType.INFORMATION
        }
    }

    private fun calculateOffset(document: Document, line: Int, column: Int): Int {
        var offset: Int
        val adjustedLine = line - 1
        val adjustedColumn = column - 1
        if (0 <= adjustedLine && adjustedLine < document.lineCount) {
            val lineStart = document.getLineStartOffset(adjustedLine)
            val lineEnd = document.getLineEndOffset(adjustedLine)
            val docText = document.charsSequence
            offset = lineStart
            var col = 0
            while (offset < lineEnd && col < adjustedColumn) {
                col += if (docText[offset] == '\t') 2 else 1
                offset++
            }
        } else {
            offset = document.textLength
        }
        return offset
    }

    private fun initKondoDependencies() {
        File(
            (CljKondoAnnotator::class.java.classLoader as PluginClassLoader).pluginDescriptor.pluginPath.toString() + "/lib"
        ).listFiles().forEach { addURL(it.toURI().toURL()) }
    }

    private fun addURL(u: URL) {
        val sysLoader = ClojureLoaderHolder.loader.get() as DynamicClassLoader
        val urls: Array<URL> = sysLoader.urLs
        for (i in urls.indices) {
            if (urls[i].toString() == u.toString()) {
                return
            }
        }
        val sysclass: Class<*> = DynamicClassLoader::class.java
        try {
            val method: Method = sysclass.getDeclaredMethod("addURL", URL::class.java)
            method.isAccessible = true
            method.invoke(sysLoader, u)
        } catch (t: Throwable) {
            throw IOException("Error, could not add URL to system classloader")
        }
    }

}

class ExternalLintAnnotationInput(
    val psiFile: PsiFile
)

class ExternalLintAnnotationResult<T>(@Suppress("unused") val input: ExternalLintAnnotationInput, val result: T)
