package com.neteinstein.smsredirect.domain.update

/** Compares dotted numeric version names such as `2.0.11` or tags such as `v2.0.11`. */
object AppVersions {
    /**
     * True when [candidate] is strictly newer than [current]. A version that cannot be parsed is
     * never considered newer, so a malformed tag can not trigger an update prompt.
     */
    fun isNewer(
        candidate: String,
        current: String,
    ): Boolean {
        val candidateParts = parse(candidate) ?: return false
        val currentParts = parse(current) ?: return true
        val length = maxOf(candidateParts.size, currentParts.size)
        for (i in 0 until length) {
            val a = candidateParts.getOrElse(i) { 0 }
            val b = currentParts.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** `v2.0.11-beta+3` -> [2, 0, 11]; null if the core is not dot-separated integers. */
    internal fun parse(version: String): List<Int>? {
        val core =
            version
                .trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore('-')
                .substringBefore('+')
        if (core.isEmpty()) return null
        return core.split('.').map { it.toIntOrNull()?.takeIf { n -> n >= 0 } ?: return null }
    }
}
