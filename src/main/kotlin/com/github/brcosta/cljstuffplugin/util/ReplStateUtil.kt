package com.github.brcosta.cljstuffplugin.util

import clojure.lang.Atom
import clojure.lang.ILookup
import com.intellij.openapi.project.Project

/**
 * @author <a href="https://github.com/zjjfly"/>zjjfly</a>
 */
fun getStateAtom(project: Project): Atom? {
    val focusedReplState: Atom? = cursive.repl.focusedReplState(project)
    val active = focusedReplState?.let { cursive.repl.isActive(it) } ?: return null
    return if (!active) null else focusedReplState
}

fun getState(project: Project): ILookup? {
    val focusedReplState = getStateAtom(project)
    return focusedReplState?.deref() as ILookup? ?: return null
}
