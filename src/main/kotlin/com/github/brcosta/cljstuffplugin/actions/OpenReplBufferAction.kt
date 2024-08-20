package com.github.brcosta.cljstuffplugin.actions

import clojure.lang.ILookup
import clojure.lang.Keyword
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import cursive.repl.ClojureConsole
import cursive.repl.actions.ReplAction

open class OpenReplBufferAction : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {
        if (event.project != null) {

            val project = event.project!!
            val stateAtom = ReplAction.replState(event.project!!)?.deref() as ILookup?

            val outputBuffer = (stateAtom?.valAt(Keyword.intern("console"))) as ClojureConsole?

            if ((outputBuffer == null) || (outputBuffer.clojureVirtualFile == null)) {
                Notification(
                    "ClojureRepl",
                    "REPL not found",
                    "Please open a REPL instance first!",
                    NotificationType.INFORMATION).notify(event.project)
                return
            }

            val replBuffer = outputBuffer.clojureVirtualFile
            val fileManager = FileEditorManager.getInstance(project)

            if (!fileManager.isFileOpen(replBuffer)) {
                fileManager.openTextEditor(OpenFileDescriptor(project, replBuffer), true)
            }

        }
    }

}
