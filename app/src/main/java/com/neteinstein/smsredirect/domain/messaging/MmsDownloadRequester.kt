package com.neteinstein.smsredirect.domain.messaging

/**
 * Starts the download of an MMS that was announced but not fetched yet (a "not downloaded"
 * message in a conversation). Implemented over `SmsManager.downloadMultimediaMessage` in `data/`;
 * the result arrives later and turns the placeholder into the real message, or back into
 * "not downloaded".
 */
fun interface MmsDownloadRequester {
    /** Returns false when the download could not even start (e.g. the row no longer exists). */
    suspend fun download(messageId: Long): Boolean
}
