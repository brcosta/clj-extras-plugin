package com.github.brcosta.cljstuffplugin.extensions

import clojure.java.api.Clojure
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.github.brcosta.cljstuffplugin.cljkondo.*
import com.github.brcosta.cljstuffplugin.util.AppSettingsState
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationBuilder
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.Strings
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import java.io.File
import java.nio.file.Files
import kotlin.math.max

@Suppress("UnstableApiUsage")
class CljKondoAnnotator : ExternalAnnotator<ExternalLintAnnotationInput, ExternalLintAnnotationResult<List<String>>>() {

    private val log = Logger.getInstance(CljKondoAnnotator::class.java)

    private var runWithInStr = getWithInStr()
    private var print = getCheshirePrint()

    private val separators = " )".toCharArray()

    private val mapper: ObjectMapper =
        ObjectMapper().registerModule(kotlinModule())
         //   .registerModule(ParameterNamesModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

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

        return if (!cljkondoEnabled) ExternalLintAnnotationResult(collectedInfo, emptyList())
        else when {
            FileUtil.exists(cljkondoPath) -> lintWithExecutableLinter(collectedInfo, cljkondoPath)
            else -> lintWithBuiltinLinter(collectedInfo)
        }

    }

    private fun lintWithExecutableLinter(
        collectedInfo: ExternalLintAnnotationInput, cljkondoPath: String,
    ): ExternalLintAnnotationResult<List<String>> {

        try {

            val psiFile = collectedInfo.psiFile
            val lintFile = getTempLintFile(psiFile) ?: return ExternalLintAnnotationResult(collectedInfo, emptyList())

            val command = CljKondoProcessBuilder()
                .workDirectory(psiFile.project.basePath!!)
                .withExePath(cljkondoPath)
                .withLintCode(lintFile)
                .withFilename(StringUtil.escapeBackSlashes(psiFile.virtualFile.path))
                .withConfig("{:output {:format :json }}")
                .build()

            val output = CljKondoProcessRunner()
                .withCommandLine(command.first)
                .withProcess(command.second)
                .withTimeout(5000)
                .run()

            return ExternalLintAnnotationResult(collectedInfo, arrayListOf(output.stdout))
        } catch (e: ProcessCanceledException) {
            return ExternalLintAnnotationResult(collectedInfo, emptyList())
        } catch (e: Exception) {
            log.error("Error trying to annotate file", e)
            return ExternalLintAnnotationResult(collectedInfo, emptyList())
        }
    }

    private fun lintWithBuiltinLinter(collectedInfo: ExternalLintAnnotationInput): ExternalLintAnnotationResult<List<String>> {
        val psiFile = collectedInfo.psiFile

        try {
            val lintFile = getTempLintFile(psiFile) ?: return ExternalLintAnnotationResult(collectedInfo, emptyList())
            val filePath = StringUtil.escapeBackSlashes(psiFile.virtualFile.path)

            val config =
                "{:config {:output {:format :json}} :filename \"$filePath\" :lint [-]}"

            val findings = runWithInStr.invoke(getPsiFileContent(psiFile), Clojure.read(config))
            val results = print.invoke(findings)

            lintFile.delete()

            return ExternalLintAnnotationResult(collectedInfo, arrayListOf(results.toString()))

        } catch (e: Exception) {
            log.error("Error trying to annotate file", e)
            return ExternalLintAnnotationResult(collectedInfo, emptyList())
        }
    }

    private fun getPsiFileContent(psiFile: PsiFile): String? {
        val documentManager = PsiDocumentManager.getInstance(psiFile.project)
        val document: Document = documentManager.getDocument(psiFile) ?: return null

        return document.text
    }

    // https://intellij-support.jetbrains.com/hc/en-us/community/posts/115000337510-Only-trigger-externalAnnotator-when-the-file-system-is-in-sync
    private fun getTempLintFile(psiFile: PsiFile): File? {
        val prefix = "clj_extras_clj_kondo_annotator"
        val content = getPsiFileContent(psiFile) ?: return null
        val lintFile = FileUtilRt.createTempFile(
            prefix,
            "${System.currentTimeMillis()}.${psiFile.virtualFile.extension}", true
        )
        Files.writeString(lintFile.toPath(), content)
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
                    makeAnnotationBuilder(it, holder, document, lines)?.create()
                }
            }
        }
        super.apply(file, annotationResult, holder)
    }

    private fun makeAnnotationBuilder(
        finding: Finding,
        holder: AnnotationHolder,
        document: Document,
        lines: List<String>,
    ): AnnotationBuilder? {
        try {
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
        } catch (e: Exception) {
            log.error("Error making clj-kondo annotation", e)
            return null
        }
    }

    private fun calculateTextRange(
        document: Document,
        lines: List<String>,
        finding: Finding,
    ): TextRange {
        val row = max(0, finding.row - 1)
        val line = if (row < lines.size) lines[row] else Strings.EMPTY_CHAR_SEQUENCE
        val col = max(0, finding.col - 1)
        val isExpr = col < line.length && line[col] == '('

        val nextToken = if (line.isNotEmpty()) line.substring(col).indexOfAny(separators) else 0
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
