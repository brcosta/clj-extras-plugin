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
            .addLabeledComponent(JBLabel("Clj-Kkndo executable path (leave empty to use built-in v.2022.01.15):"), cljkondoPath, 1, false)
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
