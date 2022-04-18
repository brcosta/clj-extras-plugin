package com.github.brcosta.cljstuffplugin.actions

import clojure.java.api.Clojure
import com.github.brcosta.cljstuffplugin.cljkondo.CljKondoProcessBuilder
import com.github.brcosta.cljstuffplugin.cljkondo.CljKondoProcessRunner
import com.github.brcosta.cljstuffplugin.cljkondo.getCljKondoRun
import com.github.brcosta.cljstuffplugin.util.AppSettingsState
import com.intellij.notification.Notification
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import java.io.File

open class AnalyzeClasspathAction : AnAction() {
    private val log = Logger.getInstance(AnalyzeClasspathAction::class.java)
    var notification: Notification? = null

    override fun actionPerformed(event: AnActionEvent) {
        if (event.project != null) {
            notification?.expire()
            analyzeDependencies(event.project!!)
        }
    }

    fun analyzeDependencies(project: Project) {
        val settings = AppSettingsState.instance
        val cljkondoPath = settings.cljkondoPath

        project.basePath?.let {
            val kondoConfig = File(project.basePath, ".clj-kondo")
            if (!kondoConfig.exists()) {
                log.info("creating .clj-kondo directory for '${project.name}'")
                kondoConfig.mkdir()
            } else {
                log.info(".clj-kondo already exists")
            }
        }

        when {
            FileUtil.exists(cljkondoPath) -> analyzeWithExecutableLinter(project, cljkondoPath)
            else -> analyzeWithBuiltinLinter(project)
        }
    }

    private fun analyzeWithBuiltinLinter(project: Project) {
        log.info("built-in clj-kondo: analyze '${project.name}' classpath")

        ProgressManager.getInstance()
            .run(object : Task.Backgroundable(project, "Clj-kondo: Analyze project classpath") {
                override fun run(indicator: ProgressIndicator) {
                    val run = getCljKondoRun()
                    val pathsList =
                        OrderEnumerator.orderEntries(project).recursively().librariesOnly().pathsList

                    val basePath = StringUtil.escapeBackSlashes(project.basePath!!)
                    indicator.text = "Analyzing project sources..."
                    run.invoke(Clojure.read("{:config {:output {:format :json}} :copy-configs true :dependencies true :lint [\"$basePath\"]}"))

                    indicator.isIndeterminate = false

                    val configDir = "${basePath}${File.separatorChar}.clj-kondo"

                    pathsList.virtualFiles.forEachIndexed { index, file ->
                        if (!indicator.isCanceled) {
                            log.info("built-in clj-kondo: Linting classpath file: ${file.path}")

                            val filePath = StringUtil.unescapeBackSlashes(file.path)
                            val config =
                                "{:config-dir \"${configDir}\" :copy-configs true :dependencies true :lint [\"$filePath\"]}"

                            indicator.text = "Analyzing '${file.path}'..."
                            indicator.fraction = index.toDouble() / pathsList.virtualFiles.size

                            run.invoke(Clojure.read(config))
                        }
                    }
                }
            })
    }

    private fun analyzeWithExecutableLinter(project: Project, cljkondoPath: String) {
        log.info("cljkondoPath: analyze '${project.name}' classpath")

        ProgressManager.getInstance()
            .run(object : Task.Backgroundable(project, "Clj-kondo: Analyze project classpath") {
                override fun run(indicator: ProgressIndicator) {
                    val pathsList = OrderEnumerator.orderEntries(project).recursively().librariesOnly().pathsList

                    indicator.text = "Analyzing project sources..."
                    commandLineLint(project.basePath!!, project.basePath!!, cljkondoPath)

                    indicator.isIndeterminate = false
                    pathsList.virtualFiles.forEachIndexed { index, file ->
                        if (!indicator.isCanceled) {
                            indicator.text = "Analyzing '${file.path}'..."
                            indicator.fraction = index.toDouble() / pathsList.virtualFiles.size
                            commandLineLint(project.basePath!!, file.path, cljkondoPath)
                        }
                    }

                }
            })
    }

    private fun commandLineLint(basePath: String, filePath: String, cljkondoPath: String) {
        log.info("$cljkondoPath: Linting classpath file: $filePath")
        val command = CljKondoProcessBuilder()
            .workDirectory(basePath)
            .withExePath(cljkondoPath)
            .withLintFile(filePath)
            .withCopyConfigs()
            .withDependencies()
            .build()

        CljKondoProcessRunner()
            .withCommandLine(command.first)
            .withProcess(command.second)
            .withTimeout(30000)
            .run()
    }
}
