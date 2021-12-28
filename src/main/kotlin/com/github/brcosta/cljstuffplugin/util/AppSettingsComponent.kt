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

    init {
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Clj-kondo executable path: "), cljkondoPath, 1, false)
            .addComponent(cljkondoEnabled, 1)
            .addComponentFillVertically(JPanel(), 0)
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
}
