package com.neteinstein.smsredirect.presentation

import com.neteinstein.smsredirect.FakeContactNameResolver
import com.neteinstein.smsredirect.FakeScheduledSmsRepository
import com.neteinstein.smsredirect.FakeScheduledSmsScheduler
import com.neteinstein.smsredirect.domain.model.ScheduledSms
import com.neteinstein.smsredirect.domain.usecase.DeleteScheduledSmsUseCase
import com.neteinstein.smsredirect.domain.usecase.GetScheduledSmsListUseCase
import com.neteinstein.smsredirect.domain.usecase.ReorderScheduledSmsUseCase
import com.neteinstein.smsredirect.presentation.schedule.ScheduleListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleListViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads and deletes scheduled sms`() =
        runTest {
            val repository =
                FakeScheduledSmsRepository(
                    listOf(ScheduledSms(id = 1, contactNumber = "555", message = "hi", dateEpochMs = 1000)),
                )
            val vm =
                ScheduleListViewModel(
                    GetScheduledSmsListUseCase(repository),
                    DeleteScheduledSmsUseCase(repository, FakeScheduledSmsScheduler()),
                    ReorderScheduledSmsUseCase(repository),
                )

            advanceUntilIdle()
            assertEquals(1, vm.scheduledSmsList.value.size)

            vm.deleteScheduledSms(vm.scheduledSmsList.value.first())
            advanceUntilIdle()

            assertEquals(0, vm.scheduledSmsList.value.size)
        }

    @Test
    fun `moves scheduled sms and commits reorder`() =
        runTest {
            val repository =
                FakeScheduledSmsRepository(
                    listOf(
                        ScheduledSms(id = 1, name = "First", contactNumber = "111", message = "a", dateEpochMs = 1000, sortOrder = 0),
                        ScheduledSms(id = 2, name = "Second", contactNumber = "222", message = "b", dateEpochMs = 1000, sortOrder = 1),
                        ScheduledSms(id = 3, name = "Third", contactNumber = "333", message = "c", dateEpochMs = 1000, sortOrder = 2),
                    ),
                )
            val vm =
                ScheduleListViewModel(
                    GetScheduledSmsListUseCase(repository),
                    DeleteScheduledSmsUseCase(repository, FakeScheduledSmsScheduler()),
                    ReorderScheduledSmsUseCase(repository),
                )

            advanceUntilIdle()
            assertEquals(3, vm.scheduledSmsList.value.size)

            vm.moveScheduledSms(0, 2)
            assertEquals("Second", vm.scheduledSmsList.value[0].name)
            assertEquals("Third", vm.scheduledSmsList.value[1].name)
            assertEquals("First", vm.scheduledSmsList.value[2].name)

            vm.commitReorder()
            advanceUntilIdle()

            val saved = repository.getScheduledSmsSnapshot()
            assertEquals(0, saved[0].sortOrder)
            assertEquals(1, saved[1].sortOrder)
            assertEquals(2, saved[2].sortOrder)
        }

    @Test
    fun `resolves contact names when resolver is provided`() =
        runTest {
            val repository =
                FakeScheduledSmsRepository(
                    listOf(ScheduledSms(id = 1, contactNumber = "555", message = "hi", dateEpochMs = 1000)),
                )
            val resolver = FakeContactNameResolver(mapOf("555" to "Alice"))
            val vm =
                ScheduleListViewModel(
                    GetScheduledSmsListUseCase(repository),
                    DeleteScheduledSmsUseCase(repository, FakeScheduledSmsScheduler()),
                    ReorderScheduledSmsUseCase(repository),
                    resolver,
                )

            advanceUntilIdle()

            assertEquals("Alice", vm.contactNames.value["555"])
        }

    @Test
    fun `contact names stay empty when no resolver is provided`() =
        runTest {
            val repository =
                FakeScheduledSmsRepository(
                    listOf(ScheduledSms(id = 1, contactNumber = "555", message = "hi", dateEpochMs = 1000)),
                )
            val vm =
                ScheduleListViewModel(
                    GetScheduledSmsListUseCase(repository),
                    DeleteScheduledSmsUseCase(repository, FakeScheduledSmsScheduler()),
                    ReorderScheduledSmsUseCase(repository),
                )

            advanceUntilIdle()

            assertEquals(emptyMap<String, String>(), vm.contactNames.value)
        }
}
