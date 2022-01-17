package com.github.brcosta.cljstuffplugin.actions

import clojure.java.api.Clojure
import clojure.lang.ClojureLoaderHolder
import com.github.brcosta.cljstuffplugin.util.AppSettingsState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.*
import com.intellij.notification.Notification
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import java.io.File

open class AnalyzeClasspathAction : AnAction() {
    private val log = Logger.getInstance(AnalyzeClasspathAction::class.java)

    var notification: Notification? = null

    override fun actionPerformed(event: AnActionEvent) {
        if (event.project != null) {
            notification?.expire()
            val settings = AppSettingsState.instance
            val cljkondoPath = settings.cljkondoPath

            event.project!!.basePath?.let {
                val kondoConfig = File(event.project!!.basePath, ".clj-kondo")
                if (!kondoConfig.exists()) {
                    log.info("creating .clj-kondo directory for '${event.project!!.name}'")
                    kondoConfig.mkdir()
                } else {
                    log.info(".clj-kondo already exists")
                }
            }

            when {
                FileUtil.exists(cljkondoPath) -> analyzeWithExecutableLinter(event.project!!, cljkondoPath)
                else -> analyzeWithBuiltinLinter(event.project!!)
            }
        }
    }

    private fun analyzeWithBuiltinLinter(project: Project) {
        log.info("built-in clj-kondo: analyze '${project.name}' classpath")

        ProgressManager.getInstance()
            .run(object : Task.Backgroundable(project, "Clj-kondo: Analyze project classpath") {
                override fun run(indicator: ProgressIndicator) {
                    val current = Thread.currentThread().contextClassLoader
                    try {
                        Thread.currentThread().contextClassLoader = ClojureLoaderHolder.loader.get()
                        val require = Clojure.`var`("clojure.core", "require")
                        require.invoke(Clojure.read("clj-kondo.core"))
                        val run = Clojure.`var`("clj-kondo.core", "run!")
                        val pathsList =
                            OrderEnumerator.orderEntries(project).recursively().librariesOnly().pathsList

                        indicator.text = "Analyzing project sources..."
                        run.invoke(Clojure.read("{:config {:output {:format :json}} :copy-configs true :dependencies true :lint [\"${project.basePath}\"]}"))

                        indicator.isIndeterminate = false
                        pathsList.virtualFiles.forEachIndexed { index, file ->
                            log.info("built-in clj-kondo: Linting classpath file: ${file.path}")

                            val config =
                                "{:copy-configs true :dependencies true :filename \"${project.basePath}\" :lint [\"${file.path}\"]}"
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
                        indicator.text = "Analyzing '${file.path}'..."
                        indicator.fraction = index.toDouble() / pathsList.virtualFiles.size
                        commandLineLint(project.basePath!!, file.path, cljkondoPath)
                    }
                }
            })
    }

    private fun commandLineLint(basePath: String, filePath: String, cljkondoPath: String) {
        log.info("$cljkondoPath: Linting classpath file: $filePath")

        val commandLine = GeneralCommandLine()
        commandLine.workDirectory = File(basePath)
        commandLine.withExePath(cljkondoPath).withParameters("--lint", filePath, "--dependencies", "--copy-configs")

        val process = commandLine.createProcess()
        val processHandler: OSProcessHandler =
            ColoredProcessHandler(process, commandLine.commandLineString, Charsets.UTF_8)

        val output = ProcessOutput()
        processHandler.addProcessListener(object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                if (outputType == ProcessOutputTypes.STDERR) {
                    output.appendStderr(event.text)
                    log.info(event.text)
                } else if (outputType != ProcessOutputTypes.SYSTEM) {
                    output.appendStdout(event.text)
                    log.info(event.text)
                }
            }
        })

        processHandler.startNotify()
        if (processHandler.waitFor(30000)) {
            output.exitCode = process.exitValue()
        } else {
            processHandler.destroyProcess()
            output.setTimeout()
        }

    }
}
