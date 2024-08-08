package com.github.brcosta.cljstuffplugin.extensions

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener


class ProjectListener : ProjectManagerListener {
    @Deprecated("Deprecated in Java")
    override fun projectOpened(project: Project) {
        LOG.debug("Project Opened ${project.name}")



    }

    override fun projectClosing(project: Project) {
        LOG.debug("Project Closed ${project.name}")

    }

    companion object {
        private val LOG: Logger = Logger.getInstance(ProjectListener::class.java)
    }
}
