package com.xperia.fpreset

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Runs the actual generated script against a fake init/PID environment; never invokes su. */
class RestartScriptTest {
    private fun execute(mode: String): Pair<Int, String> {
        assumeTrue(java.io.File("/bin/sh").exists())
        val dir = Files.createTempDirectory("fp-reset-test").toFile()
        try {
            fun command(name: String, body: String) = java.io.File(dir, name).apply {
                writeText("#!/bin/sh\n$body\n"); setExecutable(true)
            }
            val fakeHal = command("hal", "exit 0")
            command("id", "echo 0")
            command("sleep", "exit 0")
            command("getprop", "echo running")
            command("pidof", """
                if [ ! -f "${'$'}TEST_STATE" ]; then echo 100; else
                    case "${'$'}TEST_MODE" in
                        unchanged) echo 100;;
                        crashloop) n=${'$'}(cat "${'$'}TEST_STATE"); n=${'$'}((n + 1)); echo "${'$'}n" > "${'$'}TEST_STATE"; echo "${'$'}n";;
                        *) echo 200;;
                    esac
                fi
            """.trimIndent())
            command("setprop", """
                [ "${'$'}TEST_MODE" = denied ] && exit 1
                [ "${'$'}1" = ctl.restart ] || exit 2
                [ "${'$'}2" = vendor.fps_hal ] || exit 3
                echo 200 > "${'$'}TEST_STATE"
            """.trimIndent())
            val path = "/vendor/bin/hw/android.hardware.biometrics.fingerprint@2.1-service.sony"
            val script = FingerprintResetManager.restartScript(HalCandidate("vendor.fps_hal", path, "test", false))
                .replace("exe='${path}'", "exe=${HalPolicy.quote(fakeHal.absolutePath)}")
            val process = ProcessBuilder("/bin/sh", "-c", script).redirectErrorStream(true).apply {
                environment()["PATH"] = "${dir.absolutePath}:/usr/bin:/bin"
                environment()["TEST_STATE"] = java.io.File(dir, "state").absolutePath
                environment()["TEST_MODE"] = mode
            }.start()
            val done = process.waitFor(8, TimeUnit.SECONDS)
            if (!done) process.destroyForcibly()
            assertTrue("Script did not terminate", done)
            return process.exitValue() to process.inputStream.bufferedReader().readText()
        } finally { dir.deleteRecursively() }
    }
    @Test fun stableNewPidIsAccepted() {
        val result = execute("success")
        assertEquals(0, result.first)
        assertTrue(result.second.contains("@@OLD_PID=100"))
        assertTrue(result.second.contains("@@NEW_PID=200"))
    }
    @Test fun unchangedRunningPidIsNotSuccess() { assertEquals(14, execute("unchanged").first) }
    @Test fun crashLoopIsNotSuccess() { assertEquals(14, execute("crashloop").first) }
    @Test fun deniedControlPropertyFails() { assertEquals(13, execute("denied").first) }
}
