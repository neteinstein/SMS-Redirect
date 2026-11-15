package com.neteinstein.smsredirect.domain.mms

import com.neteinstein.smsredirect.domain.messaging.MessagingAddresses

/** Who a received MMS's conversation is with. */
object MmsRecipients {
    /**
     * A one-to-one MMS lists only this phone in To, so the conversation is just the sender. A
     * group MMS lists everyone, and the conversation is the sender plus every other recipient --
     * minus this phone's own number(s), when known, so a group does not include yourself.
     */
    fun conversationAddresses(
        from: String?,
        to: List<String>,
        cc: List<String>,
        ownNumbers: Collection<String>,
    ): List<String> {
        val others = (to + cc).filter { it.isNotBlank() }
        val isGroup = others.size > 1
        val candidates = listOfNotNull(from?.takeIf { it.isNotBlank() }) + if (isGroup) others else emptyList()
        val result = mutableListOf<String>()
        for (address in candidates) {
            if (ownNumbers.any { own -> MessagingAddresses.same(own, address) }) continue
            if (result.any { MessagingAddresses.same(it, address) }) continue
            result += address
        }
        return result
    }
}
