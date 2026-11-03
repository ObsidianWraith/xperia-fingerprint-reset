package com.xperia.fpreset

import org.junit.Assert.*
import org.junit.Test

class HalPolicyTest {
    private val path = "/vendor/bin/hw/android.hardware.biometrics.fingerprint@2.1-service.sony"
    private fun rc(extra: String = "") = "@@FILE /vendor/etc/init/fp.rc\nservice vendor.fps_hal $path\n    class hal\n$extra"
    @Test fun discoversActualServiceName() {
        assertEquals("vendor.fps_hal", HalPolicy.select(HalPolicy.parse(rc())).name)
    }
    @Test fun rejectsCriticalAndRestartSideEffects() {
        for (option in listOf("critical", "reboot_on_failure reboot", "onrestart restart zygote", "override", "oneshot")) {
            assertThrows(IllegalArgumentException::class.java) { HalPolicy.select(HalPolicy.parse(rc("    $option"))) }
        }
    }
    @Test fun rejectsAmbiguousServices() {
        val text = rc() + "\nservice another_fp $path\n"
        assertThrows(IllegalArgumentException::class.java) { HalPolicy.select(HalPolicy.parse(text)) }
    }
    @Test fun refusesGuessedNamesAndUnrelatedExecutables() {
        val text = "service vendor.fps_hal /system/bin/sh\nservice test;reboot $path\nservice fp $path --argument\n"
        assertTrue(HalPolicy.parse(text).isEmpty())
    }
    @Test fun commentsAndNewSectionsDoNotLeakFlags() {
        val text = rc("# critical\non boot\n    onrestart restart zygote\nservice other /vendor/bin/other\n    critical")
        assertFalse(HalPolicy.select(HalPolicy.parse(text)).blocked)
    }
    @Test fun shellQuoteEscapesSingleQuote() {
        assertEquals("'a'\\''b'", HalPolicy.quote("a'b"))
    }
    @Test fun ignoresSimilarButNonFingerprintBinary() {
        assertTrue(HalPolicy.parse("service fp /vendor/bin/hw/not-fingerprint").isEmpty())
    }
}
