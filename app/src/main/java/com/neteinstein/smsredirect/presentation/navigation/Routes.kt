package com.neteinstein.smsredirect.presentation.navigation

import android.net.Uri

object Routes {
    const val Splash = "splash"
    const val Home = "home"
    const val Settings = "settings"
    const val History = "history"
    const val Rule = "rule"
    const val RuleIdArg = "ruleId"
    const val RuleWithArg = "$Rule?$RuleIdArg={$RuleIdArg}"
    const val ScheduleHistory = "schedule_history"
    const val Schedule = "schedule"
    const val ScheduleIdArg = "scheduleId"
    const val ScheduleWithArg = "$Schedule?$ScheduleIdArg={$ScheduleIdArg}"
    const val NewMessage = "new_message"
    const val Conversation = "conversation"
    const val ThreadIdArg = "threadId"
    const val AddressesArg = "addresses"
    const val DraftArg = "draft"
    const val AttachmentArg = "attachment"
    const val AttachmentTypeArg = "attachmentType"
    const val NewMessageWithArgs =
        "$NewMessage?$DraftArg={$DraftArg}&$AttachmentArg={$AttachmentArg}&$AttachmentTypeArg={$AttachmentTypeArg}"
    const val ConversationWithArgs =
        "$Conversation?$ThreadIdArg={$ThreadIdArg}&$AddressesArg={$AddressesArg}&$DraftArg={$DraftArg}" +
            "&$AttachmentArg={$AttachmentArg}&$AttachmentTypeArg={$AttachmentTypeArg}"

    const val DEEP_LINK_URI = "smsredirect://app"

    fun ruleRoute(ruleId: Long? = null): String = if (ruleId == null) Rule else "$Rule?$RuleIdArg=$ruleId"

    fun scheduleRoute(scheduleId: Long? = null): String = if (scheduleId == null) Schedule else "$Schedule?$ScheduleIdArg=$scheduleId"

    fun conversationRoute(threadId: Long): String = "$Conversation?$ThreadIdArg=$threadId"

    /** A conversation identified by its recipients, optionally pre-filled (sms: links, Share). */
    fun conversationRoute(
        addresses: List<String>,
        draft: String? = null,
        attachment: String? = null,
        attachmentType: String? = null,
    ): String =
        buildString {
            append("$Conversation?$ThreadIdArg=0&$AddressesArg=")
            append(Uri.encode(addresses.joinToString(ADDRESS_SEPARATOR)))
            draft?.let { append("&$DraftArg=").append(Uri.encode(it)) }
            attachment?.let { append("&$AttachmentArg=").append(Uri.encode(it)) }
            attachmentType?.let { append("&$AttachmentTypeArg=").append(Uri.encode(it)) }
        }

    /** New message, optionally carrying text or an attachment shared from another app. */
    fun newMessageRoute(
        draft: String? = null,
        attachment: String? = null,
        attachmentType: String? = null,
    ): String =
        buildString {
            append("$NewMessage?")
            draft?.let { append("$DraftArg=").append(Uri.encode(it)).append('&') }
            attachment?.let { append("$AttachmentArg=").append(Uri.encode(it)).append('&') }
            attachmentType?.let { append("$AttachmentTypeArg=").append(Uri.encode(it)) }
        }.trimEnd('&', '?')

    /** Never part of a phone number or an email address. */
    const val ADDRESS_SEPARATOR = "\u001F"
}
