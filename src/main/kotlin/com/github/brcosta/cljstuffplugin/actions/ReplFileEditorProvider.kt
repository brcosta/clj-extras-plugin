package com.github.brcosta.cljstuffplugin.actions

import clojure.lang.ILookup
import clojure.lang.Keyword
import com.intellij.execution.filters.CompositeFilter
import com.intellij.execution.impl.EditorHyperlinkSupport
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import cursive.repl.ClojureConsole
import cursive.repl.StyledOutputBuffer
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

        var edit: Editor? = null
        var proj: Project? = null

        constructor(proj: Project) : this() {
            this.proj = proj
            val stateAtom = ReplAction.replState(proj)?.deref() as ILookup?

            val buffer =
                (stateAtom?.valAt(Keyword.intern("output-buffer"))) as StyledOutputBuffer

            val factory = EditorFactory.getInstance()
            edit = factory.createViewer(
                buffer.getEditor().document,
                proj,
                EditorKind.CONSOLE)

            val filters = CompositeFilter(proj)
            val hyperlinkSupport = EditorHyperlinkSupport(edit!!, proj)

            edit!!.settings.isLineMarkerAreaShown = false
            edit!!.settings.setGutterIconsShown(false)
            edit!!.settings.isVirtualSpace = false
            edit!!.settings.isAdditionalPageAtBottom = false

            updateHighlighters(buffer, 50)

            buffer.getEditor().document.addDocumentListener(object :
                com.intellij.openapi.editor.event.DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    if (event.isWholeTextReplaced) {
                        edit!!.markupModel.removeAllHighlighters()
                    }
                    updateHighlighters(buffer, 50)
                    updateHighlighters(buffer, 300)
                    hyperlinkSupport.highlightHyperlinks(filters,
                        0,
                        edit!!.document.getLineNumber(edit!!.document.textLength))
                    super.documentChanged(event)
                }
            })

        }

        private fun updateHighlighters(buffer: StyledOutputBuffer, delayMs: Long) {
            Executors.newCachedThreadPool().submit {
                ApplicationManager.getApplication().invokeLater {
                    Thread.sleep(delayMs)
                    edit!!.markupModel.removeAllHighlighters()
                    buffer.getEditor().markupModel.allHighlighters.forEach {
                        if (it.endOffset <= edit!!.document.textLength) {
                            edit!!.markupModel.addRangeHighlighter(it.startOffset,
                                it.endOffset,
                                it.layer,
                                it.getTextAttributes(EditorColorsManager.getInstance().globalScheme), it.targetArea)
                        }
                    }
                    EditorUtil.scrollToTheEnd(edit!!)
                }
            }
        }

        override fun getFile(): VirtualFile? {
            val stateAtom = ReplAction.replState(proj)?.deref() as ILookup?
            return ((stateAtom?.valAt(Keyword.intern("console"))) as ClojureConsole?)?.clojureVirtualFile
        }

        override fun dispose() {
            proj = null
            edit = null
        }

        private fun StyledOutputBuffer.getEditor(): EditorEx {
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
