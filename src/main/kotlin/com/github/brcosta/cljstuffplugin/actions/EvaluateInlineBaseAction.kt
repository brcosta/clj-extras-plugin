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
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.EditorTextField
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ConcurrencyUtil
import cursive.file.ClojureFileType
import cursive.psi.ClojurePsiElement
import cursive.repl.actions.ReplAction
import java.awt.Component
import java.awt.Point
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.swing.*


open class EvaluateInlineBaseAction(private val formFn: IFn) : AnAction() {
    private val log = Logger.getInstance(AnAction::class.java)
    private val threadPool = Executors.newCachedThreadPool(
        ConcurrencyUtil.newNamedThreadFactory("clojure-extras", true, Thread.NORM_PRIORITY)
    )

    override fun actionPerformed(event: AnActionEvent) {
        println("START OF BASE ACTION  ${Thread.currentThread().name}")
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return

        threadPool.submit {
            println("START OF RUNNABLE THREAD  ${Thread.currentThread().name}")

            val form = ApplicationManager.getApplication()
                .runReadAction(Computable { this.formFn.invoke(editor) as ClojurePsiElement? })

            if (form == null) {
                ApplicationManager.getApplication().invokeLater { addInlineElement(editor, "(x) Could not find form") }
            } else {

                val nrepl = NReplClient()
                ApplicationManager.getApplication()
                    .invokeLater { addInlineElement(editor, "(*) Evaluating....") { nrepl.interrupt()?.get(); } }

                val result = processForm(form, editor as EditorEx, nrepl)
                if (nrepl.isConnected) {
                    nrepl.disconnect()
                }

                ApplicationManager.getApplication().invokeLater { addInlineElement(editor, result) }
            }

        }

        println("END OF BASE ACTION ${Thread.currentThread().name} ")

    }


    private fun processForm(
        form: ClojurePsiElement, editor: EditorEx, nrepl: NReplClient
    ): String? {

        val stateAtom = ReplAction.replState(editor.project)?.deref() as ILookup? ?: return "(x) Repl disconnected"

        val replState = (stateAtom.valAt(Keyword.intern("repl-state")) as Atom?)?.deref() as ILookup?
        val host = replState?.valAt(Keyword.intern("host")) as String?
        val port = replState?.valAt(Keyword.intern("port")) as Long?
        val project = editor.project

        if (project != null && host != null && port != null) {

            val namespace =
                ApplicationManager.getApplication()
                    .runReadAction(Computable { form.ns?.qualifiedName })

            val text = ApplicationManager.getApplication()
                .runReadAction(Computable { form.text })

            val sessionId = (stateAtom.valAt(Keyword.intern("session-id"))) as String
            nrepl.connect(host, port.toInt(), sessionId)

            if (!nrepl.isConnected) {
                return "(x) Couldn'' connect to running session"
            } else {
                try {
                    val result = nrepl.eval(text, namespace).get(30, TimeUnit.SECONDS)
                    if (nrepl.isConnected) {
                        when {
                            result["err"] != null -> {
                                val errorTokens = (result["err"] as String).split("\n").filter { it.isNotEmpty() }
                                val errorStr = if (errorTokens.size > 1) errorTokens[1] else (errorTokens[0])
                                log.info("Error: $errorStr")
                                return "(x) $errorStr"
                            }
                            result["value"] != null -> return "=> ${result["value"] as String}" //addInlineElement(editor, "=> ${result["value"] as String}")
                            else -> {
                                if (result["status"] != null && (result["status"] as ArrayList<*>).size >= 3) {
                                    return "=> ${(result["status"] as ArrayList<*>)[0]}"
                                }
                            }
                        }
                    }
                } catch (e: TimeoutException) {
                    nrepl.interrupt()?.get(5, TimeUnit.SECONDS)
                    return "(x) Eval timed out"
                }
                if (nrepl.isConnected) {
                    nrepl.disconnect()
                }
            }
        }
        return null
    }

    private fun addInlineElement(editor: Editor, text: String?, action: Runnable? = null) {
        if (text != null) {

            val document = EditorFactory.getInstance().createDocument(StringUtil.convertLineSeparators(text))
            val editorInput = ClojureEditorComponent(
                document, editor.project, ClojureFileType.getInstance(), isViewer = true, oneLineMode = false
            )
            editorInput.border = BorderFactory.createEmptyBorder()
            val panel = JPanel()
            panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
            panel.border = BorderFactory.createEmptyBorder()
            panel.add(editorInput)

            if (action != null) {
                val btn = JButton("Stop")
                btn.alignmentX = Component.CENTER_ALIGNMENT
                btn.addActionListener {
                    action.run()
                    HintManager.getInstance().showInformationHint(editor, JLabel("Interrupted"))
                }
                panel.add(btn)

                val cursorAbsoluteLocation = editor.visualPositionToXY(editor.caretModel.visualPosition)
                val editorLocation = editor.component.locationOnScreen
                val editorContentLocation = editor.contentComponent.locationOnScreen
                val popupLocation = Point(
                    editorContentLocation.x + cursorAbsoluteLocation.x,
                    editorLocation.y + cursorAbsoluteLocation.y - editor.scrollingModel.verticalScrollOffset
                )

                HintManager.getInstance().showHint(
                    panel,
                    RelativePoint.fromScreen(popupLocation),
                    HintManager.HIDE_BY_OTHER_HINT,
                    60000
                )
            } else {
                HintManager.getInstance().showInformationHint(editor, panel)
            }


        }
    }

    class ClojureEditorComponent(
        document: Document?, project: Project?, fileType: FileType?, isViewer: Boolean, oneLineMode: Boolean
    ) : EditorTextField(document, project, fileType, isViewer, oneLineMode) {

        override fun createEditor(): EditorEx {
            val editor = super.createEditor()
            editor.settings.isUseSoftWraps = true
            return editor
        }
    }

}
