package com.github.brcosta.cljstuffplugin.extensions

import clojure.java.api.Clojure
import clojure.lang.IFn
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.brcosta.cljstuffplugin.cljkondo.*
import com.github.brcosta.cljstuffplugin.util.AppSettingsState
import com.github.brcosta.cljstuffplugin.util.runWithClojureClassloader
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationBuilder
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import java.io.File
import java.nio.file.Files
import kotlin.math.max

@Suppress("UnstableApiUsage")
class CljKondoAnnotator : ExternalAnnotator<ExternalLintAnnotationInput, ExternalLintAnnotationResult<List<String>>>() {

    private val log = Logger.getInstance(CljKondoAnnotator::class.java)

    private lateinit var run: IFn
    private lateinit var print: IFn
    private val separators = " )".toCharArray()

    private val mapper: ObjectMapper =
        ObjectMapper().registerKotlinModule().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    init {
        initKondo()
        runWithClojureClassloader {
            run = getCljKondoRun()
            print = getCheshirePrint()
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
        collectedInfo: ExternalLintAnnotationInput, cljkondoPath: String,
    ): ExternalLintAnnotationResult<List<String>> {

        val psiFile = collectedInfo.psiFile
        val lintFile = getTempLintFile(psiFile) ?: return ExternalLintAnnotationResult(collectedInfo, emptyList())

        val command = CljKondoProcessBuilder()
            .workDirectory(psiFile.project.basePath!!)
            .withExePath(cljkondoPath)
            .withLintFile(lintFile.absolutePath)
            .withFilename(StringUtil.escapeBackSlashes(psiFile.virtualFile.path))
            .withConfig("{:output {:format :json }}")
            .build()

        val output = CljKondoProcessRunner()
            .withCommandLine(command.first)
            .withProcess(command.second)
            .withTimeout(5000)
            .run()

        return ExternalLintAnnotationResult(collectedInfo, arrayListOf(output.stdout))
    }

    private fun lintWithBuiltinLinter(collectedInfo: ExternalLintAnnotationInput): ExternalLintAnnotationResult<List<String>> {
        val psiFile = collectedInfo.psiFile

        try {
            val lintFile = getTempLintFile(psiFile) ?: return ExternalLintAnnotationResult(collectedInfo, emptyList())

            val results = runWithClojureClassloader {
                val filePath = StringUtil.escapeBackSlashes(psiFile.virtualFile.path)
                val tempPath = StringUtil.escapeBackSlashes(lintFile.absolutePath)

                val config =
                    "{:config {:output {:format :json}} :filename \"$filePath\" :lint [\"$tempPath\"]}"
                val findings = run.invoke(Clojure.read(config))
                val results = print.invoke(findings)

                lintFile.delete()
                results
            }

            return ExternalLintAnnotationResult(collectedInfo, arrayListOf(results.toString()))

        } catch (e: Exception) {
            log.error("Error trying to annotate file", e)
            return ExternalLintAnnotationResult(collectedInfo, emptyList())
        }
    }


    // https://intellij-support.jetbrains.com/hc/en-us/community/posts/115000337510-Only-trigger-externalAnnotator-when-the-file-system-is-in-sync
    private fun getTempLintFile(psiFile: PsiFile): File? {
        val prefix = "clj_extras_clj_kondo_annotator"
        val documentManager = PsiDocumentManager.getInstance(psiFile.project)
        val document: Document = documentManager.getDocument(psiFile) ?: return null
        val lintFile = FileUtilRt.createTempFile(
            prefix,
            "${System.currentTimeMillis()}.${psiFile.virtualFile.extension}", true
        )
        Files.writeString(lintFile.toPath(), document.charsSequence)
        return lintFile
    }

    private fun collectInformation(
        psiFile: PsiFile, @Suppress("UNUSED_PARAMETER") editor: Editor?,
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
        file: PsiFile, annotationResult: ExternalLintAnnotationResult<List<String>>?, holder: AnnotationHolder,
    ) {
        if (annotationResult?.result != null && annotationResult.result.isNotEmpty()) {
            val result = annotationResult.result.first()

            val documentManager = PsiDocumentManager.getInstance(file.project)
            val document: Document? = documentManager.getDocument(file)
            val lines = document?.charsSequence?.lines()

            if (document != null && lines != null) {
                mapper.readValue(result, Diagnostics::class.java).findings.forEach {
                    makeAnnotationBuilder(it, holder, document, lines).create()
                }
            }
        }
        super.apply(file, annotationResult, holder)
    }

    private fun bla(param: String){

    }
    private fun makeAnnotationBuilder(
        finding: Finding,
        holder: AnnotationHolder,
        document: Document,
        lines: List<String>,
    ): AnnotationBuilder {
        val severity = convertLevelToSeverity(finding.level)
        val message = "clj-kondo: ${finding.message}"
        val textRange = calculateTextRange(document, lines, finding)
        val annotation = holder.newAnnotation(severity, message).range(textRange)

        return when (finding.type) {
            "unused-binding", "unused-import", "unused-namespace" -> {
                annotation.textAttributes(CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES)
            }
            else -> when (finding.level) {
                "warning" -> annotation.textAttributes(CodeInsightColors.WEAK_WARNING_ATTRIBUTES)
                else -> annotation.highlightType(convertLevelToHighlight(finding.level))
            }
        }
    }

    private fun calculateTextRange(
        document: Document,
        lines: List<String>,
        finding: Finding,
    ): TextRange {
        val row = max(0, finding.row - 1)
        val line = lines[row]
        val col = max(0, finding.col - 1)
        val isExpr = line[col] == '('

        val nextToken = line.substring(col).indexOfAny(separators)
        val endRow = if (isExpr) row else finding.endRow - 1
        val endCol = if (isExpr) col + max(1, nextToken) else finding.endCol - 1

        return TextRange.create(
            calculateOffset(document, row, if (isExpr) col + 1 else col),
            calculateOffset(document, endRow, endCol)
        )
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

}

class ExternalLintAnnotationInput(
    val psiFile: PsiFile,
)

class ExternalLintAnnotationResult<T>(@Suppress("unused") val input: ExternalLintAnnotationInput, val result: T)
