package com.github.brcosta.cljstuffplugin.extensions

import clojure.lang.ILookup
import clojure.lang.Keyword
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import cursive.repl.ClojureConsole
import cursive.repl.actions.ReplAction

class CustomEditorTabProvider: EditorTabTitleProvider {

    override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {

        if (file.name == "REPL.clj") {
            val stateAtom = ReplAction.replState(project)?.deref() as ILookup? ?: return null
            val outputBuffer =
                (stateAtom.valAt(Keyword.intern("console"))) as ClojureConsole? ?: return null
            if (file == outputBuffer.clojureVirtualFile) {
                return "REPL (Active)"
            }
        }
        return null
    }

}
