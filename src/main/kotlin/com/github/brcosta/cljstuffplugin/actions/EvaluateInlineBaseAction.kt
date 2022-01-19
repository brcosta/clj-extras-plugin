package com.github.brcosta.cljstuffplugin.actions

import clojure.lang.Atom
import clojure.lang.IFn
import clojure.lang.ILookup
import clojure.lang.Keyword
import com.github.brcosta.cljstuffplugin.extensions.ClojureColorsAndFontsPageEx
import com.github.brcosta.cljstuffplugin.util.AppSettingsState
import com.github.brcosta.cljstuffplugin.util.NReplClient
import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.EditorTextField
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.ui.JBUI
import cursive.file.ClojureFileType
import cursive.psi.ClojurePsiElement
import cursive.repl.StyledOutputBuffer
import cursive.repl.actions.ReplAction
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.math.min


open class EvaluateInlineBaseAction(private val formFn: IFn) : AnAction() {
    private val log = Logger.getInstance(AnAction::class.java)
    private val threadPool = Executors.newCachedThreadPool(
        ConcurrencyUtil.newNamedThreadFactory("clojure-extras", true, Thread.NORM_PRIORITY)
    )

    override fun actionPerformed(event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return

        threadPool.submit {

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
    }


    private fun processForm(
        form: ClojurePsiElement, editor: EditorEx, nrepl: NReplClient
    ): String? {

        val settings = AppSettingsState.instance
        val prettyPrint = settings.prettyPrint
        val redirectStdout = settings.redirectStdoutToRepl

        val stateAtom = ReplAction.replState(editor.project)?.deref() as ILookup? ?: return "(x) Repl is not connected"

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
                return "(x) Couldn't connect to running session"
            } else {
                try {
                    setupPrettyPrint(prettyPrint, nrepl)
                    val result =
                        nrepl.eval(text, namespace, prettyPrint).get(30, TimeUnit.SECONDS)

                    if (nrepl.isConnected) {
                        when {
                            result["err"] != null -> {
                                val errorTokens = (result["err"] as String).split("\n").filter { it.isNotEmpty() }
                                val errorStr = if (errorTokens.size > 1) errorTokens[1] else (errorTokens[0])
                                log.info("Error: $errorStr")

                                return "(x) $errorStr"
                            }
                            result["value"] != null -> {
                                val current = Thread.currentThread().contextClassLoader

                                return try {
                                    var value = result["value"] as String
                                    value = org.apache.commons.lang.StringEscapeUtils.unescapeJava(value).trim()

                                    if (redirectStdout && result["out"] != null) {
                                        val outputBuffer =
                                            (stateAtom.valAt(Keyword.intern("output-buffer"))) as StyledOutputBuffer
                                        outputBuffer.print(result["out"] as String, null)
                                    }

                                    when {
                                        value.lines().count() == 1 -> "=> $value"
                                        else -> "=>\n$value"
                                    }
                                } catch (e: Exception) {
                                    "=> ${result["value"] as String}"
                                } finally {
                                    Thread.currentThread().contextClassLoader = current
                                }

                            }
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

    private fun setupPrettyPrint(prettyPrint: Boolean, nrepl: NReplClient) {
        when {
            prettyPrint -> nrepl.eval(
                "(defn extra-pprint [obj out opt] (clojure.pprint/pprint obj out))",
                "user"
            ).get(5, TimeUnit.SECONDS)
        }
    }

    private fun addInlineElement(editor: Editor, text: String?, action: Runnable? = null) {
        if (text != null) {

            val document = EditorFactory.getInstance().createDocument(StringUtil.convertLineSeparators(text))
            val editorInput = ClojureEditorComponent(
                document,
                editor.project,
                ClojureFileType.getInstance(),
                isViewer = true,
                oneLineMode = false
            )

            val font = editorInput.component.font
            val lines = text.lines()
            val linesCount = lines.count()
            val width = editorInput.getFontMetrics(font)
                .getStringBounds(lines.maxByOrNull { it.length }, editorInput.graphics).width
            val height =
                (editorInput.getFontMetrics(font).getStringBounds("W", editorInput.graphics).height + 5) * linesCount

            editorInput.preferredSize =
                Dimension(min(800, width.toInt() + 20), min(400, height.toInt()) + (if (linesCount == 1) 6 else 24))
            editorInput.autoscrolls = true

            val panel = JPanel()
            panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
            panel.add(editorInput.component)

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

            editorInput.editor?.scrollingModel?.scrollTo(LogicalPosition(0, 0), ScrollType.CENTER_UP)

        }
    }

    class ClojureEditorComponent(
        document: Document?, project: Project?, fileType: FileType?, isViewer: Boolean, oneLineMode: Boolean
    ) : EditorTextField(document, project, fileType, isViewer, oneLineMode) {

        override fun createEditor(): EditorEx {
            val editor = super.createEditor()

            editor.settings.isUseSoftWraps = true
            editor.setVerticalScrollbarVisible(true)
            editor.setBorder(JBUI.Borders.empty(2))

            return editor
        }
    }

}
