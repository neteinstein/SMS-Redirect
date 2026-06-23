package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.FakeForwardHistoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordForwardHistoryUseCaseTest {
    @Test
    fun `records history with correct fields`() =
        runTest {
            val repository = FakeForwardHistoryRepository()
            val useCase = RecordForwardHistoryUseCase(repository)

            useCase(sender = "BANK", targetNumber = "+351999", messageBody = "Hello", ruleName = "Rule1")

            val entries = repository.getAll()
            assertEquals(1, entries.size)
            val entry = entries[0]
            assertEquals("BANK", entry.sender)
            assertEquals("+351999", entry.targetNumber)
            assertEquals("Hello", entry.messageBody)
            assertEquals("Rule1", entry.ruleName)
            assertTrue(entry.forwardedAtEpochMs > 0)
        }

    @Test
    fun `trims to 50 entries after insert`() =
        runTest {
            val repository = FakeForwardHistoryRepository()
            val useCase = RecordForwardHistoryUseCase(repository)

            // Insert 55 entries
            repeat(55) { i ->
                useCase(sender = "S$i", targetNumber = "999", messageBody = "msg$i", ruleName = "R")
            }

            val entries = repository.getAll()
            assertEquals(50, entries.size)
        }

    @Test
    fun `handles empty sender string`() =
        runTest {
            val repository = FakeForwardHistoryRepository()
            val useCase = RecordForwardHistoryUseCase(repository)

            useCase(sender = "", targetNumber = "999", messageBody = "msg", ruleName = "R")

            val entries = repository.getAll()
            assertEquals(1, entries.size)
            assertEquals("", entries[0].sender)
        }

    @Test
    fun `sets forwardedAtEpochMs close to current time`() =
        runTest {
            val repository = FakeForwardHistoryRepository()
            val useCase = RecordForwardHistoryUseCase(repository)

            val before = System.currentTimeMillis()
            useCase(sender = "X", targetNumber = "999", messageBody = "msg", ruleName = "R")
            val after = System.currentTimeMillis()

            val entry = repository.getAll().first()
            assertTrue(entry.forwardedAtEpochMs in before..after)
        }
}
