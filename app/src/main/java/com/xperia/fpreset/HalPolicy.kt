package com.xperia.fpreset

/** Pure policy: no caller-supplied shell commands, service names or sysfs writes. */
data class HalCandidate(val name: String, val binary: String, val file: String, val blocked: Boolean)
object HalPolicy {
    private val namePattern = Regex("[A-Za-z0-9_.-]{1,80}")
    private val binaryPattern = Regex("/(vendor|odm)/bin/hw/android\\.hardware\\.biometrics\\.fingerprint@2\\.[123]-service(?:[.-][A-Za-z0-9_-]+)*")
    fun parse(text: String): List<HalCandidate> {
        val found = mutableListOf<HalCandidate>()
        var file = ""
        var current: HalCandidate? = null
        fun flush() { current?.let { found += it }; current = null }
        for (line in text.lineSequence()) {
            if (line.startsWith("@@FILE ")) { flush(); file = line.removePrefix("@@FILE "); continue }
            val s = line.substringBefore('#').trim()
            if (s.isEmpty()) continue
            val parts = s.split(Regex("\\s+"))
            if (parts[0] == "service" || parts[0] == "on" || parts[0] == "import") {
                flush()
                if (parts[0] == "service" && parts.size == 3 && namePattern.matches(parts[1]) && binaryPattern.matches(parts[2])) {
                    current = HalCandidate(parts[1], parts[2], file, false)
                }
            } else if (parts[0] in setOf("critical", "reboot_on_failure", "onrestart", "override", "oneshot")) {
                current = current?.copy(blocked = true)
            }
        }
        flush()
        // Conflicting declarations, including overrides, are not guessed through.
        return found.groupBy { it.name }.map { (_, entries) ->
            val first = entries.first()
            first.copy(blocked = entries.any { it.blocked } || entries.map { it.binary }.distinct().size != 1)
        }
    }
    fun select(candidates: List<HalCandidate>): HalCandidate {
        require(candidates.size == 1) { "Expected one fingerprint HAL declaration; found ${candidates.size}. Export diagnostics for review." }
        return candidates.single().also { require(!it.blocked) { "HAL has restart-sensitive init options. Automatic restart is disabled." } }
    }
    fun quote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
}
