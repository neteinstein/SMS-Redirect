package com.neteinstein.smsredirect.domain.mail

/**
 * A fully-resolved message, ready to be turned into MIME by the data layer.
 *
 * Plain Kotlin on purpose: building this is the interesting logic (subject templating, threading
 * headers, timestamps) and it stays unit-testable because no `javax.mail` type appears here.
 */
data class OutgoingMail(
    val from: String,
    val fromDisplayName: String?,
    val to: String,
    val subject: String,
    val body: String,
    /**
     * The original SMS timestamp. Carried through to both the `Date:` header and the IMAP
     * INTERNALDATE, so a backfill of old messages does not land dated "today".
     */
    val dateEpochMs: Long,
    val messageId: String,
    val inReplyTo: String?,
    val references: List<String>,
    /** Machine-readable restore metadata; see [SmsMailHeaders]. Written in iteration order. */
    val extraHeaders: Map<String, String> = emptyMap(),
)
