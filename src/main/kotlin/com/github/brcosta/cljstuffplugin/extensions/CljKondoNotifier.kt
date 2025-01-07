package com.github.brcosta.cljstuffplugin.extensions

import com.github.brcosta.cljstuffplugin.actions.AnalyzeClasspathAction
import com.github.brcosta.cljstuffplugin.util.AppSettingsState
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import java.io.File

class CljKondoNotifier : ProjectActivity {

    override suspend fun execute(project: Project) {
        if (project.basePath == null) {
            return
        }

        val settings = AppSettingsState.instance

        val files = File(project.basePath!!).list()

        val isClojureProject = files!!.count { it.contains("clj") or it.contains("cljs") or it.contains("edn") } > 0

        if (settings.cljkondoEnabled and isClojureProject) {

            if (files.contains(".clj-kondo")) {
                AnalyzeClasspathAction().analyzeDependencies(project)
            } else {
                val notification = Notification(
                    "Project Startup Tasks Messages",
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

}
