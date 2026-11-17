package com.neteinstein.smsredirect.data.device

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RingCoordinatorTest {
    private var volume = 3
    private val events = mutableListOf<String>()
    private val coordinator = RingCoordinator()

    private fun begin(name: String): Long =
        coordinator.begin(currentVolume = { volume }) {
            volume = MAX
            events += "start $name"
        }

    private fun end(
        token: Long,
        name: String,
    ): Boolean =
        coordinator.end(token) { saved ->
            volume = saved
            events += "restore $name"
        }

    @Test
    fun `a ring that ends restores the user's volume`() {
        val token = begin("a")
        assertEquals(MAX, volume)

        assertTrue(end(token, "a"))

        assertEquals(3, volume)
        assertEquals(listOf("start a", "restore a"), events)
    }

    @Test
    fun `a stopped ring still restores the volume`() {
        val token = begin("a")

        coordinator.stop()

        assertTrue(end(token, "a"))
        assertEquals(3, volume)
    }

    @Test
    fun `an overlapping ring keeps the original volume, not the temporary maximum`() {
        val first = begin("a")
        val second = begin("b")

        // The superseded ring leaves the volume and the notification to the newer one.
        assertFalse(end(first, "a"))
        assertEquals(MAX, volume)

        assertTrue(end(second, "b"))
        assertEquals(3, volume)
        assertEquals(listOf("start a", "start b", "restore b"), events)
    }

    @Test
    fun `a ring started after another was stopped but before it cleaned up keeps the original volume`() {
        val first = begin("a")
        coordinator.stop()
        val second = begin("b")

        assertFalse(end(first, "a"))
        assertTrue(end(second, "b"))

        assertEquals(3, volume)
    }

    @Test
    fun `awaitEnd returns when stopped or superseded`() =
        runTest {
            val first = begin("a")
            val waiting = async { coordinator.awaitEnd(first) }
            yield()
            assertFalse(waiting.isCompleted)

            begin("b")
            waiting.await()

            val second = 2L
            val waitingSecond = async { coordinator.awaitEnd(second) }
            yield()
            coordinator.stop()
            waitingSecond.await()
        }

    private companion object {
        const val MAX = 7
    }
}
