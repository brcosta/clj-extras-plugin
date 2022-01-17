package com.github.brcosta.cljstuffplugin.extensions

import com.github.brcosta.cljstuffplugin.actions.AnalyzeClasspathAction
import com.github.brcosta.cljstuffplugin.util.AppSettingsState
import com.intellij.lang.LanguageUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import java.io.File

class CljKondoNotifier : StartupActivity {
    override fun runActivity(project: Project) {

        if (project.basePath == null) {
            return
        }

        val settings = AppSettingsState.instance

        val files = File(project.basePath!!).list()
        val isClojureProject = files!!.count { it.contains("clj") or it.contains("cljs") or  it.contains("edn") } > 0

        if (settings.cljkondoEnabled and isClojureProject) {
            val notification = Notification(
                "ProjectOpenNotification",
                "Clj-kondo: Analyze project classpath",
                "Analyze classpath on '${project.name}' for better linting results",
                NotificationType.INFORMATION
            )

            val action = AnalyzeClasspathAction()
            action.templatePresentation.text = "Analyze Classpath"
            action.notification = notification

            notification.addAction(action)
            notification.notify(project)
        }

    }

}
