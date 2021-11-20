package com.github.brcosta.cljstuffplugin.services

import com.intellij.openapi.project.Project
import com.github.brcosta.cljstuffplugin.MyBundle

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}
