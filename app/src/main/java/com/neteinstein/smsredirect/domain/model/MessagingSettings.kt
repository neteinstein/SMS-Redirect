package com.neteinstein.smsredirect.domain.model

/**
 * The user's message-receipt choices. All off by default, as in the stock Messages app: a
 * delivery report can cost a network round trip, and a read report tells the sender when you
 * read their message.
 */
data class MessagingSettings(
    /** Ask the carrier to report when each SMS sent from this phone is delivered. */
    val smsDeliveryReports: Boolean = false,
    /**
     * Ask for MMS delivery reports on MMS sent from this phone, and let the carrier tell senders
     * when their MMS reached this phone (X-Mms-Report-Allowed).
     */
    val mmsDeliveryReports: Boolean = false,
    /**
     * Ask recipients for MMS read reports, and send one back when you read an MMS whose sender
     * asked for it.
     */
    val mmsReadReports: Boolean = false,
)
