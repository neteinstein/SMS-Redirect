package com.neteinstein.smsredirect.presentation

import com.neteinstein.smsredirect.FakeRuleRepository
import com.neteinstein.smsredirect.FakeSettingsRepository
import com.neteinstein.smsredirect.domain.usecase.ExportRulesUseCase
import com.neteinstein.smsredirect.domain.usecase.GetDefaultPrefixUseCase
import com.neteinstein.smsredirect.domain.usecase.GetForwardPrefixUseCase
import com.neteinstein.smsredirect.domain.usecase.GetSendWhileRoamingUseCase
import com.neteinstein.smsredirect.domain.usecase.GetShowNotificationsUseCase
import com.neteinstein.smsredirect.domain.usecase.ImportRulesUseCase
import com.neteinstein.smsredirect.domain.usecase.SetDefaultPrefixUseCase
import com.neteinstein.smsredirect.domain.usecase.SetForwardPrefixUseCase
import com.neteinstein.smsredirect.domain.usecase.SetSendWhileRoamingUseCase
import com.neteinstein.smsredirect.domain.usecase.SetShowNotificationsUseCase
import com.neteinstein.smsredirect.presentation.settings.SettingsViewModel
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
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val settingsRepository = FakeSettingsRepository()
    private val ruleRepository = FakeRuleRepository()

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    private fun createViewModel(): SettingsViewModel =
        SettingsViewModel(
            getSendWhileRoamingUseCase = GetSendWhileRoamingUseCase(settingsRepository),
            setSendWhileRoamingUseCase = SetSendWhileRoamingUseCase(settingsRepository),
            getDefaultPrefixUseCase = GetDefaultPrefixUseCase(settingsRepository),
            setDefaultPrefixUseCase = SetDefaultPrefixUseCase(settingsRepository),
            getForwardPrefixUseCase = GetForwardPrefixUseCase(settingsRepository),
            setForwardPrefixUseCase = SetForwardPrefixUseCase(settingsRepository),
            getShowNotificationsUseCase = GetShowNotificationsUseCase(settingsRepository),
            setShowNotificationsUseCase = SetShowNotificationsUseCase(settingsRepository),
            exportRulesUseCase = ExportRulesUseCase(ruleRepository),
            importRulesUseCase = ImportRulesUseCase(ruleRepository),
        )

    @Test
    fun `init observes sendWhileRoaming`() =
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()
            assertFalse(vm.sendWhileRoaming.value)
        }

    @Test
    fun `init observes defaultPrefix`() =
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()
            assertEquals("+351", vm.defaultPrefix.value)
        }

    @Test
    fun `init observes forwardPrefix`() =
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()
            assertEquals("SMS Redirect:", vm.forwardPrefix.value)
        }

    @Test
    fun `setSendWhileRoaming updates state`() =
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.setSendWhileRoaming(true)
            advanceUntilIdle()

            assertTrue(vm.sendWhileRoaming.value)
        }

    @Test
    fun `setDefaultPrefix updates state`() =
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.setDefaultPrefix("+44")
            advanceUntilIdle()

            assertEquals("+44", vm.defaultPrefix.value)
        }

    @Test
    fun `setDefaultPrefix to empty clears prefix`() =
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.setDefaultPrefix("")
            advanceUntilIdle()

            assertEquals("", vm.defaultPrefix.value)
        }

    @Test
    fun `setForwardPrefix updates state`() =
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.setForwardPrefix("Forwarded SMS:")
            advanceUntilIdle()

            assertEquals("Forwarded SMS:", vm.forwardPrefix.value)
        }

    @Test
    fun `init observes showNotifications defaults to true`() =
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()
            assertTrue(vm.showNotifications.value)
        }

    @Test
    fun `setShowNotifications updates state`() =
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.setShowNotifications(false)
            advanceUntilIdle()

            assertFalse(vm.showNotifications.value)
        }
}
