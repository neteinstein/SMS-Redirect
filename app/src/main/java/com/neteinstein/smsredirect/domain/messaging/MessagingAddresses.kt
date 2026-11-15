package com.neteinstein.smsredirect.domain.messaging

/**
 * Whether two recipient addresses are the same person, for sending and for grouping MMS
 * participants. Deliberately stricter than the restore-time `SmsBackupKey.sameAddress`, which
 * matches on the last nine digits and so would merge `+12025550123` with `+442025550123`:
 *
 * - two numbers that both carry a country code (`+` or `00`) must match exactly;
 * - a number without a country code matches one with it when it is its suffix (`912345678` and
 *   `+351912345678`), and is at least [MIN_LOCAL_DIGITS] long;
 * - formatting (spaces, dashes, parentheses) never matters;
 * - email addresses and alphanumeric senders compare case-insensitively, punctuation included.
 */
object MessagingAddresses {
    private const val MIN_LOCAL_DIGITS = 7

    fun isEmail(address: String): Boolean = '@' in address

    fun same(
        a: String,
        b: String,
    ): Boolean {
        val left = a.trim()
        val right = b.trim()
        if (left.isEmpty() || right.isEmpty()) return false
        if (isEmail(left) || isEmail(right)) return left.equals(right, ignoreCase = true)

        val leftNumber = PhoneNumber.parse(left)
        val rightNumber = PhoneNumber.parse(right)
        if (leftNumber == null || rightNumber == null) return left.equals(right, ignoreCase = true)
        if (leftNumber.digits == rightNumber.digits) return true
        if (leftNumber.international && rightNumber.international) return false
        val (shorter, longer) =
            if (leftNumber.digits.length <= rightNumber.digits.length) leftNumber to rightNumber else rightNumber to leftNumber
        // A local number is a suffix of the international one; strip a national trunk "0".
        val local = if (shorter.international) return false else shorter.digits.trimStart('0')
        return local.length >= MIN_LOCAL_DIGITS && longer.digits.endsWith(local)
    }

    private data class PhoneNumber(
        val digits: String,
        val international: Boolean,
    ) {
        companion object {
            /** Null for anything that is not a dialable number, e.g. an alphanumeric sender. */
            fun parse(raw: String): PhoneNumber? {
                if (!raw.all { it.isDigit() || it in "+-(). " }) return null
                val digits = raw.filter { it.isDigit() }
                if (digits.isEmpty()) return null
                return when {
                    raw.startsWith("+") -> PhoneNumber(digits, international = true)
                    raw.startsWith("00") -> PhoneNumber(digits.drop(2), international = true)
                    else -> PhoneNumber(digits, international = false)
                }
            }
        }
    }
}
