package com.github.brcosta.cljstuffplugin.util

import com.intellij.openapi.options.Configurable
import javax.swing.JComponent

class AppSettingsConfigurable : Configurable {
    private var mySettingsComponent: AppSettingsComponent? = null

    override fun getPreferredFocusedComponent(): JComponent {
        return mySettingsComponent!!.preferredFocusedComponent
    }

    override fun createComponent(): JComponent {
        mySettingsComponent = AppSettingsComponent()
        return mySettingsComponent!!.panel
    }

    override fun isModified(): Boolean {
        val settings: AppSettingsState = AppSettingsState.instance
        var modified: Boolean = mySettingsComponent?.getCljkondoPath() != settings.cljkondoPath
        modified = modified or (mySettingsComponent?.getCljkondoEnabled() != settings.cljkondoEnabled)
        modified = modified or (mySettingsComponent?.getPrettyPrint() != settings.prettyPrint)
        modified = modified or (mySettingsComponent?.getRedirectStdoutToRepl() != settings.redirectStdoutToRepl)
        return modified
    }

    override fun apply() {
        val settings: AppSettingsState = AppSettingsState.instance
        mySettingsComponent?.let {
            settings.cljkondoPath = it.getCljkondoPath()
            settings.cljkondoEnabled = it.getCljkondoEnabled()
            settings.prettyPrint = it.getPrettyPrint()
            settings.redirectStdoutToRepl = it.getRedirectStdoutToRepl()
        }
    }

    override fun reset() {
        val settings: AppSettingsState = AppSettingsState.instance
        mySettingsComponent?.setCljkondoPath(settings.cljkondoPath)
        mySettingsComponent?.setCljkondoEnabled(settings.cljkondoEnabled)
        mySettingsComponent?.setPrettyPrint(settings.prettyPrint)
        mySettingsComponent?.setRedirectStdoutToRepl(settings.redirectStdoutToRepl)
    }

    override fun disposeUIResources() {
        mySettingsComponent = null
    }

    override fun getDisplayName(): String {
        return "Clojure Extras"
    }
}
