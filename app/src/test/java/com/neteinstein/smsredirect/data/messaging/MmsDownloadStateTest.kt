package com.neteinstein.smsredirect.data.messaging

import com.neteinstein.smsredirect.domain.model.ConversationMessage
import com.neteinstein.smsredirect.domain.model.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsDownloadStateTest {
    @Test
    fun `only an in-flight download shows as downloading, every other state can be retried`() {
        assertEquals(MessageStatus.DOWNLOADING, MmsDownloadState.toMessageStatus(MmsDownloadState.DOWNLOADING))
        for (state in listOf(MmsDownloadState.UNSTARTED, MmsDownloadState.TRANSIENT_FAILURE, MmsDownloadState.PERMANENT_FAILURE, 0)) {
            assertEquals(MessageStatus.NOT_DOWNLOADED, MmsDownloadState.toMessageStatus(state))
        }
    }

    @Test
    fun `a message awaiting download is incoming, so it sits on the sender's side`() {
        val placeholder = ConversationMessage(1, true, 7, "+351912345678", "", 1_000, MessageStatus.NOT_DOWNLOADED)

        assertTrue(placeholder.isIncoming)
        assertTrue(placeholder.isAwaitingDownload)
    }
}
