package com.neteinstein.smsredirect.presentation

import com.neteinstein.smsredirect.FakeForwardHistoryRepository
import com.neteinstein.smsredirect.domain.model.ForwardHistory
import com.neteinstein.smsredirect.domain.usecase.GetForwardHistoryUseCase
import com.neteinstein.smsredirect.presentation.history.HistoryViewModel
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
class HistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val historyRepository = FakeForwardHistoryRepository()

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    private fun createViewModel(): HistoryViewModel =
        HistoryViewModel(
            getForwardHistoryUseCase = GetForwardHistoryUseCase(historyRepository),
        )

    @Test
    fun `init observes forwardHistory`() =
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()
            assertEquals(emptyList<ForwardHistory>(), vm.forwardHistory.value)
        }

    @Test
    fun `forwardHistory emits items from repository`() =
        runTest {
            val item =
                ForwardHistory(
                    id = 1L,
                    sender = "+351912345678",
                    targetNumber = "+351987654321",
                    messageBody = "Hello",
                    ruleName = "Test Rule",
                    forwardedAtEpochMs = 1000L,
                )
            historyRepository.insert(item)

            val vm = createViewModel()
            advanceUntilIdle()

            assertEquals(1, vm.forwardHistory.value.size)
            assertEquals(
                item.sender,
                vm.forwardHistory.value
                    .first()
                    .sender,
            )
        }
}
