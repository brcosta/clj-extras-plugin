package com.github.brcosta.cljstuffplugin.cljkondo

import clojure.java.api.Clojure
import clojure.lang.IFn
import com.github.brcosta.cljstuffplugin.extensions.CljKondoAnnotator
import com.github.brcosta.cljstuffplugin.util.addURL
import com.github.brcosta.cljstuffplugin.util.runWithClojureClassloader
import com.intellij.ide.plugins.cl.PluginClassLoader
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

val initialized = AtomicBoolean(false)

fun initKondo() {
    if (!initialized.get()) {
        initialized.set(true)
        loadKondoDependencies()
        requireClojureDependencies()
    }
}

private fun loadKondoDependencies() {
    val pluginsPath = (CljKondoAnnotator::class.java.classLoader as PluginClassLoader).pluginDescriptor.pluginPath
    val libsPath = "${pluginsPath}${File.separatorChar}lib"
    File(libsPath).listFiles()?.forEach { addURL(it.toURI().toURL()) }
}

private fun requireClojureDependencies() {
    runWithClojureClassloader {
        val require = Clojure.`var`("clojure.core", "require")
        require.invoke(Clojure.read("clj-kondo.core"))
        require.invoke(Clojure.read("cheshire.core"))
    }
}

fun getCljKondoRun(): IFn {
    return Clojure.`var`("clj-kondo.core", "run!")
}

fun getCheshirePrint(): IFn {
    return Clojure.`var`("cheshire.core", "generate-string")
}
