@file:Suppress("DEPRECATION")

package com.github.brcosta.cljstuffplugin.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.util.ConcurrencyUtil
import java.io.*
import java.net.Socket
import java.net.SocketException
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.ArrayList
import kotlin.reflect.KProperty

/**
 * @author gregsh
 * original src: https://github.com/gregsh/Clojure-Kit/blob/master/src/tools/nrepl-client.kt
 */
private val LOG = Logger.getInstance(NReplClient::class.java)

private object ClientID {
    private val id = AtomicLong(0)
    operator fun getValue(thisRef: Any?, property: KProperty<*>) = id.incrementAndGet()
}

inline fun <reified T : Any> Any?.cast(): T? = this as? T

class NReplClient {

    private var transport: Transport = NOT_CONNECTED
    val isConnected: Boolean get() = transport != NOT_CONNECTED

    private val nextId: Long by ClientID
    private val callbacks = ConcurrentHashMap<Long, Request>()
    private val partialResponses = HashMap<Long, MutableMap<String, Any?>>()

    private var mainSession = ""
    private var defaultRequest: Request? = null

    fun connect(host: String, port: Int, sessionId: String) {
        if (isConnected) throw IllegalStateException("Already connected")
        try {
            transport = AsyncTransport(SocketTransport(Socket(host, port))) { o -> runCallbacks(o) }
            mainSession = sessionId
        } catch (e: Exception) {
            if (transport != NOT_CONNECTED) disconnect()
        }
    }

    fun disconnect() {
        if (!isConnected) {
            return
        }
        try {
            defaultRequest = null
        } finally {
            val tmp = transport
            transport = NOT_CONNECTED
            val reason = (tmp as? AsyncTransport)?.closed?.get() ?: ProcessCanceledException()
            try {
                tmp.close()
            } catch (ignore: Throwable) {
            } finally {
                try {
                    clearCallbacks(reason)
                } catch (ignore: Throwable) {
                }
            }
        }
    }

    inner class Request {
        constructor(op: String, namespace: String?) {
            this.op = op
            this.namespace = namespace
        }

        internal val map = HashMap<String, Any?>()
        internal val future = CompletableFuture<Map<String, Any?>>()
        var stdout: ((String) -> Unit)? = null
        var stderr: ((String) -> Unit)? = null
        var stdin: (((String) -> Unit) -> Unit)? = null

        private var op: String?
            get() = get("op") as? String
            set(op) {
                set("op", op)
            }
        var session: Any?
            get() = get("session")
            set(op) {
                set("session", op)
            }

        @Suppress("unused")
        var namespace: String?
            get() = get("ns") as String?
            set(op) {
                set("ns", op)
            }
        var code: String?
            get() = get("code") as String?
            set(op) {
                set("code", op)
            }

        operator fun get(prop: String) = map[prop]
        operator fun set(prop: String, value: Any?) {
            if (value == null) map -= prop else map[prop] = value
        }

        fun send() = send(this)
    }

    private fun send(r: Request): CompletableFuture<Map<String, Any?>> {
        val id = nextId
        r["id"] = id
        callbacks[id] = r
        try {
            transport.send(r.map)
        } catch (ex: IOException) {
            try {
                transport.close()
            } catch (ignore: Throwable) {
            }
            transport = NOT_CONNECTED
            r.future.completeExceptionally(ex)
        } catch (ex: Throwable) {
            r.future.completeExceptionally(ex)
        }
        return r.future
    }

    private fun runCallbacks(o: Any?) {
        val m = o.cast<Map<String, Any?>>() ?: clearCallbacks(o as? Throwable ?: Throwable(o.toString())).run { return }
        val id = m["id"] as? Long ?: return
        val r = callbacks[id] ?: defaultRequest ?: return
        val status = m["status"] as? List<*> ?: emptyList<String>()
        r.stdout?.let { handler -> (m["out"] as? String)?.let { msg -> handler(msg) } }
        r.stderr?.let { handler -> (m["err"] as? String)?.let { msg -> handler(msg) } }
        if (status.contains("need-input")) r.stdin?.let { handler ->
            handler { input ->
                request("stdin", null) {
                    session = r.session
                    stdin = r.stdin
                    stdout = r.stdout
                    set("stdin", input)
                }.send()
            }
        }
        val keyOp: (String) -> JoinOp = {
            when (it) {
                "id", "session", "root-ex" -> JoinOp.SKIP
                "status" -> JoinOp.OVERRIDE
                "out" -> if (r.stdout != null) JoinOp.SKIP else JoinOp.JOIN
                "err" -> if (r.stderr != null) JoinOp.SKIP else JoinOp.JOIN
                else -> JoinOp.JOIN
            }
        }
        if (status.contains("done")) {
            val combined = (partialResponses.remove(id) ?: LinkedHashMap()).apply { joinMaps(m, keyOp) }
            callbacks.remove(id)?.future?.complete(combined)
        } else {
            partialResponses[id] = (partialResponses[id] ?: LinkedHashMap()).apply { joinMaps(m, keyOp) }
        }
    }

