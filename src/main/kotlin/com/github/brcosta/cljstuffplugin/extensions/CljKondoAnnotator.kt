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
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import java.io.File
import java.io.IOException
import java.lang.reflect.Method
import java.net.URL
import java.nio.file.Files
import kotlin.math.max

@Suppress("UnstableApiUsage")
class CljKondoAnnotator : ExternalAnnotator<ExternalLintAnnotationInput, ExternalLintAnnotationResult<List<String>>>() {

    private val require: IFn
    private val run: IFn
    private val print: IFn

    private val mapper: ObjectMapper =
        ObjectMapper().registerKotlinModule().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    init {
        initKondoDependencies()
        val current = Thread.currentThread().contextClassLoader

        try {
            Thread.currentThread().contextClassLoader = ClojureLoaderHolder.loader.get()
            require = Clojure.`var`("clojure.core", "require")
            require.invoke(Clojure.read("clj-kondo.core"))
            require.invoke(Clojure.read("cheshire.core"))
            run = Clojure.`var`("clj-kondo.core", "run!")
            print = Clojure.`var`("cheshire.core", "generate-string")
        } finally {
            Thread.currentThread().contextClassLoader = current
        }

    }

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

        val psiFile = collectedInfo.psiFile
        val commandLine = GeneralCommandLine()

        val lintFile = getLintFile(psiFile) ?: return ExternalLintAnnotationResult(collectedInfo, emptyList())

        val basePath = psiFile.project.basePath!!
        val filePath = psiFile.virtualFile.path
        val lintPath = lintFile.absolutePath

        commandLine.workDirectory = File(basePath)
        println(filePath)
        commandLine.withExePath(cljkondoPath).withParameters(
            "--lint", lintPath, "--filename", filePath, "--lang", "cljs", "--config", "{:output {:format :json }}"
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
        if (processHandler.waitFor(30000)) {
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
        val psiFile = collectedInfo.psiFile

        try {
            val lintFile = getLintFile(psiFile) ?: return ExternalLintAnnotationResult(collectedInfo, emptyList())
            Thread.currentThread().contextClassLoader = ClojureLoaderHolder.loader.get()

            val filePath = psiFile.virtualFile.path
            val tempPath = lintFile.absolutePath

            println(filePath)

            val config =
                "{:config {:output {:format :json}} :filename \"$filePath\" :lint [\"$tempPath\"]}"
            val findings = run.invoke(Clojure.read(config))
            val results = print.invoke(findings)


            lintFile.delete()
            return ExternalLintAnnotationResult(collectedInfo, arrayListOf(results.toString()))

        } catch (e: Exception) {
            return ExternalLintAnnotationResult(collectedInfo, emptyList())
        } finally {
            Thread.currentThread().contextClassLoader = current
        }
    }


    // https://intellij-support.jetbrains.com/hc/en-us/community/posts/115000337510-Only-trigger-externalAnnotator-when-the-file-system-is-in-sync
    private fun getLintFile(psiFile: PsiFile): File? {
        val prefix = "clj_extras_clj_kondo_annotator"
        val documentManager = PsiDocumentManager.getInstance(psiFile.project)
        val document: Document = documentManager.getDocument(psiFile) ?: return null
        val lintFile = FileUtilRt.createTempFile(
            prefix,
            "${System.currentTimeMillis()}.${psiFile.virtualFile.extension}", true
        )
        Files.writeString(lintFile.toPath(), document.text)
        return lintFile
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
            val lines = document?.text?.lines()

            if (document != null) {
                mapper.readValue(result, Diagnostics::class.java).findings.forEach {
                    val row = max(0, it.row - 1)
                    val col = max(0, it.col - 1)
                    val isExpr = lines!![row][col] == '('
                    val endRow = if (isExpr) row else it.endRow - 1
                    val endCol = if (isExpr) col + 1 else it.endCol - 1
                    val annotation = holder.newAnnotation(convertLevelToSeverity(it.level), "clj-kondo: ${it.message}")
                        .range(
                            TextRange.create(
                                calculateOffset(document, row, col),
                                calculateOffset(document, endRow, endCol)
                            )
                        )
                    when (it.level) {
                        "warning" -> annotation.textAttributes(CodeInsightColors.WEAK_WARNING_ATTRIBUTES).create()
                        else -> annotation.highlightType(convertLevelToHighlight(it.level)).create()
                    }
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
            "warning" -> ProblemHighlightType.WEAK_WARNING
            else -> ProblemHighlightType.INFORMATION
        }
    }

    private fun calculateOffset(document: Document, line: Int, column: Int): Int {
        var offset: Int
        if (0 <= line && line < document.lineCount) {
            val lineStart = document.getLineStartOffset(line)
            val lineEnd = document.getLineEndOffset(line)
            val docText = document.charsSequence
            offset = lineStart
            var col = 0
            while (offset < lineEnd && col < column) {
                col += if (docText[offset] == '\t') 2 else 1
                offset++
            }
        } else {
            offset = document.textLength
        }
        return offset
    }

    private fun initKondoDependencies() {
        @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
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
        val sysClass: Class<*> = DynamicClassLoader::class.java
        try {
            val method: Method = sysClass.getDeclaredMethod("addURL", URL::class.java)
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
