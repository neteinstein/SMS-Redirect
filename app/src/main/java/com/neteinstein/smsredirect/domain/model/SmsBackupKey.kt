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

    /**
     * Whether two provider addresses name the same sender, for restore-time duplicate detection.
     *
     * Looser than [normalizeAddress] equality on purpose: the same contact can be stored as
     * `+351912345678` on one device and `912345678` on another, depending on the carrier and the
     * messaging app. Numeric addresses therefore also match on their last [SUFFIX_DIGITS] digits.
     * Short codes are too short for that and must match exactly.
     */
    fun sameAddress(
        a: String?,
        b: String?,
    ): Boolean {
        val left = normalizeAddress(a)
        val right = normalizeAddress(b)
        if (left == right) return left.isNotEmpty()
        val leftDigits = left.removePrefix("+")
        val rightDigits = right.removePrefix("+")
        if (!leftDigits.all { it.isDigit() } || !rightDigits.all { it.isDigit() }) return false
        if (leftDigits.length < SUFFIX_DIGITS || rightDigits.length < SUFFIX_DIGITS) return false
        return leftDigits.takeLast(SUFFIX_DIGITS) == rightDigits.takeLast(SUFFIX_DIGITS)
    }

    private const val SUFFIX_DIGITS = 9

    /**
     * The key for one message. [box] is folded in for sent messages only.
     *
     * An inbox message keeps the original `address|body|date` input, so every key already in
     * `sms_backup_state` stays valid and an app update does not re-upload a user's whole inbox.
     * A sent message gets a distinct prefix so that it can never share a key with a received one:
     * texting your own number puts the same address and body in both boxes, and if the two
     * timestamps ever coincided, one of them would be silently skipped as "already backed up".
     * No sent message was backed up before this prefix existed, so it cannot orphan any record.
     */
    fun of(
        address: String?,
        body: String,
        dateEpochMs: Long,
        box: SmsBox = SmsBox.INBOX,
    ): String {
        val prefix = if (box == SmsBox.INBOX) "" else box.headerValue + FIELD_SEPARATOR
        return sha256Hex(
            prefix + normalizeAddress(address) + FIELD_SEPARATOR + body + FIELD_SEPARATOR + dateEpochMs,
        )
    }

    fun sha256Hex(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
