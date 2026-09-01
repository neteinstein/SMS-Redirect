package com.neteinstein.smsredirect.presentation

import com.neteinstein.smsredirect.FakeMailTransport
import com.neteinstein.smsredirect.FakeRuleRepository
import com.neteinstein.smsredirect.FakeScheduledSmsRepository
import com.neteinstein.smsredirect.FakeScheduledSmsScheduler
import com.neteinstein.smsredirect.FakeSettingsRepository
import com.neteinstein.smsredirect.FakeSmsBackupScheduler
import com.neteinstein.smsredirect.FakeSmsBackupSettingsRepository
import com.neteinstein.smsredirect.FakeSmsBackupStateRepository
import com.neteinstein.smsredirect.domain.mail.MailVerificationResult
import com.neteinstein.smsredirect.domain.usecase.ClearGmailCredentialsUseCase
import com.neteinstein.smsredirect.domain.usecase.ExportBackupUseCase
import com.neteinstein.smsredirect.domain.usecase.GetDefaultPrefixUseCase
import com.neteinstein.smsredirect.domain.usecase.GetForwardPrefixUseCase
import com.neteinstein.smsredirect.domain.usecase.GetSendWhileRoamingUseCase
import com.neteinstein.smsredirect.domain.usecase.GetShowNotificationsUseCase
import com.neteinstein.smsredirect.domain.usecase.GetSmsBackupSettingsUseCase
import com.neteinstein.smsredirect.domain.usecase.ImportBackupUseCase
import com.neteinstein.smsredirect.domain.usecase.SetDefaultPrefixUseCase
import com.neteinstein.smsredirect.domain.usecase.SetForwardPrefixUseCase
import com.neteinstein.smsredirect.domain.usecase.SetSendWhileRoamingUseCase
import com.neteinstein.smsredirect.domain.usecase.SetShowNotificationsUseCase
import com.neteinstein.smsredirect.domain.usecase.VerifyGmailCredentialsUseCase
import com.neteinstein.smsredirect.presentation.settings.GmailVerificationState
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
    private val scheduledSmsRepository = FakeScheduledSmsRepository()
    private val scheduledSmsScheduler = FakeScheduledSmsScheduler()
    private val backupSettingsRepository = FakeSmsBackupSettingsRepository()
    private val backupStateRepository = FakeSmsBackupStateRepository()
    private val mailTransport = FakeMailTransport()
    private val smsBackupScheduler = FakeSmsBackupScheduler()

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
            exportBackupUseCase = ExportBackupUseCase(ruleRepository, scheduledSmsRepository, settingsRepository),
            importBackupUseCase =
                ImportBackupUseCase(ruleRepository, scheduledSmsRepository, settingsRepository, scheduledSmsScheduler),
            getSmsBackupSettingsUseCase = GetSmsBackupSettingsUseCase(backupSettingsRepository),
            verifyGmailCredentialsUseCase =
                VerifyGmailCredentialsUseCase(mailTransport, backupSettingsRepository, backupStateRepository),
            clearGmailCredentialsUseCase = ClearGmailCredentialsUseCase(backupSettingsRepository),
            smsBackupScheduler = smsBackupScheduler,
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
            assertEquals("SMS Toolbox:", vm.forwardPrefix.value)
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

    @Test
    fun `saving gmail credentials moves to success and persists them`() =
        runTest {
            mailTransport.verifyResult = MailVerificationResult.Success
            val vm = createViewModel()
            advanceUntilIdle()

            vm.saveGmailCredentials("me@gmail.com", "abcd efgh ijkl mnop")
            advanceUntilIdle()

            assertEquals(GmailVerificationState.Success, vm.gmailVerification.value)
            assertEquals("me@gmail.com", vm.backupSettings.value.gmailAddress)
            assertTrue(vm.backupSettings.value.hasAppPassword)
        }

    @Test
    fun `rejected credentials surface the reason and persist nothing`() =
        runTest {
            mailTransport.verifyResult = MailVerificationResult.InvalidCredentials
            val vm = createViewModel()
            advanceUntilIdle()

            vm.saveGmailCredentials("me@gmail.com", "wrongpassword")
            advanceUntilIdle()

            val state = vm.gmailVerification.value
            assertTrue(state is GmailVerificationState.Failure)
            assertEquals(
                MailVerificationResult.InvalidCredentials,
                (state as GmailVerificationState.Failure).result,
            )
            assertEquals("", vm.backupSettings.value.gmailAddress)
            assertFalse(vm.backupSettings.value.hasAppPassword)
        }

    @Test
    fun `a network failure is reported separately from a rejection`() =
        runTest {
            mailTransport.verifyResult = MailVerificationResult.NetworkError
            val vm = createViewModel()
            advanceUntilIdle()

            vm.saveGmailCredentials("me@gmail.com", "abcdefghijklmnop")
            advanceUntilIdle()

            val state = vm.gmailVerification.value as GmailVerificationState.Failure
            assertEquals(MailVerificationResult.NetworkError, state.result)
        }

    @Test
    fun `clearing credentials resets both the stored values and the verification state`() =
        runTest {
            mailTransport.verifyResult = MailVerificationResult.Success
            val vm = createViewModel()
            advanceUntilIdle()
            vm.saveGmailCredentials("me@gmail.com", "abcdefghijklmnop")
            advanceUntilIdle()

            vm.clearGmailCredentials()
            advanceUntilIdle()

            assertEquals(GmailVerificationState.Idle, vm.gmailVerification.value)
            assertEquals("", vm.backupSettings.value.gmailAddress)
            assertFalse(vm.backupSettings.value.hasAppPassword)
        }

    @Test
    fun `clearing credentials cancels any backup run so it cannot keep failing against them`() =
        runTest {
            mailTransport.verifyResult = MailVerificationResult.Success
            val vm = createViewModel()
            advanceUntilIdle()
            vm.saveGmailCredentials("me@gmail.com", "abcdefghijklmnop")
            advanceUntilIdle()

            vm.clearGmailCredentials()
            advanceUntilIdle()

            assertEquals(1, smsBackupScheduler.cancelBackupCalls)
        }
}
