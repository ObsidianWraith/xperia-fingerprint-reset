package com.xperia.fpreset

import android.os.Build

data class ResetOutcome(val oldPid: String, val newPid: String, val consoleLogs: String)

object FingerprintResetManager {
    private val models = setOf("XQ-BC52", "XQ-BC62", "XQ-BC72", "SO-51B", "SOG03")
    fun supported() = (Build.DEVICE.lowercase().contains("pdx215") || Build.MODEL.uppercase() in models) && Build.VERSION.SDK_INT in 30..33

    // Fixed read-only commands. Root is still needed for full init/sysfs visibility.
    val discoverScript = """
        [ "${'$'}(id -u)" = 0 ] || { echo 'Root permission was not granted'; exit 10; }
        echo '@@DEVICE'
        getprop ro.product.model
        getprop ro.product.device
        getprop ro.build.fingerprint
        uname -r
        for f in /vendor/etc/init/*.rc /odm/etc/init/*.rc /vendor/etc/init/hw/*.rc /odm/etc/init/hw/*.rc; do
            [ -r "${'$'}f" ] || continue
            if grep -q 'biometrics.fingerprint' "${'$'}f"; then
                echo "@@FILE ${'$'}f"
                cat "${'$'}f"
                echo
            fi
        done
        echo '@@FILE END'
    """.trimIndent()

    suspend fun diagnose(log: (String) -> Unit): Boolean {
        log("Read-only discovery. No services or drivers will be changed.")
        val r = RootShell.run(discoverScript)
        log(r.output)
        if (r.code != 0 || r.timedOut) { ResetStore.status("Unavailable / denied", "Unknown", "—"); log("Discovery failed or root authorization timed out."); return false }
        ResetStore.status("Granted", "Unknown", "—")
        val candidates = HalPolicy.parse(r.output)
        log("HAL candidates: " + candidates.joinToString { "${it.name} → ${it.binary} (blocked=${it.blocked})" })
        val details = RootShell.run("""
            echo '@@INIT STATES'
            getprop | grep -iE 'init.svc.*(finger|fps|biometric)' || true
            echo '@@DRIVERS (read-only candidates; not verified for rebind)'
            for bus in platform spi i2c; do
                for d in /sys/bus/${'$'}bus/drivers/*; do
                    [ -d "${'$'}d" ] || continue
                    case "${'$'}d" in *egis*|*et5*|*esfp*|*fingerprint*|*goodix*|*fpc*)
                        echo "${'$'}d"; ls -l "${'$'}d" ;;
                    esac
                done
            done
            echo '@@FINGERPRINT FRAMEWORK'
            /system/bin/timeout 5 dumpsys fingerprint
            echo '@@AVAILABLE SHELL HELP'
            /system/bin/timeout 3 cmd fingerprint help
            /system/bin/timeout 3 cmd biometric help
            exit 0
        """.trimIndent(), 25)
        log(details.output)
        if (details.timedOut) log("Some diagnostics timed out.")
        log("Hardware rebind is not enabled. This report identifies candidates, not safe reset targets.")
        return true
    }

    suspend fun resetSensor(log: (String) -> Unit): ResetOutcome {
        require(supported()) { "Reset supports Xperia 1 III on Android 11–13 only. Diagnostics remain available." }
        log("Stage 1 — identify the installed fingerprint service")
        val discovery = RootShell.run(discoverScript)
        if (discovery.code != 0 || discovery.timedOut) {
            ResetStore.status("Unavailable / denied", "Unknown", "—")
            log(discovery.output); error("Root denied, unavailable, or timed out.")
        }
        ResetStore.status("Granted", "Checking…", "—")
        val hal = HalPolicy.select(HalPolicy.parse(discovery.output))
        log("Selected ${hal.name}\n${hal.binary}\nDeclared in ${hal.file}")
        log("Hardware bus reset: skipped (no verified driver profile for this firmware).")
        log("Stage 2 — ask Android init to restart the fingerprint HAL")
        val r = RootShell.run(restartScript(hal), 35, log)
        check(r.code == 0 && !r.timedOut) { "HAL restart was not verified. See the log. A timeout does not prove that nothing changed." }
        log("Stage 3 — allow framework recovery through its normal HAL death/reconnection handling.")
        log("HAL process restart verified. Sensor recovery still needs a successful fingerprint test; unlock-screen behavior must be tested separately.")
        val oldPid = marker(r.output, "@@OLD_PID").ifBlank { "none" }
        val newPid = marker(r.output, "@@NEW_PID")
        ResetStore.status("Granted", "Running (process only)", newPid)
        return ResetOutcome(oldPid, newPid, r.output)
    }

