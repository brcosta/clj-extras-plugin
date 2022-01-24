package com.github.brcosta.cljstuffplugin.cljkondo

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.util.text.StringUtil

class CljKondoProcessBuilder {

    private var workDirectory: String? = null
    private var exePath: String? = null
    private var lintFile: String? = null
    private var filename: String? = null
    private var copyConfigs: Boolean? = null
    private var dependencies: Boolean? = null
    private var config: String? = null

    fun workDirectory(workDirectory: String) = apply { this.workDirectory = workDirectory }
    fun withExePath(exePath: String) = apply { this.exePath = exePath }
    fun withLintFile(lintFile: String) = apply { this.lintFile = lintFile }
    fun withFilename(filename: String) = apply { this.filename = filename }
    fun withCopyConfigs() = apply { this.copyConfigs = true }
    fun withDependencies() = apply { this.dependencies = true }

    fun withConfig(config: String) = apply { this.config = config }

    fun build(): Pair<GeneralCommandLine, Process> {
        val commandLine = GeneralCommandLine()
            .withWorkDirectory(workDirectory)
            .withExePath(exePath!!)

        if (lintFile != null) commandLine.addParameters("--lint", StringUtil.escapeBackSlashes(lintFile!!))
        if (filename != null) commandLine.addParameters("--filename", StringUtil.escapeBackSlashes(filename!!))
        if (copyConfigs == true) commandLine.addParameter("--copy-configs")
        if (dependencies == true) commandLine.addParameter("--dependencies")
        if (config != null) commandLine.addParameters("--config", StringUtil.escapeBackSlashes(config!!))


        return Pair(commandLine, commandLine.createProcess())
    }
}
