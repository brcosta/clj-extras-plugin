package com.github.brcosta.cljstuffplugin.cljkondo

import clojure.java.api.Clojure
import clojure.lang.IFn
import com.github.brcosta.cljstuffplugin.extensions.CljKondoAnnotator
import com.github.brcosta.cljstuffplugin.util.addURL
import com.github.brcosta.cljstuffplugin.util.runWithClojureClassloader
import com.intellij.ide.plugins.cl.PluginClassLoader
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

val initializing = AtomicBoolean(false)
val initialized = AtomicBoolean(false)
val completableFuture = CompletableFuture<Boolean>()

fun initKondo(): CompletableFuture<Boolean> {
    if (initialized.get() || initializing.get()) return completableFuture
    Executors.newCachedThreadPool().submit {
        runWithClojureClassloader {
            initializing.set(true)
            loadKondoDependencies()
            requireClojureDependencies()
            initialized.set(true)
            initializing.set(false)
        }
        completableFuture.complete(true)
    }
    return completableFuture
}


private fun loadKondoDependencies() {
    val pluginsPath = (CljKondoAnnotator::class.java.classLoader as PluginClassLoader).pluginDescriptor.pluginPath
    val libsPath = "${pluginsPath}${File.separatorChar}lib"
    File(libsPath).listFiles()?.forEach { addURL(it.toURI().toURL()) }
}

private fun requireClojureDependencies() {
    val require = Clojure.`var`("clojure.core", "require")
    require.invoke(Clojure.read("clj-kondo.core"))
    require.invoke(Clojure.read("cheshire.core"))
}

fun getCljKondoRun(): IFn {
    initKondo().get()
    return Clojure.`var`("clj-kondo.core", "run!")
}

fun getCheshirePrint(): IFn {
    initKondo().get()
    return Clojure.`var`("cheshire.core", "generate-string")
}
