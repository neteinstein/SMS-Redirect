package com.neteinstein.smsredirect.domain.model

/** One thread in the device's SMS/MMS store, as the Messages tab lists it. */
data class Conversation(
    val threadId: Long,
    /** Every other participant; more than one for a group conversation. */
    val addresses: List<String>,
    val snippet: String,
    val dateEpochMs: Long,
    val unread: Boolean,
    val hasAttachment: Boolean,
)

/** Where a message is in its lifecycle, from this phone's point of view. */
enum class MessageStatus {
    RECEIVED,

    /** An MMS that was announced but not downloaded yet: tap to download it. */
    NOT_DOWNLOADED,

    /** An announced MMS whose download is in progress. */
    DOWNLOADING,
    SENDING,
    SENT,
    FAILED,
}

/** One SMS or MMS inside a conversation. */
data class ConversationMessage(
    /** Provider row id; unique per [isMms] kind, not across both. */
    val id: Long,
    val isMms: Boolean,
    val threadId: Long,
    /** The sender for a received message, the recipient(s) for a sent one. */
    val address: String,
    val body: String,
    val dateEpochMs: Long,
    val status: MessageStatus,
    val attachments: List<MessageAttachment> = emptyList(),
    val subject: String? = null,
) {
    val isIncoming: Boolean
        get() = status == MessageStatus.RECEIVED || status == MessageStatus.NOT_DOWNLOADED || status == MessageStatus.DOWNLOADING

    /** A picture message still waiting on the carrier's server. */
    val isAwaitingDownload: Boolean
        get() = status == MessageStatus.NOT_DOWNLOADED || status == MessageStatus.DOWNLOADING

    /** Stable across the two provider tables, for list keys. */
    val key: String get() = if (isMms) "mms-$id" else "sms-$id"
}

/** A stored MMS part the UI can display or open, addressed by its content URI. */
data class MessageAttachment(
    val uri: String,
    val contentType: String,
    val name: String?,
) {
    val isImage: Boolean get() = contentType.startsWith("image/")
    val isVideo: Boolean get() = contentType.startsWith("video/")
    val isAudio: Boolean get() = contentType.startsWith("audio/")
}

/** Something the user picked to send, not yet in the store. */
data class OutgoingAttachment(
    val uri: String,
    val contentType: String,
    val name: String? = null,
)
