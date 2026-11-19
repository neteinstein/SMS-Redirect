package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.contacts.ContactNameResolver
import com.neteinstein.smsredirect.domain.messaging.MessagingAddresses
import com.neteinstein.smsredirect.domain.messaging.SpamAssessment
import com.neteinstein.smsredirect.domain.messaging.SpamFraudDetector
import com.neteinstein.smsredirect.domain.model.ConversationMessage
import com.neteinstein.smsredirect.domain.repository.TrustedSenderRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Which received messages of a conversation look like spam or fraud, and from whom. */
data class ConversationRisk(
    /** [ConversationMessage.key]s of the flagged messages. */
    val flaggedMessageKeys: Set<String>,
    /** The senders of those messages, in order of appearance, one entry per person. */
    val flaggedSenders: List<String>,
    /**
     * Every message this result covers. A received message that is not in it arrived after the
     * check ran: its links stay closed until the next result includes it.
     */
    val assessedMessageKeys: Set<String> = emptySet(),
) {
    val isFlagged: Boolean get() = flaggedMessageKeys.isNotEmpty()

    companion object {
        val NONE = ConversationRisk(emptySet(), emptyList())
    }
}

private fun Set<String>.containsAddress(address: String): Boolean = any { MessagingAddresses.same(it, address) }

/** A check that fails (it never should: see `MessageLinks`) errs on the side of a warning. */
private fun SpamFraudDetector.assessSafely(text: String): SpamAssessment =
    runCatching { assess(text) }.getOrElse { SpamAssessment(emptySet(), suspicious = true) }

/**
 * The body on its own (its links are the tappable ones, and a long subject must not use up the
 * link limit), then with the subject, whose wording may be the scam half of an MMS.
 */
private fun SpamFraudDetector.assessMessage(
    body: String,
    subject: String?,
): SpamAssessment {
    val alone = assessSafely(body)
    if (alone.suspicious || subject.isNullOrBlank()) return alone
    return assessSafely("$subject\n$body")
}

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
        subject: String? = null,
    ): SpamAssessment {
        if (sender.isBlank() || (text.isBlank() && subject.isNullOrBlank())) return SpamAssessment.SAFE
        if (trustedSenderRepository.get().containsAddress(sender)) return SpamAssessment.SAFE
        if (contactNameResolver.lookupNameByNumber(sender) != null) return SpamAssessment.SAFE
        return detector.assessMessage(text, subject)
    }
}

/**
 * The spam/fraud check of a conversation: every received message whose sender is neither a
 * contact nor in [trusted]. Sent messages are never checked. Runs on [dispatcher], off the main
 * thread: a long conversation is a lot of text.
 */
class AssessConversationRiskUseCase(
    private val detector: SpamFraudDetector,
    private val contactNameResolver: ContactNameResolver,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend operator fun invoke(
        messages: List<ConversationMessage>,
        trusted: Set<String>,
    ): ConversationRisk = withContext(dispatcher) { assess(messages, trusted) }

    private suspend fun assess(
        messages: List<ConversationMessage>,
        trusted: Set<String>,
    ): ConversationRisk {
        val isContact = mutableMapOf<String, Boolean>()
        val flagged = mutableListOf<ConversationMessage>()
        for (message in messages) {
            if (!message.isIncoming || message.address.isBlank()) continue
            if ((message.subject.isNullOrBlank() && message.body.isBlank()) || trusted.containsAddress(message.address)) continue
            val contact = isContact.getOrPut(message.address) { contactNameResolver.lookupNameByNumber(message.address) != null }
            if (!contact && isSuspicious(message)) flagged += message
        }
        return ConversationRisk(
            flaggedMessageKeys = flagged.map { it.key }.toSet(),
            // One entry per person: `ann.lee@x.com` and `annlee@x.com` are two people.
            flaggedSenders =
                flagged.map { it.address }.fold(mutableListOf()) { senders, address ->
                    if (senders.none { MessagingAddresses.same(it, address) }) senders += address
                    senders
                },
            assessedMessageKeys = messages.map { it.key }.toSet(),
        )
    }

    private fun isSuspicious(message: ConversationMessage): Boolean = detector.assessMessage(message.body, message.subject).suspicious
}

class ObserveTrustedSendersUseCase(
    private val repository: TrustedSenderRepository,
) {
    operator fun invoke(): Flow<Set<String>> = repository.observe()
}

/** The trusted senders among [addresses], e.g. to offer "Stop trusting" in their conversation. */
class GetTrustedAmongUseCase {
    operator fun invoke(
        addresses: List<String>,
        trusted: Set<String>,
    ): List<String> = addresses.filter { trusted.containsAddress(it) }
}

/** Undoes [TrustSendersUseCase]: these senders' messages are checked, and flagged, again. */
class UntrustSendersUseCase(
    private val repository: TrustedSenderRepository,
) {
    suspend operator fun invoke(addresses: List<String>) {
        val stored = repository.get().filter { entry -> addresses.any { MessagingAddresses.same(entry, it) } }.toSet()
        if (stored.isNotEmpty()) repository.untrust(stored)
    }
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
