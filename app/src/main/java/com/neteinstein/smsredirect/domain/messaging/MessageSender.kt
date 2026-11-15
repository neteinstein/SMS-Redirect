package com.neteinstein.smsredirect.domain.messaging

import com.neteinstein.smsredirect.domain.model.OutgoingAttachment

/**
 * Sends a message and records it in the store as "sending"; the result arrives later through the
 * platform's sent broadcast and updates that row. Implemented over `SmsManager` in `data/`.
 */
interface MessageSender {
    /** One SMS to one recipient, split into parts as needed. */
    suspend fun sendSms(
        address: String,
        body: String,
    ): SendOutcome

    /** One MMS to every recipient: a picture message, or a group message. */
    suspend fun sendMms(
        addresses: List<String>,
        body: String,
        attachments: List<OutgoingAttachment>,
    ): SendOutcome
}

sealed interface SendOutcome {
    /** Handed to the radio. [threadId] is the conversation the message now belongs to. */
    data class Queued(
        val threadId: Long,
    ) : SendOutcome

    /** Never left the phone. Nothing was charged. */
    data class Failed(
        val reason: SendFailure,
    ) : SendOutcome
}

enum class SendFailure {
    NOT_DEFAULT_SMS_APP,
    NO_RECIPIENTS,
    EMPTY_MESSAGE,
    ATTACHMENT_TOO_LARGE,
    ATTACHMENT_UNREADABLE,
    NO_SERVICE,
}