    private fun clearCallbacks(reason: Throwable) {
        val cb = HashMap(callbacks)
        callbacks.clear()
        for (value in cb.values) {
            value.future.completeExceptionally(reason)
        }
    }

    private enum class JoinOp { SKIP, OVERRIDE, JOIN }

    private fun MutableMap<String, Any?>.joinMaps(m: Map<String, Any?>, keyOp: (String) -> JoinOp = { JoinOp.JOIN }) {
        m.keys.forEach loop@{ key ->
            val val1 = get(key)
            val val2 = m[key]
            val op = keyOp(key)
            when {
                op == JoinOp.SKIP -> remove(key)
                val1 == null || op == JoinOp.OVERRIDE -> put(key, val2)
                val1 is ArrayList<*> -> val1.cast<ArrayList<Any?>>()!!
                    .run { if (val2 is Collection<*>) addAll(val2) else add(val2) }
                val1 is MutableMap<*, *> && val2 is Map<*, *> -> val1.cast<MutableMap<String, Any?>>()!!
                    .joinMaps(val2.cast()!!)
                key == "value" -> put(key, ArrayList(listOf(val1, val2)))
                key == "ns" -> put(key, val2)
                val1 is String && val2 is String -> put(key, val1 + val2)
            }
        }
    }

    fun eval(code: String? = null, namespace: String?, f: Request.() -> Unit = {}) = request("eval", namespace) {
        this.code = code
        f(this)
    }.send()

    fun interrupt() = if (isConnected) request("interrupt", "") {}.send() else null

    private fun request(op: String, namespace: String?, f: Request.() -> Unit = {}): Request =
        Request(op, namespace).apply {
            f()
            if (session == null && mainSession != "") session = mainSession
        }
}

abstract class Transport : Closeable {
    open fun recv(): Any? = recv(Long.MAX_VALUE)
    abstract fun recv(timeout: Long): Any?
    abstract fun send(message: Any)
    override fun close() = Unit
}

val NOT_CONNECTED = object : Transport() {
    override fun recv(timeout: Long) = throw IllegalStateException()
    override fun send(message: Any) = throw IllegalStateException()
}

class AsyncTransport(private val delegate: Transport, private val responseHandler: (Any?) -> Any) : Transport() {
    companion object {
        private val threadPool = Executors.newCachedThreadPool(
            ConcurrencyUtil.newNamedThreadFactory(
                "clojure-kit-nrepl",
                true,
                Thread.NORM_PRIORITY
            )
        )
    }

    val closed = AtomicReference<Throwable>()
    private val reader = threadPool.submit {
        while (closed.get() == null) {
            val response = try {
                delegate.recv()
            } catch (ex: Throwable) {
                closed.set(ex)
                ex
            }
            try {
                responseHandler(response)
            } catch (ex: Throwable) {
                try {
                    LOG.error(ex)
                } catch (ignore: Throwable) {
                }
            }
        }
    }

    override fun recv(timeout: Long) = throw IllegalStateException()

    override fun send(message: Any) = closed.get()?.let { throw it } ?: delegate.send(message)

    override fun close() {
        try {
            closed.compareAndSet(null, ProcessCanceledException())
            delegate.close()
        } catch (t: Throwable) {
            LOG.warn(t)
        } finally {
            reader.cancel(true)
        }
    }
}

class SocketTransport(private val socket: Socket) : Transport() {
    private val input = BEncodeInput(socket.inputStream)
    private val output = BEncodeOutput(socket.outputStream)

    override fun recv(timeout: Long) = wrap { input.read() }
    override fun send(message: Any) = wrap {
        synchronized(output) {
            output.write(message); output.stream.flush()
        }
    }

