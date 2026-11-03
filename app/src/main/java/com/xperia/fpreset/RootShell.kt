package com.xperia.fpreset

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

data class ShellResult(val code: Int, val output: String, val timedOut: Boolean = false)
object RootShell {
    /** Dedicated su process; bounded output is continuously drained to prevent pipe deadlock. */
    suspend fun run(script: String, seconds: Long = 45, onOutput: ((String) -> Unit)? = null): ShellResult = withContext(Dispatchers.IO) {
        val p = try { ProcessBuilder("su", "-c", script).redirectErrorStream(true).start() }
        catch (e: Exception) { return@withContext ShellResult(-1, "Root unavailable: ${e.message}") }
        p.outputStream.close()
        val output = StringBuffer()
        val reader = thread(isDaemon = true, name = "su-output") {
            try {
                p.inputStream.bufferedReader().use { r ->
                    val buf = CharArray(2048)
                    while (true) {
                        val n = r.read(buf)
                        if (n < 0) break
                        synchronized(output) {
                            val room = 262144 - output.length
                            if (room > 0) {
                                val count = minOf(n, room)
                                output.append(buf, 0, count)
                                onOutput?.invoke(String(buf, 0, count))
                            }
                        }
                    }
                }
            } catch (_: Exception) { /* timeout may close stream */ }
        }
        try {
            val done = p.waitFor(seconds, TimeUnit.SECONDS)
            if (!done) { p.destroy(); if (!p.waitFor(1, TimeUnit.SECONDS)) p.destroyForcibly() }
            reader.join(1500)
            ShellResult(if (done) p.exitValue() else -1, output.toString(), !done)
        } finally {
            runCatching { p.inputStream.close() }
            if (p.isAlive) p.destroyForcibly()
        }
    }
}
