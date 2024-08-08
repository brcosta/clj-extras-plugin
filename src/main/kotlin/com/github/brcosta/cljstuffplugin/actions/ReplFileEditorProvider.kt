package com.github.brcosta.cljstuffplugin.actions

import clojure.lang.ILookup
import clojure.lang.Keyword
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import cursive.repl.ClojureConsole
import cursive.repl.StyledPrinter
import cursive.repl.actions.ReplAction
import java.beans.PropertyChangeListener
import java.util.concurrent.Executors
import javax.swing.JComponent


class ReplFileEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean {
        val stateAtom = ReplAction.replState(project)?.deref() as ILookup? ?: return false
        val outputBuffer =
            (stateAtom.valAt(Keyword.intern("console"))) as ClojureConsole? ?: return false
        return (file == outputBuffer.clojureVirtualFile)
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor = ReplFileEditor(project)

    override fun getEditorTypeId(): String {
        return "REPL"
    }

    override fun getPolicy(): FileEditorPolicy {
        return FileEditorPolicy.HIDE_DEFAULT_EDITOR
    }

    class ReplFileEditor() : UserDataHolderBase(), FileEditor {

        private val log = Logger.getInstance(ReplFileEditor::class.java)

        var edit: Editor? = null
        var proj: Project? = null

        constructor(proj: Project) : this() {
            this.proj = proj
            val stateAtom = ReplAction.replState(proj)?.deref() as ILookup?

            val buffer =
                (stateAtom?.valAt(Keyword.intern("output-buffer"))) as StyledPrinter

            val factory = EditorFactory.getInstance()
            edit = factory.createViewer(
                buffer.getEditor().document,
                proj,
                EditorKind.CONSOLE)

            edit?.let {
                it.settings.isLineMarkerAreaShown = false
                it.settings.isVirtualSpace = false
                it.settings.isAdditionalPageAtBottom = false
                it.settings.isUseSoftWraps = false
                it.settings.setGutterIconsShown(false)

                updateHighlighters(buffer, 50)

                buffer.getEditor().document.addDocumentListener(object : DocumentListener {
                    override fun documentChanged(event: DocumentEvent) {
                        try {
                            if (event.isWholeTextReplaced) {
                                it.markupModel.removeAllHighlighters()
                            }
                            updateHighlighters(buffer, 50)
                            updateHighlighters(buffer, 300)
                            super.documentChanged(event)
                        } catch (e: Exception) {
                            log.error("File buffer update error.", e)
                        }
                    }
                })
            }

        }

        private fun updateHighlighters(buffer: StyledPrinter, delayMs: Long) {
            Executors.newCachedThreadPool().submit {
                ApplicationManager.getApplication().invokeLater {
                    Thread.sleep(delayMs)
                    edit?.let { editor ->
                        editor.markupModel.removeAllHighlighters()
                        buffer.getEditor().markupModel.allHighlighters.forEach {
                            if (it.endOffset <= editor.document.textLength) {
                                editor.markupModel.addRangeHighlighter(it.startOffset,
                                    it.endOffset,
                                    it.layer,
                                    it.getTextAttributes(EditorColorsManager.getInstance().globalScheme), it.targetArea)
                            }
                        }
                        EditorUtil.scrollToTheEnd(editor)
                    }
                }
            }
        }

        override fun getFile(): VirtualFile? {
            val stateAtom = proj?.let { ReplAction.replState(it)?.deref() } as ILookup?
            return ((stateAtom?.valAt(Keyword.intern("console"))) as ClojureConsole?)?.clojureVirtualFile
        }

        override fun dispose() {
            proj = null
            edit = null
        }

        private fun StyledPrinter.getEditor(): EditorEx {
            return javaClass.getDeclaredField("destination").let {
                it.isAccessible = true
                return@let it.get(this) as EditorEx
            }
        }

        override fun getComponent(): JComponent {
            return edit!!.component
        }

        override fun getPreferredFocusedComponent(): JComponent? {
            return edit!!.component
        }

        override fun getName(): String {
            return "REPL"
        }

        override fun setState(state: FileEditorState) {

        }

        override fun isModified(): Boolean {
            return false
        }

        override fun isValid(): Boolean {
            return true
        }

        override fun addPropertyChangeListener(listener: PropertyChangeListener) {
            // nothing
        }

        override fun removePropertyChangeListener(listener: PropertyChangeListener) {
            // nothing
        }

        override fun getCurrentLocation(): FileEditorLocation? {
            return null
        }

    }
}
