package com.github.brcosta.cljstuffplugin.cljkondo

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key

class CljKondoProcessRunner {

    private val log = Logger.getInstance(CljKondoProcessRunner::class.java)

    private var commandLine: GeneralCommandLine? = null
    private var process: Process? = null
    private var timeout: Long? = null

    fun withCommandLine(commandLine: GeneralCommandLine) = apply { this.commandLine = commandLine }
    fun withProcess(process: Process) = apply { this.process = process }
    fun withTimeout(timeout: Long) = apply { this.timeout = timeout }

    fun run(): ProcessOutput {
        val output = ProcessOutput()

        process?.let { process ->
            val processHandler: OSProcessHandler =
                ColoredProcessHandler(process, commandLine?.commandLineString, Charsets.UTF_8).apply {
                    addProcessListener(object : ProcessAdapter() {
                        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) =
                            when (outputType) {
                                ProcessOutputTypes.STDERR -> output.appendStderr(event.text)
                                ProcessOutputTypes.STDOUT -> output.appendStdout(event.text)
                                else -> log.debug(event.text)
                            }
                    })
                    startNotify()
                }

            if (processHandler.waitFor(timeout!!)) {
                output.exitCode = process.exitValue()
            } else {
                processHandler.destroyProcess()
                output.setTimeout()
            }

            if (output.isTimeout) {
                throw ExecutionException("Command '" + process.info().commandLine().get() + "' is timed out.")
            }
        }

        return output
    }

}
