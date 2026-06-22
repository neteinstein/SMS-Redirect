package com.neteinstein.smsredirect.domain.usecase

import app.cash.turbine.test
import com.neteinstein.smsredirect.FakeForwardHistoryRepository
import com.neteinstein.smsredirect.domain.model.ForwardHistory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetForwardHistoryUseCaseTest {

    @Test
    fun `emits empty list when no history exists`() = runTest {
        val repository = FakeForwardHistoryRepository()
        val useCase = GetForwardHistoryUseCase(repository)

        useCase().test {
            assertEquals(emptyList<ForwardHistory>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits history after insert`() = runTest {
        val repository = FakeForwardHistoryRepository()
        val useCase = GetForwardHistoryUseCase(repository)

        useCase().test {
            assertEquals(0, awaitItem().size)

            repository.insert(
                ForwardHistory(sender = "BANK", targetNumber = "999", messageBody = "Hello", ruleName = "Rule1", forwardedAtEpochMs = 1000L)
            )
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("BANK", items[0].sender)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `respects limit parameter`() = runTest {
        val repository = FakeForwardHistoryRepository()
        val useCase = GetForwardHistoryUseCase(repository)

        // Insert 5 entries
        repeat(5) { i ->
            repository.insert(
                ForwardHistory(sender = "S$i", targetNumber = "999", messageBody = "msg", ruleName = "R", forwardedAtEpochMs = (i + 1).toLong())
            )
        }

        useCase(limit = 3).test {
            val items = awaitItem()
            assertEquals(3, items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `default limit is 50`() = runTest {
        val repository = FakeForwardHistoryRepository()
        val useCase = GetForwardHistoryUseCase(repository)

        // Insert 55 entries
        repeat(55) { i ->
            repository.insert(
                ForwardHistory(sender = "S$i", targetNumber = "999", messageBody = "msg", ruleName = "R", forwardedAtEpochMs = (i + 1).toLong())
            )
        }

        useCase().test {
            val items = awaitItem()
            assertEquals(50, items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `returns entries ordered by most recent first`() = runTest {
        val repository = FakeForwardHistoryRepository()
        val useCase = GetForwardHistoryUseCase(repository)

        repository.insert(ForwardHistory(sender = "OLD", targetNumber = "999", messageBody = "msg", ruleName = "R", forwardedAtEpochMs = 100L))
        repository.insert(ForwardHistory(sender = "NEW", targetNumber = "999", messageBody = "msg", ruleName = "R", forwardedAtEpochMs = 200L))

        useCase().test {
            val items = awaitItem()
            assertEquals("NEW", items[0].sender)
            assertEquals("OLD", items[1].sender)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
