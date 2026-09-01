package com.neteinstein.smsredirect.presentation

import app.cash.turbine.test
import com.neteinstein.smsredirect.FakeSmsBackupScheduler
import com.neteinstein.smsredirect.FakeSmsBackupSettingsRepository
import com.neteinstein.smsredirect.domain.model.SmsBackupProgress
import com.neteinstein.smsredirect.domain.model.SmsBackupSettings
import com.neteinstein.smsredirect.domain.usecase.GetSmsBackupSettingsUseCase
import com.neteinstein.smsredirect.domain.usecase.SetBackupEnabledUseCase
import com.neteinstein.smsredirect.domain.usecase.SetBackupGmailLabelUseCase
import com.neteinstein.smsredirect.domain.usecase.SetBackupPreviousEnabledUseCase
import com.neteinstein.smsredirect.domain.usecase.SetBackupSubjectTemplateUseCase
import com.neteinstein.smsredirect.presentation.backup.BackupViewModel
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
class BackupViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val scheduler = FakeSmsBackupScheduler()
    private lateinit var settingsRepository: FakeSmsBackupSettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(initial: SmsBackupSettings = SmsBackupSettings()): BackupViewModel {
        settingsRepository = FakeSmsBackupSettingsRepository(initial)
        return BackupViewModel(
            getSmsBackupSettingsUseCase = GetSmsBackupSettingsUseCase(settingsRepository),
            setBackupSubjectTemplateUseCase = SetBackupSubjectTemplateUseCase(settingsRepository),
            setBackupGmailLabelUseCase = SetBackupGmailLabelUseCase(settingsRepository),
            setBackupPreviousEnabledUseCase = SetBackupPreviousEnabledUseCase(settingsRepository),
            setBackupEnabledUseCase = SetBackupEnabledUseCase(settingsRepository, now = { 5_000L }),
            smsBackupScheduler = scheduler,
        )
    }

    private fun configured(
        backupPrevious: Boolean = false,
        backupEnabled: Boolean = false,
    ) = SmsBackupSettings(
        gmailAddress = "me@gmail.com",
        hasAppPassword = true,
        backupPreviousEnabled = backupPrevious,
        backupEnabled = backupEnabled,
    )

    @Test
    fun `without credentials the screen reports the empty state`() =
        runTest {
            val vm = createViewModel()

            vm.uiState.test {
                assertFalse(awaitItem().hasCredentials)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an address with no decryptable password still counts as no credentials`() =
        runTest {
            // What a database restored onto a new device looks like: the address survives the
            // backup, the keystore key does not.
            val vm = createViewModel(SmsBackupSettings(gmailAddress = "me@gmail.com", hasAppPassword = false))

            vm.uiState.test {
                assertFalse(awaitItem().hasCredentials)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `with credentials the configuration is shown`() =
        runTest {
            val vm = createViewModel(configured())

            vm.uiState.test {
                assertTrue(awaitItem().hasCredentials)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `defaults are surfaced when nothing has been configured`() =
        runTest {
            val vm = createViewModel(configured())

            vm.uiState.test {
                val state = awaitItem()
                assertEquals(SmsBackupSettings.DEFAULT_SUBJECT_TEMPLATE, state.settings.subjectTemplate)
                assertEquals(SmsBackupSettings.DEFAULT_LABEL, state.settings.gmailLabel)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `enabling backup requests a run`() =
        runTest {
            val vm = createViewModel(configured())
            advanceUntilIdle()

            vm.onToggleBackupEnabled()
            advanceUntilIdle()

            assertTrue(settingsRepository.getSettings().backupEnabled)
            assertEquals(1, scheduler.requestBackupCalls)
        }

    @Test
    fun `disabling backup cancels the run`() =
        runTest {
            val vm = createViewModel(configured(backupEnabled = true))
            advanceUntilIdle()

            vm.onToggleBackupEnabled()
            advanceUntilIdle()

            assertFalse(settingsRepository.getSettings().backupEnabled)
            assertEquals(1, scheduler.cancelBackupCalls)
        }

    @Test
    fun `enabling without backup previous records a watermark so old messages are skipped`() =
        runTest {
            val vm = createViewModel(configured(backupPrevious = false))
            advanceUntilIdle()

            vm.onToggleBackupEnabled()
            advanceUntilIdle()

            assertEquals(5_000L, settingsRepository.getSettings().backupStartedAtMillis)
        }

    @Test
    fun `enabling with backup previous clears the watermark so the whole inbox qualifies`() =
        runTest {
            val vm = createViewModel(configured(backupPrevious = true))
            advanceUntilIdle()

            vm.onToggleBackupEnabled()
            advanceUntilIdle()

            assertEquals(0L, settingsRepository.getSettings().backupStartedAtMillis)
        }

    @Test
    fun `enabling without credentials warns instead of silently doing nothing`() =
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onToggleBackupEnabled()
            advanceUntilIdle()

            assertTrue(vm.needsCredentials.value)
            assertFalse(settingsRepository.getSettings().backupEnabled)
            assertEquals(0, scheduler.requestBackupCalls)
        }

    @Test
    fun `the credentials warning clears once shown`() =
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()
            vm.onToggleBackupEnabled()
            advanceUntilIdle()

            vm.onCredentialsWarningShown()

            assertFalse(vm.needsCredentials.value)
        }

    @Test
    fun `saving the subject template persists it`() =
        runTest {
            val vm = createViewModel(configured())
            advanceUntilIdle()

            vm.onSubjectTemplateSaved("  Msg from \$CONTACT_NAME  ")
            advanceUntilIdle()

            assertEquals("Msg from \$CONTACT_NAME", settingsRepository.getSettings().subjectTemplate)
        }

    @Test
    fun `saving the gmail label normalizes stray separators`() =
        runTest {
            val vm = createViewModel(configured())
            advanceUntilIdle()

            vm.onGmailLabelSaved("/Archive//SMS/")
            advanceUntilIdle()

            assertEquals("Archive/SMS", settingsRepository.getSettings().gmailLabel)
        }

    @Test
    fun `toggling backup previous persists it`() =
        runTest {
            val vm = createViewModel(configured())
            advanceUntilIdle()

            vm.onBackupPreviousChanged(true)
            advanceUntilIdle()

            assertTrue(settingsRepository.getSettings().backupPreviousEnabled)
        }

    @Test
    fun `toggling backup previous while disabled does not start a run`() =
        runTest {
            val vm = createViewModel(configured(backupEnabled = false))
            advanceUntilIdle()

            vm.onBackupPreviousChanged(true)
            advanceUntilIdle()

            assertEquals(0, scheduler.requestBackupCalls)
        }

    @Test
    fun `toggling backup previous while already enabled recomputes the watermark and rescans`() =
        runTest {
            val vm = createViewModel(configured(backupPrevious = false, backupEnabled = true))
            advanceUntilIdle()

            vm.onBackupPreviousChanged(true)
            advanceUntilIdle()

            // Watermark cleared so the whole inbox becomes eligible, and a scan is kicked off
            // immediately rather than waiting for the next incoming SMS.
            assertEquals(0L, settingsRepository.getSettings().backupStartedAtMillis)
            assertEquals(1, scheduler.requestBackupCalls)
        }

    @Test
    fun `turning backup previous off while enabled restores the current-moment watermark`() =
        runTest {
            val vm = createViewModel(configured(backupPrevious = true, backupEnabled = true))
            advanceUntilIdle()

            vm.onBackupPreviousChanged(false)
            advanceUntilIdle()

            assertEquals(5_000L, settingsRepository.getSettings().backupStartedAtMillis)
        }

    @Test
    fun `progress from the scheduler reaches the ui`() =
        runTest {
            val vm = createViewModel(configured(backupEnabled = true))

            vm.uiState.test {
                awaitItem()
                scheduler.progress.value = SmsBackupProgress(done = 12, total = 40)
                val state = awaitItem()
                assertEquals(12, state.progress?.done)
                assertEquals(40, state.progress?.total)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
