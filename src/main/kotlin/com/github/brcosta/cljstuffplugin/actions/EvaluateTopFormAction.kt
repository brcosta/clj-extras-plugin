package com.github.brcosta.cljstuffplugin.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.diagnostic.Logger
import cursive.repl.actions.ReplAction

@Suppress("UnstableApiUsage", "unused")
class EvaluateTopFormAction : EvaluateInlineBaseAction(ReplAction.findTopForm) {
    private val log = Logger.getInstance(AnAction::class.java)
}
