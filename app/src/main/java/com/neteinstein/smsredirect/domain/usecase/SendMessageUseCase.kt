package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.messaging.MessageSender
import com.neteinstein.smsredirect.domain.messaging.SendFailure
import com.neteinstein.smsredirect.domain.messaging.SendOutcome
import com.neteinstein.smsredirect.domain.model.OutgoingAttachment
import com.neteinstein.smsredirect.domain.model.SmsBackupKey
import com.neteinstein.smsredirect.domain.sms.DefaultSmsAppChecker

/**
 * Sends what the user typed in a conversation. Chooses the transport the way the stock Messages
 * app does: plain text to one person is an SMS; anything with an attachment, or to more than one
 * person, is an MMS (a group message, so replies stay in one conversation).
 */
class SendMessageUseCase(
    private val messageSender: MessageSender,
    private val defaultSmsAppChecker: DefaultSmsAppChecker,
) {
    suspend operator fun invoke(
        addresses: List<String>,
        body: String,
        attachments: List<OutgoingAttachment> = emptyList(),
    ): SendOutcome {
        // Only the default SMS app may write the message to the store; sending without it would
        // leave a message the user can never see in any conversation.
        if (!defaultSmsAppChecker.isDefaultSmsApp()) return SendOutcome.Failed(SendFailure.NOT_DEFAULT_SMS_APP)
        val recipients = normalizeRecipients(addresses)
        if (recipients.isEmpty()) return SendOutcome.Failed(SendFailure.NO_RECIPIENTS)
        val text = body.trim()
        if (text.isEmpty() && attachments.isEmpty()) return SendOutcome.Failed(SendFailure.EMPTY_MESSAGE)

        return if (attachments.isNotEmpty() || recipients.size > 1) {
            messageSender.sendMms(recipients, text, attachments)
        } else {
            messageSender.sendSms(recipients.single(), text)
        }
    }
}

/** Trims, drops blanks and removes duplicates that differ only in formatting or country code. */
internal fun normalizeRecipients(addresses: List<String>): List<String> {
    val result = mutableListOf<String>()
    for (raw in addresses) {
        val address = raw.trim()
        if (address.isEmpty()) continue
        if (result.any { it == address || SmsBackupKey.sameAddress(it, address) }) continue
        result += address
    }
    return result
}