    private fun marker(text: String, key: String): String = text.lineSequence().firstOrNull { it.startsWith("$key=") }?.substringAfter("=")?.trim().orEmpty()

    suspend fun refresh(log: (String) -> Unit) {
        val discovery = RootShell.run(discoverScript)
        if (discovery.code != 0 || discovery.timedOut) {
            ResetStore.status("Unavailable / denied", "Unknown", "—")
            log(discovery.output)
            error("Root unavailable; status cannot be verified.")
        }
        ResetStore.status("Granted", "Unknown", "—")
        val hal = HalPolicy.select(HalPolicy.parse(discovery.output))
        val r = RootShell.run("echo @@STATE=\$(getprop init.svc.${hal.name}); echo @@PID=\$(pidof ${HalPolicy.quote(hal.binary)})", 15)
        check(r.code == 0 && !r.timedOut) { "Status query failed." }
        ResetStore.status("Granted", marker(r.output, "@@STATE").ifBlank { "Unknown" }, marker(r.output, "@@PID").ifBlank { "none" })
        log("Status snapshot for ${hal.name}:\n${r.output}A running PID does not prove sensor responsiveness.")
    }

    suspend fun kernelLogs(log: (String) -> Unit) {
        log("Last 15 matching kernel log lines (when permitted):")
        val r = RootShell.run("dmesg 2>&1 | grep -iE 'egis|et5xx|esfp|fingerprint|fps|denied|permitted' | tail -n 15", 10)
        log(r.output.ifBlank { "No matching lines returned, or kernel log access is unavailable." })
    }

    fun restartScript(hal: HalCandidate): String {
        // Revalidate even though this method is internal and input comes from the parser.
        val safe = HalPolicy.select(HalPolicy.parse("@@FILE internal\nservice ${hal.name} ${hal.binary}\n"))
        val name = HalPolicy.quote(safe.name)
        val binary = HalPolicy.quote(safe.binary)
        return """
            [ "${'$'}(id -u)" = 0 ] || exit 10
            svc=$name
            exe=$binary
            [ -x "${'$'}exe" ] || { echo 'HAL executable is missing'; exit 11; }
            state=${'$'}(getprop "init.svc.${'$'}svc")
            case "${'$'}state" in running|stopped|restarting) ;; *) echo 'Init service is not known'; exit 12;; esac
            old=${'$'}(pidof "${'$'}exe")
            echo "Before: state=${'$'}state pid=${'$'}old"
            echo "@@OLD_PID=${'$'}old"
            setprop ctl.restart "${'$'}svc" || { echo 'Init rejected restart (root SELinux policy may deny it)'; exit 13; }
            # Waiting for a changed PID prevents stale 'running' from looking like success.
            i=0
            last=''
            stable=0
            while [ "${'$'}i" -lt 20 ]; do
                sleep 1
                now=${'$'}(pidof "${'$'}exe")
                state=${'$'}(getprop "init.svc.${'$'}svc")
                if [ "${'$'}state" = running ] && [ -n "${'$'}now" ] && [ "${'$'}now" != "${'$'}old" ]; then
                    if [ "${'$'}now" = "${'$'}last" ]; then stable=${'$'}((stable + 1)); else stable=1; fi
                    if [ "${'$'}stable" -ge 3 ]; then echo "Restart verified: pid=${'$'}now, stable for 3 polls"; echo "@@NEW_PID=${'$'}now"; exit 0; fi
                else
                    stable=0
                fi
                last=${'$'}now
                i=${'$'}((i + 1))
            done
            echo "Could not verify restart: state=${'$'}state pid=${'$'}now"
            exit 14
        """.trimIndent()
    }
}
