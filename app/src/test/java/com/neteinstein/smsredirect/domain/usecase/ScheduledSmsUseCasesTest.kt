package com.neteinstein.smsredirect.domain.usecase

import app.cash.turbine.test
import com.neteinstein.smsredirect.FakeScheduledSmsRepository
import com.neteinstein.smsredirect.FakeScheduledSmsScheduler
import com.neteinstein.smsredirect.domain.model.RepeatType
import com.neteinstein.smsredirect.domain.model.ScheduledSms
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledSmsUseCasesTest {
    // --- AddScheduledSmsUseCase ---

    @Test
    fun `add fails validation when contact is blank`() =
        runTest {
            val repository = FakeScheduledSmsRepository()
            val scheduler = FakeScheduledSmsScheduler()
            val useCase = AddScheduledSmsUseCase(repository, scheduler)

            val result = useCase(ScheduledSms(contactNumber = "", message = "hi", dateEpochMs = 1000))

            assertTrue(result.isFailure)
            assertTrue(repository.getScheduledSmsSnapshot().isEmpty())
            assertTrue(scheduler.scheduleInitialCalls.isEmpty())
        }

    @Test
    fun `add fails validation when message is blank`() =
        runTest {
            val repository = FakeScheduledSmsRepository()
            val scheduler = FakeScheduledSmsScheduler()
            val useCase = AddScheduledSmsUseCase(repository, scheduler)

            val result = useCase(ScheduledSms(contactNumber = "555", message = "", dateEpochMs = 1000))

            assertTrue(result.isFailure)
        }

    @Test
    fun `add fails validation when date is not set`() =
        runTest {
            val repository = FakeScheduledSmsRepository()
            val scheduler = FakeScheduledSmsScheduler()
            val useCase = AddScheduledSmsUseCase(repository, scheduler)

            val result = useCase(ScheduledSms(contactNumber = "555", message = "hi", dateEpochMs = 0))

            assertTrue(result.isFailure)
        }

    @Test
    fun `add persists valid scheduled sms and schedules it with the generated id`() =
        runTest {
            val repository = FakeScheduledSmsRepository()
            val scheduler = FakeScheduledSmsScheduler()
            val useCase = AddScheduledSmsUseCase(repository, scheduler)

            val result = useCase(ScheduledSms(name = "Rent", contactNumber = "555", message = "hi", dateEpochMs = 1000))

            assertTrue(result.isSuccess)
            assertEquals(1, repository.getScheduledSmsSnapshot().size)
            assertEquals(1, scheduler.scheduleInitialCalls.size)
            // The scheduler must receive the repository-generated id, not the placeholder 0.
            assertEquals(repository.getScheduledSmsSnapshot().first().id, scheduler.scheduleInitialCalls.first().id)
        }

    // --- UpdateScheduledSmsUseCase ---

    @Test
    fun `update fails validation and does not touch the repository or scheduler`() =
        runTest {
            val existing = ScheduledSms(id = 1, contactNumber = "555", message = "hi", dateEpochMs = 1000)
            val repository = FakeScheduledSmsRepository(listOf(existing))
            val scheduler = FakeScheduledSmsScheduler()
            val useCase = UpdateScheduledSmsUseCase(repository, scheduler)

            val result = useCase(existing.copy(message = ""))

            assertTrue(result.isFailure)
            assertTrue(scheduler.cancelCalls.isEmpty())
            assertTrue(scheduler.scheduleInitialCalls.isEmpty())
        }

    @Test
    fun `update cancels the previous schedule and reschedules fresh`() =
        runTest {
            val existing = ScheduledSms(id = 1, contactNumber = "555", message = "hi", dateEpochMs = 1000)
            val repository = FakeScheduledSmsRepository(listOf(existing))
            val scheduler = FakeScheduledSmsScheduler()
            val useCase = UpdateScheduledSmsUseCase(repository, scheduler)

            val updated = existing.copy(message = "updated", repeatType = RepeatType.DAILY)
            val result = useCase(updated)

            assertTrue(result.isSuccess)
            assertEquals("updated", repository.getScheduledSms(1)?.message)
            assertEquals(listOf(1L), scheduler.cancelCalls)
            assertEquals(listOf(updated), scheduler.scheduleInitialCalls)
        }

    // --- DeleteScheduledSmsUseCase ---

    @Test
    fun `delete removes the scheduled sms and cancels its scheduler work`() =
        runTest {
            val existing = ScheduledSms(id = 1, contactNumber = "555", message = "hi", dateEpochMs = 1000)
            val repository = FakeScheduledSmsRepository(listOf(existing))
            val scheduler = FakeScheduledSmsScheduler()
            val useCase = DeleteScheduledSmsUseCase(repository, scheduler)

            useCase(existing)

            assertTrue(repository.getScheduledSmsSnapshot().isEmpty())
            assertEquals(listOf(1L), scheduler.cancelCalls)
        }

    // --- ReorderScheduledSmsUseCase ---

    @Test
    fun `reorder assigns sort order matching the given list order`() =
        runTest {
            val first = ScheduledSms(id = 1, contactNumber = "555", message = "a", dateEpochMs = 1000, sortOrder = 0)
            val second = ScheduledSms(id = 2, contactNumber = "555", message = "b", dateEpochMs = 1000, sortOrder = 1)
            val repository = FakeScheduledSmsRepository(listOf(first, second))
            val useCase = ReorderScheduledSmsUseCase(repository)

            // Reversed order: second should now be sortOrder 0, first sortOrder 1.
            useCase(listOf(second, first))

            val snapshot = repository.getScheduledSmsSnapshot()
            assertEquals(0, snapshot.first { it.id == 2L }.sortOrder)
            assertEquals(1, snapshot.first { it.id == 1L }.sortOrder)
        }

    // --- GetScheduledSmsUseCase / GetScheduledSmsListUseCase / GetScheduledSmsCategoriesUseCase ---

    @Test
    fun `getScheduledSms returns matching entry by id`() =
        runTest {
            val repository =
                FakeScheduledSmsRepository(
                    listOf(ScheduledSms(id = 1, name = "Rent", contactNumber = "555", message = "hi", dateEpochMs = 1000)),
                )
            val useCase = GetScheduledSmsUseCase(repository)

            assertEquals("Rent", useCase(1)?.name)
        }

    @Test
    fun `getScheduledSms returns null when not found`() =
        runTest {
            val repository = FakeScheduledSmsRepository()
            val useCase = GetScheduledSmsUseCase(repository)

            assertNull(useCase(99))
        }

    @Test
    fun `getScheduledSmsList emits updated list when scheduled sms change`() =
        runTest {
            val repository = FakeScheduledSmsRepository()
            val useCase = GetScheduledSmsListUseCase(repository)

            useCase().test {
                assertEquals(0, awaitItem().size)
                repository.insert(ScheduledSms(contactNumber = "555", message = "hi", dateEpochMs = 1000))
                assertEquals(1, awaitItem().size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getScheduledSmsCategories returns distinct sorted categories`() =
        runTest {
            val repository =
                FakeScheduledSmsRepository(
                    listOf(
                        ScheduledSms(id = 1, category = "Bills", contactNumber = "1", message = "a", dateEpochMs = 1000),
                        ScheduledSms(id = 2, category = "Personal", contactNumber = "2", message = "b", dateEpochMs = 1000),
                        ScheduledSms(id = 3, category = "Bills", contactNumber = "3", message = "c", dateEpochMs = 1000),
                    ),
                )
            val useCase = GetScheduledSmsCategoriesUseCase(repository)

            useCase().test {
                assertEquals(listOf("Bills", "Personal"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
