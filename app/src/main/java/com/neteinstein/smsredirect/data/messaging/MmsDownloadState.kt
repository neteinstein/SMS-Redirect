package com.neteinstein.smsredirect.data.messaging

import com.neteinstein.smsredirect.domain.model.MessageStatus

/**
 * The download state of an announced-but-not-downloaded MMS, kept in its provider row's `st`
 * column. Same values the AOSP messaging app uses, so rows written by either app read the same.
 */
object MmsDownloadState {
    const val UNSTARTED = 0x80
    const val DOWNLOADING = 0x81
    const val TRANSIENT_FAILURE = 0x82
    const val PERMANENT_FAILURE = 0x87

    /** What a conversation shows for a notification row in this state. */
    fun toMessageStatus(state: Int): MessageStatus = if (state == DOWNLOADING) MessageStatus.DOWNLOADING else MessageStatus.NOT_DOWNLOADED
}
