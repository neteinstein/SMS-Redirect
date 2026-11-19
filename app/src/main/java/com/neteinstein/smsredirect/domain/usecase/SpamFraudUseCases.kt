package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.contacts.ContactNameResolver
import com.neteinstein.smsredirect.domain.messaging.MessagingAddresses
import com.neteinstein.smsredirect.domain.messaging.SpamAssessment
import com.neteinstein.smsredirect.domain.messaging.SpamFraudDetector
import com.neteinstein.smsredirect.domain.model.ConversationMessage
import com.neteinstein.smsredirect.domain.repository.TrustedSenderRepository
import kotlinx.coroutines.flow.Flow

/** Which received messages of a conversation look like spam or fraud, and from whom. */
data class ConversationRisk(
    /** [ConversationMessage.key]s of the flagged messages. */
    val flaggedMessageKeys: Set<String>,
    /** The senders of those messages, in order of appearance. */
    val flaggedSenders: List<String>,
) {
    val isFlagged: Boolean get() = flaggedMessageKeys.isNotEmpty()

    companion object {
        val NONE = ConversationRisk(emptySet(), emptyList())
    }
}

private fun Set<String>.containsAddress(address: String): Boolean = any { MessagingAddresses.same(it, address) }

/**
 * The spam/fraud check of one received message, for its notification. Messages from a contact
 * or a sender the user trusts are never flagged.
 */
class AssessIncomingMessageUseCase(
    private val detector: SpamFraudDetector,
    private val trustedSenderRepository: TrustedSenderRepository,
    private val contactNameResolver: ContactNameResolver,
) {
    suspend operator fun invoke(
        sender: String,
        text: String,
    ): SpamAssessment {
        if (sender.isBlank() || text.isBlank()) return SpamAssessment.SAFE
        if (trustedSenderRepository.get().containsAddress(sender)) return SpamAssessment.SAFE
        if (contactNameResolver.lookupNameByNumber(sender) != null) return SpamAssessment.SAFE
        return detector.assess(text)
    }
}

/**
 * The spam/fraud check of a conversation: every received message whose sender is neither a
 * contact nor in [trusted]. Sent messages are never checked.
 */
class AssessConversationRiskUseCase(
    private val detector: SpamFraudDetector,
    private val contactNameResolver: ContactNameResolver,
) {
    suspend operator fun invoke(
        messages: List<ConversationMessage>,
        trusted: Set<String>,
    ): ConversationRisk {
        val isContact = mutableMapOf<String, Boolean>()
        val flagged = mutableListOf<ConversationMessage>()
        for (message in messages) {
            if (!message.isIncoming || message.address.isBlank()) continue
            val text = listOfNotNull(message.subject, message.body).joinToString("\n")
            if (text.isBlank() || trusted.containsAddress(message.address)) continue
            val contact = isContact.getOrPut(message.address) { contactNameResolver.lookupNameByNumber(message.address) != null }
            if (!contact && detector.assess(text).suspicious) flagged += message
        }
        if (flagged.isEmpty()) return ConversationRisk.NONE
        return ConversationRisk(
            flaggedMessageKeys = flagged.map { it.key }.toSet(),
            flaggedSenders = flagged.map { it.address }.distinctBy { it.filter(Char::isLetterOrDigit).lowercase() },
        )
    }
}

class ObserveTrustedSendersUseCase(
    private val repository: TrustedSenderRepository,
) {
    operator fun invoke(): Flow<Set<String>> = repository.observe()
}

/** The user confirmed they know these senders: stop flagging them and let their links open. */
class TrustSendersUseCase(
    private val repository: TrustedSenderRepository,
) {
    suspend operator fun invoke(addresses: List<String>) {
        val trusted = repository.get()
        addresses
            .filter { it.isNotBlank() && !trusted.containsAddress(it) }
            .forEach { repository.trust(it) }
    }
}
