package com.neteinstein.smsredirect.domain.usecase

import app.cash.turbine.test
import com.neteinstein.smsredirect.FakeScheduledSmsHistoryRepository
import com.neteinstein.smsredirect.FakeSettingsRepository
import com.neteinstein.smsredirect.domain.model.ScheduledSmsHistory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledSmsHistoryUseCasesTest {
    // --- RecordScheduledSmsHistoryUseCase / GetScheduledSmsHistoryUseCase ---

    @Test
    fun `record inserts an entry with the given fields`() =
        runTest {
            val repository = FakeScheduledSmsHistoryRepository()
            val useCase = RecordScheduledSmsHistoryUseCase(repository)

            useCase(scheduledSmsId = 1, scheduledSmsName = "Rent", contactNumber = "555", messageBody = "Pay rent")

            val entry = repository.getAll().single()
            assertEquals(1L, entry.scheduledSmsId)
            assertEquals("Rent", entry.scheduledSmsName)
            assertEquals("555", entry.contactNumber)
            assertEquals("Pay rent", entry.messageBody)
            assertTrue(entry.sentAtEpochMs > 0)
        }

    @Test
    fun `record trims history to the latest fifty entries`() =
        runTest {
            val repository = FakeScheduledSmsHistoryRepository()
            val useCase = RecordScheduledSmsHistoryUseCase(repository)

            repeat(55) { i ->
                repository.insert(
                    ScheduledSmsHistory(scheduledSmsId = 1, contactNumber = "555", messageBody = "msg $i", sentAtEpochMs = i.toLong()),
                )
            }
            useCase(scheduledSmsId = 1, scheduledSmsName = "Rent", contactNumber = "555", messageBody = "final")

            assertEquals(50, repository.getAll().size)
        }

    @Test
    fun `getScheduledSmsHistory returns the most recent entries first`() =
        runTest {
            val repository = FakeScheduledSmsHistoryRepository()
            repository.insert(ScheduledSmsHistory(scheduledSmsId = 1, contactNumber = "555", messageBody = "old", sentAtEpochMs = 1000))
            repository.insert(ScheduledSmsHistory(scheduledSmsId = 1, contactNumber = "555", messageBody = "new", sentAtEpochMs = 2000))
            val useCase = GetScheduledSmsHistoryUseCase(repository)

            useCase().test {
                val items = awaitItem()
                assertEquals("new", items.first().messageBody)
                assertEquals("old", items[1].messageBody)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- DeleteAllScheduledSmsHistoryUseCase ---

    @Test
    fun `deleteAll clears every history entry`() =
        runTest {
            val repository = FakeScheduledSmsHistoryRepository()
            repository.insert(ScheduledSmsHistory(scheduledSmsId = 1, contactNumber = "555", messageBody = "a", sentAtEpochMs = 1000))
            val useCase = DeleteAllScheduledSmsHistoryUseCase(repository)

            useCase()

            assertTrue(repository.getAll().isEmpty())
        }

    // --- KeepScheduleHistory settings use cases ---

    @Test
    fun `setKeepScheduleHistory persists enabled state`() =
        runTest {
            val repository = FakeSettingsRepository()
            val useCase = SetKeepScheduleHistoryUseCase(repository)

            useCase(false)
            useCase(true)

            assertTrue(repository.getKeepScheduleHistory())
        }

    @Test
    fun `setKeepScheduleHistory persists disabled state`() =
        runTest {
            val repository = FakeSettingsRepository()
            val useCase = SetKeepScheduleHistoryUseCase(repository)

            useCase(false)

            assertFalse(repository.getKeepScheduleHistory())
        }

    @Test
    fun `getKeepScheduleHistory observes state changes`() =
        runTest {
            val repository = FakeSettingsRepository()
            val useCase = GetKeepScheduleHistoryUseCase(repository)

            useCase().test {
                assertTrue(awaitItem()) // default is true
                repository.setKeepScheduleHistory(false)
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getKeepScheduleHistoryNow returns current value`() =
        runTest {
            val repository = FakeSettingsRepository()
            val useCase = GetKeepScheduleHistoryNowUseCase(repository)

            assertTrue(useCase())
            repository.setKeepScheduleHistory(false)
            assertFalse(useCase())
        }
}
