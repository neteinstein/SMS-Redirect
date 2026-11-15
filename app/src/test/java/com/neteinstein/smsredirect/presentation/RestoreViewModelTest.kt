package com.neteinstein.smsredirect.presentation

import com.neteinstein.smsredirect.FakeDefaultSmsAppChecker
import com.neteinstein.smsredirect.FakeSmsBackupSettingsRepository
import com.neteinstein.smsredirect.FakeSmsRestoreScheduler
import com.neteinstein.smsredirect.domain.model.SmsBackupSettings
import com.neteinstein.smsredirect.domain.model.SmsRestoreState
import com.neteinstein.smsredirect.domain.usecase.GetSmsBackupSettingsUseCase
import com.neteinstein.smsredirect.presentation.restore.RestoreViewModel
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
class RestoreViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val restoreScheduler = FakeSmsRestoreScheduler()
    private val defaultSmsAppChecker = FakeDefaultSmsAppChecker()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(settings: SmsBackupSettings = configured()) =
        RestoreViewModel(
            getSmsBackupSettingsUseCase = GetSmsBackupSettingsUseCase(FakeSmsBackupSettingsRepository(settings)),
            smsRestoreScheduler = restoreScheduler,
            defaultSmsAppChecker = defaultSmsAppChecker,
        )

    private fun configured() = SmsBackupSettings(gmailAddress = "me@gmail.com", hasAppPassword = true)

    @Test
    fun `without credentials the screen asks for them`() =
        runTest {
            val vm = createViewModel(SmsBackupSettings())
            advanceUntilIdle()

            assertFalse(vm.uiState.value.hasCredentials)
        }

    @Test
    fun `confirming a restore as the default SMS app starts it`() =
        runTest {
            defaultSmsAppChecker.isDefault = true
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onRestoreConfirmed()

            assertEquals(1, restoreScheduler.requestRestoreCalls)
            assertFalse(vm.defaultSmsRoleDenied.value)
        }

    @Test
    fun `without the SMS role nothing starts and the screen says why`() =
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onRestoreConfirmed()

            assertEquals(0, restoreScheduler.requestRestoreCalls)
            assertTrue(vm.defaultSmsRoleDenied.value)
        }

    @Test
    fun `a role change made in system settings is picked up`() =
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()
            assertFalse(vm.uiState.value.isDefaultSmsApp)

            defaultSmsAppChecker.isDefault = true
            vm.refreshDefaultSmsApp()
            advanceUntilIdle()

            assertTrue(vm.uiState.value.isDefaultSmsApp)
        }

    @Test
    fun `a dismissed result is hidden until the next restore starts`() =
        runTest {
            val vm = createViewModel()
            restoreScheduler.state.value = SmsRestoreState.Finished(restored = 3, alreadyOnDevice = 0, failed = 0)
            advanceUntilIdle()

            vm.onRestoreResultDismissed()
            advanceUntilIdle()
            assertEquals(SmsRestoreState.Idle, vm.uiState.value.restoreState)

            defaultSmsAppChecker.isDefault = true
            vm.onRestoreConfirmed()
            advanceUntilIdle()
            assertEquals(SmsRestoreState.Finished(3, 0, 0), vm.uiState.value.restoreState)
        }

    @Test
    fun `stopping a restore cancels it`() =
        runTest {
            val vm = createViewModel()

            vm.onCancelRestore()

            assertEquals(1, restoreScheduler.cancelRestoreCalls)
        }
}
