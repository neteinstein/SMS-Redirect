package com.neteinstein.smsredirect.domain.model

import java.security.MessageDigest

/**
 * Derives the stable identity used to tell whether an SMS has already been backed up.
 *
 * Deliberately not the `content://sms` `_id`: SQLite reuses provider ids after a delete, and
 * they do not survive a device restore, so an id-keyed table would eventually skip or duplicate
 * messages. Hashing the content instead means the same message always resolves to the same key
 * no matter which scan found it.
 *
 * Uses only JDK APIs (MessageDigest) so it behaves correctly under unit tests -- see the note in
 * agents.md about `isReturnDefaultValues` silently nulling out android.jar calls.
 */
object SmsBackupKey {
    private const val FIELD_SEPARATOR = "|"

    /**
     * Strips formatting so `+351 912 345 678` and `+351912345678` resolve to the same address.
     * Alphanumeric shortcodes (bank senders, "VODAFONE") are lowercased and otherwise kept as-is.
     */
    fun normalizeAddress(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""
        val stripped = trimmed.filterNot { it.isWhitespace() || it in "-().[]" }
        val digitsOnly = stripped.removePrefix("+").all { it.isDigit() }
        return if (digitsOnly) stripped else stripped.lowercase()
    }

    fun of(
        address: String?,
        body: String,
        dateEpochMs: Long,
    ): String =
        sha256Hex(
            normalizeAddress(address) + FIELD_SEPARATOR + body + FIELD_SEPARATOR + dateEpochMs,
        )

    fun sha256Hex(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
