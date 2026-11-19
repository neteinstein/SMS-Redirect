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
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduledSmsUseCasesTest {
    /** A wall-clock time in the zone the scheduler computes repeats in. 2025-07-18 is a Friday. */
    private fun local(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int = 0,
    ): Long = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()

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
    fun `update cancels the previous schedule and arms the edited date while it is still ahead`() =
        runTest {
            val existing =
                ScheduledSms(id = 1, contactNumber = "555", message = "hi", dateEpochMs = local(2025, 7, 18, 10))
            val repository = FakeScheduledSmsRepository(listOf(existing))
            val scheduler = FakeScheduledSmsScheduler()
            val useCase = UpdateScheduledSmsUseCase(repository, scheduler, now = { local(2025, 7, 18, 9) })

            val updated = existing.copy(message = "updated", repeatType = RepeatType.DAILY)
            val result = useCase(updated)

            assertTrue(result.isSuccess)
            assertEquals("updated", repository.getScheduledSms(1)?.message)
            assertEquals(listOf(1L), scheduler.cancelCalls)
            assertEquals(listOf(updated), scheduler.scheduleInitialCalls)
            assertTrue(scheduler.scheduleNextCalls.isEmpty())
        }

    @Test
    fun `editing a repeating schedule after its first send arms the next occurrence instead of sending again`() =
        runTest {
            // Found in end-to-end testing: a Daily schedule sent at Friday 10:00 and was then edited to
            // Work week days. Re-arming its Friday 10:00 date sent the SMS a second time at once.
            val firstSend = local(2025, 7, 18, 10)
            val existing =
                ScheduledSms(
                    id = 1,
                    contactNumber = "555",
                    message = "Daily text",
                    dateEpochMs = firstSend,
                    repeatType = RepeatType.DAILY,
                )
            val repository = FakeScheduledSmsRepository(listOf(existing))
            val scheduler = FakeScheduledSmsScheduler()
            val useCase = UpdateScheduledSmsUseCase(repository, scheduler, now = { firstSend + 60_000 })

            val result = useCase(existing.copy(repeatType = RepeatType.WORK_WEEK_DAYS))

            assertTrue(result.isSuccess)
            assertEquals(listOf(1L), scheduler.cancelCalls)
            assertTrue(scheduler.scheduleInitialCalls.isEmpty())
            assertEquals(listOf(1L to local(2025, 7, 21, 10)), scheduler.scheduleNextCalls)
        }

    @Test
    fun `editing a one-off after it was sent arms nothing`() =
        runTest {
            val sentAt = local(2025, 7, 18, 10)
            val existing = ScheduledSms(id = 1, contactNumber = "555", message = "hi", dateEpochMs = sentAt)
            val repository = FakeScheduledSmsRepository(listOf(existing))
            val scheduler = FakeScheduledSmsScheduler()
            val useCase = UpdateScheduledSmsUseCase(repository, scheduler, now = { sentAt + 3_600_000 })

            val result = useCase(existing.copy(message = "updated"))

            assertTrue(result.isSuccess)
            assertEquals("updated", repository.getScheduledSms(1)?.message)
            assertEquals(listOf(1L), scheduler.cancelCalls)
            assertTrue(scheduler.scheduleInitialCalls.isEmpty())
            assertTrue(scheduler.scheduleNextCalls.isEmpty())
        }

    @Test
    fun `editing a sent one-off to a later date arms the new date`() =
        runTest {
            val sentAt = local(2025, 7, 18, 10)
            val existing = ScheduledSms(id = 1, contactNumber = "555", message = "hi", dateEpochMs = sentAt)
            val repository = FakeScheduledSmsRepository(listOf(existing))
            val scheduler = FakeScheduledSmsScheduler()
            val useCase = UpdateScheduledSmsUseCase(repository, scheduler, now = { sentAt + 3_600_000 })

            val updated = existing.copy(dateEpochMs = local(2025, 7, 19, 10))
            useCase(updated)

            assertEquals(listOf(updated), scheduler.scheduleInitialCalls)
            assertTrue(scheduler.scheduleNextCalls.isEmpty())
        }

    @Test
    fun `editing a repeating schedule that has reached its stop date arms nothing`() =
        runTest {
            val existing =
                ScheduledSms(
                    id = 1,
                    contactNumber = "555",
                    message = "hi",
                    dateEpochMs = local(2025, 7, 14, 10),
                    repeatType = RepeatType.DAILY,
                    stopAtEpochMs = local(2025, 7, 18, 12),
                )
            val repository = FakeScheduledSmsRepository(listOf(existing))
            val scheduler = FakeScheduledSmsScheduler()
            val useCase = UpdateScheduledSmsUseCase(repository, scheduler, now = { local(2025, 7, 18, 11) })

            useCase(existing.copy(message = "updated"))

            assertEquals(listOf(1L), scheduler.cancelCalls)
            assertTrue(scheduler.scheduleInitialCalls.isEmpty())
            assertTrue(scheduler.scheduleNextCalls.isEmpty())
        }

    // --- GetNextScheduledSendUseCase ---

    @Test
    fun `next send is the schedule's date while that is still ahead`() {
        val date = local(2025, 7, 18, 10)
        val useCase = GetNextScheduledSendUseCase(now = { local(2025, 7, 18, 9) })

        val result =
            useCase(
                ScheduledSms(id = 1, contactNumber = "555", message = "hi", dateEpochMs = date, repeatType = RepeatType.DAILY),
            )

        assertEquals(date, result)
    }

    @Test
    fun `next send moves on once a repeating schedule has sent`() {
        val useCase = GetNextScheduledSendUseCase(now = { local(2025, 7, 18, 10, 1) })

        val result =
            useCase(
                ScheduledSms(
                    id = 1,
                    contactNumber = "555",
                    message = "hi",
                    dateEpochMs = local(2025, 7, 18, 10),
                    repeatType = RepeatType.DAILY,
                ),
            )

        assertEquals(local(2025, 7, 19, 10), result)
    }

    @Test
    fun `a one-off that has sent has no next send`() {
        val useCase = GetNextScheduledSendUseCase(now = { local(2025, 7, 18, 10, 1) })

        val result = useCase(ScheduledSms(id = 1, contactNumber = "555", message = "hi", dateEpochMs = local(2025, 7, 18, 10)))

        assertNull(result)
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
