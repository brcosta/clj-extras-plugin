package com.github.brcosta.cljstuffplugin.extensions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.TextPanel
import com.intellij.ui.ClickListener
import java.awt.event.MouseEvent
import javax.swing.JComponent

@Suppress("unused")
class FileInfoStatusBarWidgetFactory : StatusBarWidgetFactory {

    companion object {
        private const val ID = "FileInfo"
    }

    override fun getId(): String = ID

    override fun getDisplayName(): String = "File Info"

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget = FileInfoStatusWidget()

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true

    override fun disposeWidget(widget: StatusBarWidget) {}

    @Suppress("DialogTitleCapitalization")
    private class FileInfoStatusWidget : TextPanel(), CustomStatusBarWidget {

        init {
            object : ClickListener() {
                override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
                    return true
                }
            }.installOn(this, true)
        }

        override fun ID(): String = ID

        override fun install(statusBar: StatusBar) {
            val bus = ApplicationManager.getApplication().messageBus
            val connection = bus.connect()
            connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    val manager = FileEditorManagerEx.getInstanceEx(event.manager.project)
                    text = (manager.currentWindow?.tabbedPane?.tabs?.selectedInfo?.text ?: "No selection")
                }
            })
            text = "No selection"
        }

        override fun getComponent(): JComponent = this

        override fun dispose() {}
    }
}