    override fun close() = socket.close()

    private fun <T> wrap(proc: () -> T): T = try {
        proc()
    } catch (e: EOFException) {
        throw SocketException("The transport's socket appears to have lost its connection to the nREPL server")
    } catch (e: Throwable) {
        throw if (!socket.isConnected)
            SocketException("The transport's socket appears to have lost its connection to the nREPL server")
        else e
    }
}

class BEncodeInput(stream: InputStream) {
    private val stream = PushbackInputStream(stream)

    fun read(): Any? = readCh().let { token ->
        when (token) {
            'e' -> null
            'i' -> readLong('e')
            'l' -> readList()
            'd' -> readMap()
            else -> stream.unread(token.toInt()).let {
                val bytes = readNetstringInner()
                try {
                    String(bytes)
                } catch (e: Exception) {
                    bytes
                }
            }
        }
    }

    private fun readList(): List<Any> {
        val result = ArrayList<Any>()
        while (true) {
            result.add(read() ?: return result)
        }
    }

    private fun readMap(): Map<String, Any?> {
        val result = LinkedHashMap<String, Any?>()
        while (true) {
            result[read() as? String ?: return result] = read()
        }
    }

    private fun readLong(delim: Char): Long {
        var result = 0L
        var negate = false
        while (true) {
            val b = readCh()
            if (b == delim) return result
            if (b == '-' && result == 0L && !negate) negate = true
            else if (b in '0'..'9') result = result * 10 + (b - '0')
            else throw IOException("Invalid long. Unexpected $b encountered.")
        }
    }

    private fun readNetstringInner(): ByteArray {
        return readBytes(readLong(':').toInt())
    }

    private fun readBytes(n: Int): ByteArray {
        val result = ByteArray(n)
        var offset = 0
        var left = n
        while (true) {
            val actual = stream.read(result, offset, left)
            if (actual < 0) throw EOFException("Invalid netstring. Less data available than expected.")
            else if (actual == left) return result
            else {
                offset += actual; left -= actual
            }
        }
    }

    private fun readCh(): Char {
        val c = stream.read()
        if (c < 0) throw EOFException("Invalid netstring. Unexpected end of input.")
        return c.toChar()
    }
}

class BEncodeOutput(val _stream: OutputStream) {
    val bos = ByteArrayOutputStream()
    val stream = object : OutputStream() {
        override fun write(b: Int) {
            bos.write(b)
            _stream.write(b)
        }
    }

    fun write(o: Any): Unit = when (o) {
        is ByteArray -> writeNetstringInner(o)
        is InputStream -> writeNetstringInner(ByteArrayOutputStream().let { o.copyTo(it); it.toByteArray() })
        is Number -> writeLong(o)
        is String -> writeNetstringInner(o.toByteArray(Charsets.UTF_8))
        is Map<*, *> -> writeMap(o)
        is Iterable<*> -> writeList(o)
        is Array<*> -> writeList(o.asList())
        else -> throw IllegalArgumentException("Cannot write value of type ${o.javaClass.name}")
    }

    private fun writeLong(o: Number) {
        stream.write('i'.toInt())
        stream.write(o.toString().toByteArray(Charsets.UTF_8))
        stream.write('e'.toInt())
    }

    private fun writeList(o: Iterable<*>) {
        stream.write('l'.toInt())
        o.forEach { write(it!!) }
        stream.write('e'.toInt())
    }

    private fun writeMap(o: Map<*, *>) {
        stream.write('d'.toInt())
        val sorted = ArrayList<Pair<Any, ByteArray>>(o.size).apply {
            o.keys.forEach {
                add(Pair(it!!, it.toString().toByteArray(Charsets.UTF_8)))
            }
        }
        sorted.sortWith { p1, p2 -> compare(p1.second, p2.second) }
        sorted.forEach { p -> write(p.second); write(o[p.first]!!) }
        stream.write('e'.toInt())
    }

    private fun writeNetstringInner(o: ByteArray) {
        stream.write(o.size.toString().toByteArray(Charsets.UTF_8))
        stream.write(':'.toInt())
        stream.write(o)
    }

    private fun compare(b1: ByteArray, b2: ByteArray): Int {
        for (i in 0 until b1.size.coerceAtMost(b2.size)) {
            (b1[i] - b2[i]).let { if (it != 0) return it }
        }
        return b1.size - b2.size
    }
}
