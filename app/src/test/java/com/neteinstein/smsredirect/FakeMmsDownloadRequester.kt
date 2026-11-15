package com.neteinstein.smsredirect

import com.neteinstein.smsredirect.domain.messaging.MmsDownloadRequester

class FakeMmsDownloadRequester(
    var starts: Boolean = true,
) : MmsDownloadRequester {
    val requested = mutableListOf<Long>()

    override suspend fun download(messageId: Long): Boolean {
        requested += messageId
        return starts
    }
}
