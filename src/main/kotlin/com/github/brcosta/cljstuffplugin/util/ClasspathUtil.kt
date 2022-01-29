package com.github.brcosta.cljstuffplugin.util

import clojure.lang.ClojureLoaderHolder
import clojure.lang.DynamicClassLoader
import java.net.URL
import java.util.concurrent.Callable

fun <T> runWithClojureClassloader(
    classLoader: ClassLoader = ClojureLoaderHolder.loader.get(),
    callable: Callable<T>,
): T {
    Thread.currentThread().contextClassLoader.let { current ->
        return try {
            Thread.currentThread().contextClassLoader = classLoader
            callable.call()
        } finally {
            Thread.currentThread().contextClassLoader = current
        }
    }
}

fun addURL(u: URL) {
    println("Loading $u")
    (ClojureLoaderHolder.loader.get() as DynamicClassLoader).let { loader ->
        loader.urLs.forEach { url ->
            if ("$url" == "$u") {
                return
            }
        }
        loader.addURL(u)
    }
}
