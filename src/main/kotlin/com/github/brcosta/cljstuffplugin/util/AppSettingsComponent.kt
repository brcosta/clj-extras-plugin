package com.github.brcosta.cljstuffplugin.util

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class AppSettingsComponent {
    val panel: JPanel
    private val cljkondoPath = JBTextField()
    private val cljkondoEnabled = JBCheckBox("Enable clj-kondo inspections")
    private val prettyPrint = JBCheckBox("Pretty print inline evaluation results")
    private val redirectStdoutToRepl = JBCheckBox("Redirect Stdout from inline evaluation to REPL console")

    init {
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(
                // TODO: get this from build.gradle
                JBLabel("Clj-Kondo executable path (leave empty to use built-in version 2022.06.22):"),
                cljkondoPath,
                1,
                true
            )
            .addComponent(cljkondoEnabled, 2)
            .addComponent(prettyPrint, 8)
            .addComponent(redirectStdoutToRepl, 2)
            .addComponentFillVertically(JPanel(), 4)
            .panel
    }

    val preferredFocusedComponent: JComponent
        get() = cljkondoPath

    fun getCljkondoPath(): String {
        return cljkondoPath.text
    }

    fun setCljkondoPath(text: String?) {
        cljkondoPath.text = text
    }

    fun getCljkondoEnabled(): Boolean {
        return cljkondoEnabled.isSelected
    }

    fun setCljkondoEnabled(newStatus: Boolean) {
        cljkondoEnabled.isSelected = newStatus
    }

    fun getPrettyPrint(): Boolean {
        return prettyPrint.isSelected
    }

    fun setPrettyPrint(newStatus: Boolean) {
        prettyPrint.isSelected = newStatus
    }

    fun getRedirectStdoutToRepl(): Boolean {
        return redirectStdoutToRepl.isSelected
    }

    fun setRedirectStdoutToRepl(newStatus: Boolean) {
        redirectStdoutToRepl.isSelected = newStatus
    }

}
