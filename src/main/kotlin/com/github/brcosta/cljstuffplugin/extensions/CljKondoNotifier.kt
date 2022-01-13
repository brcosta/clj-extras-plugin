package com.github.brcosta.cljstuffplugin.extensions

import com.github.brcosta.cljstuffplugin.actions.EvaluateTopFormAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import java.lang.String

class CljKondoNotifier : StartupActivity {
    private val log = Logger.getInstance(CljKondoNotifier::javaClass.name)

    override fun runActivity(project: Project) {
        val notification = Notification(
            "ProjectOpenNotification", "Clj-kondo classpath linting",
            String.format("Run clj-kondo on the project classpath/dependencies for better results", project.name), NotificationType.INFORMATION
        ).addAction(object : AnAction("Lint Dependencies")  {
            override fun actionPerformed(e: AnActionEvent) {
               log.info("Nothing yet")
            }
        })
        notification.notify(project)
    }
}
