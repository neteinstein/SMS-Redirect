package com.neteinstein.smsredirect

import com.neteinstein.smsredirect.RcsNotificationListener.Companion.selectNewMessages
import com.neteinstein.smsredirect.RcsNotificationListener.TimestampedMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RcsNotificationListenerTest {
    @Test
    fun `all messages are new when nothing was seen before`() {
        val messages =
            listOf(
                TimestampedMessage(timestamp = 100L, sender = "Alice", body = "hi"),
                TimestampedMessage(timestamp = 200L, sender = "Alice", body = "there"),
            )

        val result = selectNewMessages(messages, lastSeenTimestamp = 0L)

        assertEquals(messages, result)
    }

    @Test
    fun `only messages newer than lastSeenTimestamp are returned`() {
        val older = TimestampedMessage(timestamp = 100L, sender = "Alice", body = "old")
        val newer = TimestampedMessage(timestamp = 200L, sender = "Alice", body = "new")

        val result = selectNewMessages(listOf(older, newer), lastSeenTimestamp = 100L)

        assertEquals(listOf(newer), result)
    }

    @Test
    fun `burst of multiple new messages are all returned oldest first`() {
        // Simulates two RCS messages arriving before a single onNotificationPosted callback —
        // the style should never drop the earlier message by only looking at the last one.
        val first = TimestampedMessage(timestamp = 200L, sender = "Bob", body = "first")
        val second = TimestampedMessage(timestamp = 300L, sender = "Bob", body = "second")

        // Passed out of order on purpose to make sure sorting isn't accidentally relying on input order.
        val result = selectNewMessages(listOf(second, first), lastSeenTimestamp = 100L)

        assertEquals(listOf(first, second), result)
    }

    @Test
    fun `no new messages when the update didn't add anything, eg a read receipt refresh`() {
        val message = TimestampedMessage(timestamp = 100L, sender = "Alice", body = "hi")

        val result = selectNewMessages(listOf(message), lastSeenTimestamp = 100L)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `empty message list returns empty result`() {
        val result = selectNewMessages(emptyList(), lastSeenTimestamp = 0L)

        assertTrue(result.isEmpty())
    }
}
