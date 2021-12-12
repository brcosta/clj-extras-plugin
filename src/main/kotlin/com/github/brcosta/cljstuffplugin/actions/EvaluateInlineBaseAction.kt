package com.github.brcosta.cljstuffplugin.actions

import clojure.lang.Atom
import clojure.lang.IFn
import clojure.lang.ILookup
import clojure.lang.Keyword
import com.github.brcosta.cljstuffplugin.util.NReplClient
import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.ComplementaryFontsRegistry
import com.intellij.openapi.editor.impl.FontInfo
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.EditorTextField
import cursive.file.ClojureFileType
import cursive.psi.ClojurePsiElement
import cursive.repl.actions.ReplAction
import org.jetbrains.annotations.NotNull
import java.awt.*


@Suppress("UnstableApiUsage")
open class EvaluateInlineBaseAction(private val formFn: IFn) : AnAction() {
    private val log = Logger.getInstance(AnAction::class.java)

    override fun actionPerformed(event: AnActionEvent) {
        event.getData(CommonDataKeys.EDITOR)?.let {
            (ApplicationManager.getApplication()).invokeAndWait {
                clearEditorInlays(it)
                addInlineElement(it, "(*) Evaluating....")
            }
            (ApplicationManager.getApplication()).invokeLater {
                val form = this.formFn.invoke(it) as ClojurePsiElement?
                if (form == null) {
                    clearEditorInlays(it)
                    addInlineElement(it, "(x) Could not find form")
                } else {
                    processForm(form, it as EditorEx)
                }
            }
        }
    }

    private fun processForm(
        form: ClojurePsiElement, editor: EditorEx
    ) {
        val stateAtom = ReplAction.replState(editor.project)?.deref() as ILookup?
        if (stateAtom == null) {
            addInlineElement(editor, "(x) Repl disconnected")
        }

        val replState = (stateAtom?.valAt(Keyword.intern("repl-state")) as Atom?)?.deref() as ILookup?
        val host = replState?.valAt(Keyword.intern("host")) as String?
        val port = replState?.valAt(Keyword.intern("port")) as Long?
        val project = editor.project

        if (project != null && host != null && port != null) {

            val namespace = form.ns?.qualifiedName
            val sessionId = (stateAtom?.valAt(Keyword.intern("session-id"))) as String

            val nrepl = NReplClient()
            nrepl.connect(host, port.toInt(), sessionId)

            clearEditorInlays(editor)

            if (!nrepl.isConnected) {
                addInlineElement(editor, "(x) Couldn't connect to running session")
            } else {
                val result = nrepl.eval(form.text, namespace).get()
                if (nrepl.isConnected) {
                    when {
                        result["err"] != null -> {
                            val errorTokens = (result["err"] as String).split("\n").filter { it.isNotEmpty() }
                            val errorStr = if (errorTokens.size > 1) errorTokens[1] else (errorTokens[0])
                            log.info("Error: $errorStr")
                            addInlineElement(editor, "(x) $errorStr")
                        }
                        result["value"] != null -> addInlineElement(editor, "=> ${result["value"] as String}")
                        else -> {
                            if (result["status"] != null && (result["status"] as ArrayList<*>).size >= 3) {
                                addInlineElement(editor, "=> ${(result["status"] as ArrayList<*>)[0]}")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun addInlineElement(editor: Editor, text: String) {
        val document = EditorFactory.getInstance().createDocument(StringUtil.convertLineSeparators(text))
        val myInput = MyEditorComponent(
            document, editor.project, ClojureFileType.getInstance(), isViewer = true, oneLineMode = false
        )
        HintManager.getInstance().showInformationHint(editor, myInput)
    }

    private fun clearEditorInlays(@NotNull editor: Editor) {
        editor.inlayModel.getAfterLineEndElementsInRange(
            0, editor.document.textLength, TextLabelCustomElementRenderer::class.java
        ).forEach(Disposer::dispose)
    }

    class MyEditorComponent(
        document: Document?,
        project: Project?,
        fileType: FileType?,
        isViewer: Boolean,
        oneLineMode: Boolean
    ) : EditorTextField(document, project, fileType, isViewer, oneLineMode) {

        override fun createEditor(): EditorEx {
            val editor = super.createEditor()
            editor.settings.isUseSoftWraps = true
            return editor
        }
    }

    class TextLabelCustomElementRenderer(label: String) : EditorCustomElementRenderer {
        private val label: String

        override fun calcWidthInPixels(inlay: Inlay<*>): Int {
            val fontInfo: FontInfo = getFontInfo(inlay.editor)

            return fontInfo.fontMetrics().stringWidth(label)
        }

        override fun paint(inlay: Inlay<*>, g: Graphics, r: Rectangle, textAttributes: TextAttributes) {
            val editor: Editor = inlay.editor
            val g2 = g as Graphics2D

            val attributes: TextAttributes = editor.colorsScheme.getAttributes(TEXT_ATTRIBUTES) ?: return
            val fgColor = attributes.foregroundColor ?: return
            val fontInfo: FontInfo = getFontInfo(editor)
            val ascent: Int = editor.ascent

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = attributes.backgroundColor ?: return
            g2.fillRoundRect(r.x, r.y + 1, calcWidthInPixels(inlay) + 1, fontInfo.fontMetrics().height + 3, 16, 16)
            g2.font = Font(fontInfo.font.name, Font.ITALIC, 11)
            g2.color = fgColor

            g2.drawString(label, r.x + 2, r.y - 1 + ascent)
        }

        companion object {
            private val TEXT_ATTRIBUTES = DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT
            private fun getFontInfo(editor: Editor): FontInfo {
                val colorsScheme: EditorColorsScheme = editor.colorsScheme
                val fontPreferences = colorsScheme.fontPreferences
                val fontStyle = Font.ITALIC
                return ComplementaryFontsRegistry.getFontAbleToDisplay(
                    'a'.code, fontStyle, fontPreferences, FontInfo.getFontRenderContext(editor.contentComponent)
                )
            }
        }

        init {
            this.label = " $label"
        }
    }

}
