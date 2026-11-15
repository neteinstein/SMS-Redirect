package com.neteinstein.smsredirect

import com.neteinstein.smsredirect.domain.messaging.MessageSender
import com.neteinstein.smsredirect.domain.messaging.SendOutcome
import com.neteinstein.smsredirect.domain.model.OutgoingAttachment

class FakeMessageSender(
    var outcome: SendOutcome = SendOutcome.Queued(threadId = 1L),
) : MessageSender {
    data class Sms(
        val address: String,
        val body: String,
    )

    data class Mms(
        val addresses: List<String>,
        val body: String,
        val attachments: List<OutgoingAttachment>,
    )

    val sentSms = mutableListOf<Sms>()
    val sentMms = mutableListOf<Mms>()

    override suspend fun sendSms(
        address: String,
        body: String,
    ): SendOutcome {
        sentSms += Sms(address, body)
        return outcome
    }

    override suspend fun sendMms(
        addresses: List<String>,
        body: String,
        attachments: List<OutgoingAttachment>,
    ): SendOutcome {
        sentMms += Mms(addresses, body, attachments)
        return outcome
    }
}
