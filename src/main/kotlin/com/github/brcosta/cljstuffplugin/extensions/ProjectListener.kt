package com.github.brcosta.cljstuffplugin.extensions

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener


class ProjectListener : ProjectManagerListener {

    override fun projectClosing(project: Project) {
        LOG.debug("Project Closed ${project.name}")

    }

    companion object {
        private val LOG: Logger = Logger.getInstance(ProjectListener::class.java)
    }
}
