package com.neteinstein.smsredirect.presentation

import com.neteinstein.smsredirect.FakeContactNameResolver
import com.neteinstein.smsredirect.FakeScheduledSmsRepository
import com.neteinstein.smsredirect.FakeScheduledSmsScheduler
import com.neteinstein.smsredirect.FakeSettingsRepository
import com.neteinstein.smsredirect.domain.model.RepeatType
import com.neteinstein.smsredirect.domain.model.ScheduledSms
import com.neteinstein.smsredirect.domain.usecase.AddScheduledSmsUseCase
import com.neteinstein.smsredirect.domain.usecase.GetDefaultPrefixNowUseCase
import com.neteinstein.smsredirect.domain.usecase.GetScheduledSmsCategoriesUseCase
import com.neteinstein.smsredirect.domain.usecase.GetScheduledSmsUseCase
import com.neteinstein.smsredirect.domain.usecase.UpdateScheduledSmsUseCase
import com.neteinstein.smsredirect.presentation.schedule.ScheduleFormViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleFormViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        repository: FakeScheduledSmsRepository = FakeScheduledSmsRepository(),
        scheduler: FakeScheduledSmsScheduler = FakeScheduledSmsScheduler(),
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository(),
        contacts: Map<String, String> = emptyMap(),
    ): ScheduleFormViewModel =
        ScheduleFormViewModel(
            getScheduledSmsUseCase = GetScheduledSmsUseCase(repository),
            addScheduledSmsUseCase = AddScheduledSmsUseCase(repository, scheduler),
            updateScheduledSmsUseCase = UpdateScheduledSmsUseCase(repository, scheduler),
            getScheduledSmsCategoriesUseCase = GetScheduledSmsCategoriesUseCase(repository),
            scheduledSmsRepository = repository,
            getDefaultPrefixNowUseCase = GetDefaultPrefixNowUseCase(settingsRepository),
            contactNameResolver = FakeContactNameResolver(contacts),
        )

    @Test
    fun `save fails when contact is not a valid phone number`() =
        runTest {
            val vm = createViewModel()

            vm.onContactChanged("BANK")
            vm.onMessageChanged("hi")
            vm.onDateSelected(1000)
            vm.saveScheduledSms()
            advanceUntilIdle()

            assertEquals("Contact must be a valid phone number", vm.uiState.value.error)
            assertTrue(!vm.uiState.value.saved)
        }

    @Test
    fun `save fails when message is blank`() =
        runTest {
            val vm = createViewModel()

            vm.onContactChanged("912345678")
            vm.onDateSelected(1000)
            vm.saveScheduledSms()
            advanceUntilIdle()

            assertEquals("Message is required", vm.uiState.value.error)
        }

    @Test
    fun `save fails when date is not selected`() =
        runTest {
            val vm = createViewModel()

            vm.onContactChanged("912345678")
            vm.onMessageChanged("hi")
            vm.saveScheduledSms()
            advanceUntilIdle()

            assertEquals("Date is required", vm.uiState.value.error)
        }

    @Test
    fun `save succeeds and generates a default name when name is blank`() =
        runTest {
            val repository = FakeScheduledSmsRepository()
            val vm = createViewModel(repository = repository)

            vm.onContactChanged("912345678")
            vm.onMessageChanged("hi")
            vm.onDateSelected(1000)
            vm.saveScheduledSms()
            advanceUntilIdle()

            assertTrue(vm.uiState.value.saved)
            assertEquals("Scheduled SMS #1", repository.getScheduledSmsSnapshot().first().name)
        }

    @Test
    fun `save preserves a manually entered name`() =
        runTest {
            val repository = FakeScheduledSmsRepository()
            val vm = createViewModel(repository = repository)

            vm.onNameChanged("Rent Reminder")
            vm.onContactChanged("912345678")
            vm.onMessageChanged("hi")
            vm.onDateSelected(1000)
            vm.saveScheduledSms()
            advanceUntilIdle()

            assertEquals("Rent Reminder", repository.getScheduledSmsSnapshot().first().name)
        }

    @Test
    fun `save normalizes the contact number using the default prefix`() =
        runTest {
            val repository = FakeScheduledSmsRepository()
            val settingsRepository = FakeSettingsRepository()
            settingsRepository.setDefaultPrefix("+351")
            val vm = createViewModel(repository = repository, settingsRepository = settingsRepository)

            vm.onContactChanged("912345678")
            vm.onMessageChanged("hi")
            vm.onDateSelected(1000)
            vm.saveScheduledSms()
            advanceUntilIdle()

            assertEquals("+351912345678", repository.getScheduledSmsSnapshot().first().contactNumber)
        }

    @Test
    fun `save schedules the new scheduled sms`() =
        runTest {
            val scheduler = FakeScheduledSmsScheduler()
            val vm = createViewModel(scheduler = scheduler)

            vm.onContactChanged("912345678")
            vm.onMessageChanged("hi")
            vm.onDateSelected(1000)
            vm.saveScheduledSms()
            advanceUntilIdle()

            assertEquals(1, scheduler.scheduleInitialCalls.size)
        }

    @Test
    fun `save on an existing scheduled sms reschedules instead of adding`() =
        runTest {
            val existing = ScheduledSms(id = 1, contactNumber = "+351912345678", message = "old", dateEpochMs = 1000)
            val repository = FakeScheduledSmsRepository(listOf(existing))
            val scheduler = FakeScheduledSmsScheduler()
            val vm = createViewModel(repository = repository, scheduler = scheduler)

            vm.loadScheduledSms(1)
            advanceUntilIdle()
            vm.onMessageChanged("updated")
            vm.saveScheduledSms()
            advanceUntilIdle()

            assertEquals(1, repository.getScheduledSmsSnapshot().size)
            assertEquals("updated", repository.getScheduledSmsSnapshot().first().message)
            assertEquals(listOf(1L), scheduler.cancelCalls)
            assertEquals(1, scheduler.scheduleInitialCalls.size)
        }

    @Test
    fun `loadScheduledSms populates all fields including repeat and stop date`() =
        runTest {
            val existing =
                ScheduledSms(
                    id = 1,
                    name = "Rent",
                    category = "Bills",
                    contactNumber = "+351912345678",
                    message = "Pay rent",
                    dateEpochMs = 1000,
                    repeatType = RepeatType.MONTHLY,
                    stopAtEpochMs = 5000,
                )
            val repository = FakeScheduledSmsRepository(listOf(existing))
            val vm = createViewModel(repository = repository)

            vm.loadScheduledSms(1)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals("Rent", state.name)
            assertEquals("Bills", state.category)
            assertEquals("+351912345678", state.contactNumber)
            assertEquals("Pay rent", state.message)
            assertEquals(1000L, state.dateEpochMs)
            assertEquals(RepeatType.MONTHLY, state.repeatType)
            assertEquals(5000L, state.stopAtEpochMs)
        }

    @Test
    fun `loadScheduledSms resolves the contact name`() =
        runTest {
            val existing = ScheduledSms(id = 1, contactNumber = "+351912345678", message = "hi", dateEpochMs = 1000)
            val repository = FakeScheduledSmsRepository(listOf(existing))
            val vm = createViewModel(repository = repository, contacts = mapOf("+351912345678" to "Landlord"))

            vm.loadScheduledSms(1)
            advanceUntilIdle()

            assertEquals("Landlord", vm.uiState.value.contactName)
        }

    @Test
    fun `onContactPicked sets contact number and name without a debounce`() =
        runTest {
            val vm = createViewModel()

            vm.onContactPicked("+351912345678", "Landlord")

            assertEquals("+351912345678", vm.uiState.value.contactNumber)
            assertEquals("Landlord", vm.uiState.value.contactName)
        }

    @Test
    fun `manual contact number change triggers debounced lookup`() =
        runTest {
            val vm = createViewModel(contacts = mapOf("+351912345678" to "Landlord"))

            vm.onContactChanged("+351912345678")
            advanceTimeBy(350)
            advanceUntilIdle()

            assertEquals("Landlord", vm.uiState.value.contactName)
        }

    @Test
    fun `changing repeat type to NONE clears the stop date`() =
        runTest {
            val vm = createViewModel()

            vm.onRepeatTypeChanged(RepeatType.DAILY)
            vm.onStopAtSelected(5000)
            assertEquals(5000L, vm.uiState.value.stopAtEpochMs)

            vm.onRepeatTypeChanged(RepeatType.NONE)

            assertNull(vm.uiState.value.stopAtEpochMs)
        }

    @Test
    fun `changing repeat type away from NONE preserves an existing stop date`() =
        runTest {
            val vm = createViewModel()

            vm.onRepeatTypeChanged(RepeatType.DAILY)
            vm.onStopAtSelected(5000)
            vm.onRepeatTypeChanged(RepeatType.MONTHLY)

            assertEquals(5000L, vm.uiState.value.stopAtEpochMs)
        }

    @Test
    fun `save clears the stop date when repeat type is NONE even if one was set`() =
        runTest {
            val repository = FakeScheduledSmsRepository()
            val vm = createViewModel(repository = repository)

            // Directly force a stop date into state while repeat stays NONE (defensive server-side guard).
            vm.onStopAtSelected(5000)
            vm.onContactChanged("912345678")
            vm.onMessageChanged("hi")
            vm.onDateSelected(1000)
            vm.saveScheduledSms()
            advanceUntilIdle()

            assertNull(repository.getScheduledSmsSnapshot().first().stopAtEpochMs)
        }

    @Test
    fun `existing categories reflect the repository`() =
        runTest {
            val repository =
                FakeScheduledSmsRepository(
                    listOf(
                        ScheduledSms(id = 1, category = "Bills", contactNumber = "1", message = "a", dateEpochMs = 1000),
                        ScheduledSms(id = 2, category = "Personal", contactNumber = "2", message = "b", dateEpochMs = 1000),
                    ),
                )
            val vm = createViewModel(repository = repository)

            advanceUntilIdle()

            assertEquals(listOf("Bills", "Personal"), vm.uiState.value.existingCategories)
        }

    @Test
    fun `clearSavedFlag resets the saved flag`() =
        runTest {
            val vm = createViewModel()

            vm.onContactChanged("912345678")
            vm.onMessageChanged("hi")
            vm.onDateSelected(1000)
            vm.saveScheduledSms()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.saved)

            vm.clearSavedFlag()

            assertTrue(!vm.uiState.value.saved)
        }
}
