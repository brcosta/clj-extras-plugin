package com.github.brcosta.cljstuffplugin.actions

import clojure.java.api.Clojure
import clojure.lang.ClojureLoaderHolder
import com.intellij.notification.Notification
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.roots.OrderEnumerator

open class AnalyzeClasspathAction : AnAction() {

    var notification: Notification? = null

    override fun actionPerformed(event: AnActionEvent) {
        notification?.expire()

        ProgressManager.getInstance()
            .run(object : Task.Backgroundable(event.project, "Clj-kondo: Analyze project classpath") {
                override fun run(indicator: ProgressIndicator) {
                    val current = Thread.currentThread().contextClassLoader
                    try {
                        Thread.currentThread().contextClassLoader = ClojureLoaderHolder.loader.get()
                        val require = Clojure.`var`("clojure.core", "require")
                        require.invoke(Clojure.read("clj-kondo.core"))
                        val run = Clojure.`var`("clj-kondo.core", "run!")
                        val pathsList =
                            OrderEnumerator.orderEntries(event.project!!).recursively().librariesOnly().pathsList
                        indicator.text = "Analyzing project sources..."
                        indicator.fraction = 0.0
                        val config =
                            "{:config {:output {:format :json}} :copy-configs true :dependencies true :lint [\".\"]}"
                        run.invoke(Clojure.read(config))

                        pathsList.virtualFiles.forEachIndexed { index, file ->
                            val config =
                                "{:config {:output {:format :json}} :copy-configs true :dependencies true :lint [\"${file.path}\" ]}"
                            indicator.text = "Analyzing '${file.path}'..."
                            indicator.fraction = index.toDouble() / pathsList.virtualFiles.size
                            run.invoke(Clojure.read(config))
                        }
                    } finally {
                        Thread.currentThread().contextClassLoader = current
                    }
                }
            })
    }
}
