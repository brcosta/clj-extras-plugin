package com.github.brcosta.cljstuffplugin.util

import clojure.lang.ClojureLoaderHolder
import clojure.lang.DynamicClassLoader
import java.io.IOException
import java.lang.reflect.Method
import java.net.URL
import java.util.concurrent.Callable

fun <T> runWithClojureClassloader(
    classLoader: ClassLoader = ClojureLoaderHolder.loader.get(),
    callable: Callable<T>
): T {
    val current = Thread.currentThread().contextClassLoader
    try {
        Thread.currentThread().contextClassLoader = classLoader
        return callable.call()
    } finally {
        Thread.currentThread().contextClassLoader = current
    }
}

fun addURL(u: URL) {
    val sysLoader = ClojureLoaderHolder.loader.get() as DynamicClassLoader
    val urls: Array<URL> = sysLoader.urLs
    for (i in urls.indices) {
        if (urls[i].toString() == u.toString()) {
            return
        }
    }
    val sysClass: Class<*> = DynamicClassLoader::class.java
    try {
        val method: Method = sysClass.getDeclaredMethod("addURL", URL::class.java)
        method.isAccessible = true
        method.invoke(sysLoader, u)
    } catch (t: Throwable) {
        throw IOException("Error, could not add URL to system classloader")
    }
}
