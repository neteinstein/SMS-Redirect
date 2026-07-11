package com.neteinstein.smsredirect.presentation

import com.neteinstein.smsredirect.FakeContactNameResolver
import com.neteinstein.smsredirect.FakeScheduledSmsHistoryRepository
import com.neteinstein.smsredirect.FakeSettingsRepository
import com.neteinstein.smsredirect.domain.model.ScheduledSmsHistory
import com.neteinstein.smsredirect.domain.usecase.DeleteAllScheduledSmsHistoryUseCase
import com.neteinstein.smsredirect.domain.usecase.GetKeepScheduleHistoryUseCase
import com.neteinstein.smsredirect.domain.usecase.GetScheduledSmsHistoryUseCase
import com.neteinstein.smsredirect.domain.usecase.SetKeepScheduleHistoryUseCase
import com.neteinstein.smsredirect.presentation.schedulehistory.ScheduleHistoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleHistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val historyRepository = FakeScheduledSmsHistoryRepository()
    private val settingsRepository = FakeSettingsRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(contacts: Map<String, String> = emptyMap()): ScheduleHistoryViewModel =
        ScheduleHistoryViewModel(
            getScheduledSmsHistoryUseCase = GetScheduledSmsHistoryUseCase(historyRepository),
            getKeepScheduleHistoryUseCase = GetKeepScheduleHistoryUseCase(settingsRepository),
            setKeepScheduleHistoryUseCase = SetKeepScheduleHistoryUseCase(settingsRepository),
            deleteAllScheduledSmsHistoryUseCase = DeleteAllScheduledSmsHistoryUseCase(historyRepository),
            contactNameResolver = FakeContactNameResolver(contacts),
        )

    @Test
    fun `init observes scheduledSmsHistory`() =
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()
            assertEquals(emptyList<ScheduledSmsHistory>(), vm.scheduledSmsHistory.value)
        }

    @Test
    fun `scheduledSmsHistory emits items from repository`() =
        runTest {
            val item =
                ScheduledSmsHistory(
                    id = 1L,
                    scheduledSmsId = 1L,
                    scheduledSmsName = "Rent",
                    contactNumber = "+351912345678",
                    messageBody = "Pay rent",
                    sentAtEpochMs = 1000L,
                )
            historyRepository.insert(item)

            val vm = createViewModel()
            advanceUntilIdle()

            assertEquals(1, vm.scheduledSmsHistory.value.size)
            assertEquals(
                item.scheduledSmsName,
                vm.scheduledSmsHistory.value
                    .first()
                    .scheduledSmsName,
            )
        }

    @Test
    fun `init observes keepScheduleHistory`() =
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()
            assertTrue(vm.keepScheduleHistory.value)
        }

    @Test
    fun `resolves contact names for history entries`() =
        runTest {
            historyRepository.insert(
                ScheduledSmsHistory(scheduledSmsId = 1, contactNumber = "+351912345678", messageBody = "hi", sentAtEpochMs = 1000),
            )

            val vm = createViewModel(contacts = mapOf("+351912345678" to "Landlord"))
            advanceUntilIdle()

            assertEquals("Landlord", vm.contactNames.value["+351912345678"])
        }

    @Test
    fun `onKeepScheduleHistoryToggled false with history shows confirmation`() =
        runTest {
            val item =
                ScheduledSmsHistory(
                    id = 1L,
                    scheduledSmsId = 1L,
                    contactNumber = "+351912345678",
                    messageBody = "Hello",
                    sentAtEpochMs = 1000L,
                )
            historyRepository.insert(item)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onKeepScheduleHistoryToggled(false)

            assertTrue(vm.showDisableConfirmation.value)
            assertTrue(vm.keepScheduleHistory.value)
        }

    @Test
    fun `onKeepScheduleHistoryToggled false with empty history disables directly`() =
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onKeepScheduleHistoryToggled(false)
            advanceUntilIdle()

            assertFalse(vm.showDisableConfirmation.value)
            assertFalse(vm.keepScheduleHistory.value)
        }

    @Test
    fun `confirmDisableHistory deletes history and disables`() =
        runTest {
            val item =
                ScheduledSmsHistory(
                    id = 1L,
                    scheduledSmsId = 1L,
                    contactNumber = "+351912345678",
                    messageBody = "Hello",
                    sentAtEpochMs = 1000L,
                )
            historyRepository.insert(item)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onKeepScheduleHistoryToggled(false)
            vm.confirmDisableHistory()
            advanceUntilIdle()

            assertFalse(vm.showDisableConfirmation.value)
            assertFalse(vm.keepScheduleHistory.value)
            assertTrue(vm.scheduledSmsHistory.value.isEmpty())
        }

    @Test
    fun `dismissDisableConfirmation hides dialog without changes`() =
        runTest {
            val item =
                ScheduledSmsHistory(
                    id = 1L,
                    scheduledSmsId = 1L,
                    contactNumber = "+351912345678",
                    messageBody = "Hello",
                    sentAtEpochMs = 1000L,
                )
            historyRepository.insert(item)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onKeepScheduleHistoryToggled(false)
            vm.dismissDisableConfirmation()

            assertFalse(vm.showDisableConfirmation.value)
            assertTrue(vm.keepScheduleHistory.value)
            assertEquals(1, vm.scheduledSmsHistory.value.size)
        }
}
