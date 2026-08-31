package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.FakeContactNameResolver
import com.neteinstein.smsredirect.FakeMailTransport
import com.neteinstein.smsredirect.FakeSmsBackupSettingsRepository
import com.neteinstein.smsredirect.FakeSmsBackupStateRepository
import com.neteinstein.smsredirect.FakeSmsMessageSource
import com.neteinstein.smsredirect.domain.mail.MailAppendResult
import com.neteinstein.smsredirect.domain.mail.MailVerificationResult
import com.neteinstein.smsredirect.domain.mail.SmsMailFactory
import com.neteinstein.smsredirect.domain.model.SmsBackupKey
import com.neteinstein.smsredirect.domain.model.SmsBackupProgress
import com.neteinstein.smsredirect.domain.model.SmsBackupSettings
import com.neteinstein.smsredirect.domain.model.SmsMessageToBackup
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsBackupUseCasesTest {
    private val transport = FakeMailTransport()
    private val stateRepository = FakeSmsBackupStateRepository()
    private val messageSource = FakeSmsMessageSource()
    private val settingsRepository =
        FakeSmsBackupSettingsRepository(
            SmsBackupSettings(gmailAddress = "me@gmail.com", hasAppPassword = true, backupEnabled = true),
        ).apply { storedPassword = "abcdefghijklmnop" }

    private fun message(
        body: String,
        date: Long,
        providerId: Long,
        address: String = "+351912345678",
    ) = SmsMessageToBackup(
        stableKey = SmsBackupKey.of(address, body, date),
        rawAddress = address,
        body = body,
        dateEpochMs = date,
        providerId = providerId,
    )

    private fun runUseCase() =
        RunSmsBackupUseCase(
            settingsRepository = settingsRepository,
            backupStateRepository = stateRepository,
            smsMessageSource = messageSource,
            mailTransport = transport,
            backupSmsMessageUseCase =
                BackupSmsMessageUseCase(
                    mailFactory = SmsMailFactory(),
                    backupStateRepository = stateRepository,
                    contactNameResolver = FakeContactNameResolver(mapOf("+351912345678" to "Jane")),
                ),
            // No pacing delay: runTest would otherwise skip virtual time per message for nothing.
            interMessageDelayMs = 0,
        )

    // --- happy path ---

    @Test
    fun `backs up every pending message and records each one`() =
        runTest {
            messageSource.messages = (1L..5L).map { message("Body $it", it * 1000, it) }

            val outcome = runUseCase()()

            assertEquals(SmsBackupRunOutcome.Completed, outcome)
            assertEquals(5, transport.appended.size)
            assertEquals(5, stateRepository.backedUp.size)
        }

    @Test
    fun `uses the configured gmail label`() =
        runTest {
            settingsRepository.state.value = settingsRepository.state.value.copy(gmailLabel = "Archive/SMS")
            messageSource.messages = listOf(message("Body", 1000, 1))

            runUseCase()()

            assertEquals(listOf("Archive/SMS"), transport.openedFolders)
        }

    @Test
    fun `opens a single folder for the whole run rather than one per message`() =
        runTest {
            messageSource.messages = (1L..20L).map { message("Body $it", it * 1000, it) }

            runUseCase()()

            assertEquals(1, transport.openedFolders.size)
        }

    // --- idempotency and resume ---

    @Test
    fun `already backed up messages are not sent again`() =
        runTest {
            val messages = (1L..3L).map { message("Body $it", it * 1000, it) }
            messageSource.messages = messages
            stateRepository.backedUp += messages.first().stableKey

            runUseCase()()

            assertEquals(2, transport.appended.size)
        }

    @Test
    fun `a second run after a completed one sends nothing`() =
        runTest {
            messageSource.messages = (1L..4L).map { message("Body $it", it * 1000, it) }

            runUseCase()()
            val appendedAfterFirst = transport.appended.size
            val outcome = runUseCase()()

            assertEquals(SmsBackupRunOutcome.Completed, outcome)
            assertEquals(appendedAfterFirst, transport.appended.size)
        }

    @Test
    fun `a run interrupted mid batch resumes without duplicating`() =
        runTest {
            messageSource.messages = (1L..6L).map { message("Body $it", it * 1000, it) }
            // Fail on the third append, simulating the process dying mid-batch.
            transport.failAppendAtIndex = 3
            transport.appendFailure = MailAppendResult.Transient("connection reset")

            val first = runUseCase()()
            assertTrue(first is SmsBackupRunOutcome.Transient)
            val sentBeforeFailure = transport.appended.size

            transport.failAppendAtIndex = null
            val second = runUseCase()()

            assertEquals(SmsBackupRunOutcome.Completed, second)
            assertEquals(6, transport.appended.size)
            // Every message present exactly once.
            assertEquals(6, transport.appended.map { it.messageId }.toSet().size)
            assertTrue(sentBeforeFailure < 6)
        }

    // --- the watermark ---

    @Test
    fun `with a watermark set only newer messages are eligible`() =
        runTest {
            settingsRepository.state.value = settingsRepository.state.value.copy(backupStartedAtMillis = 3000)
            messageSource.messages = (1L..5L).map { message("Body $it", it * 1000, it) }

            runUseCase()()

            // Dates 3000, 4000, 5000 qualify; 1000 and 2000 do not.
            assertEquals(3, transport.appended.size)
        }

    @Test
    fun `with no watermark the whole inbox is eligible`() =
        runTest {
            settingsRepository.state.value = settingsRepository.state.value.copy(backupStartedAtMillis = 0)
            messageSource.messages = (1L..5L).map { message("Body $it", it * 1000, it) }

            runUseCase()()

            assertEquals(5, transport.appended.size)
        }

    // --- bounded runs ---

    @Test
    fun `a run stops at the message budget and reports a chunk`() =
        runTest {
            messageSource.messages = (1L..120L).map { message("Body $it", it * 1000, it) }

            val outcome = runUseCase()(maxMessages = 60)

            assertTrue(outcome is SmsBackupRunOutcome.ChunkDone)
            assertEquals(60, transport.appended.size)
        }

    @Test
    fun `chunks continue from where the previous one stopped`() =
        runTest {
            messageSource.messages = (1L..120L).map { message("Body $it", it * 1000, it) }

            runUseCase()(maxMessages = 60)
            val outcome = runUseCase()(maxMessages = 60)

            assertEquals(SmsBackupRunOutcome.Completed, outcome)
            assertEquals(120, transport.appended.size)
            assertEquals(120, transport.appended.map { it.messageId }.toSet().size)
        }

    @Test
    fun `cancellation stops the run and reports a chunk`() =
        runTest {
            messageSource.messages = (1L..50L).map { message("Body $it", it * 1000, it) }
            var calls = 0

            val outcome = runUseCase()(isStopped = { calls++ > 5 })

            assertTrue(outcome is SmsBackupRunOutcome.ChunkDone)
            assertTrue(transport.appended.size < 50)
        }

    // --- progress ---

    @Test
    fun `progress counts up to the total`() =
        runTest {
            messageSource.messages = (1L..4L).map { message("Body $it", it * 1000, it) }
            val seen = mutableListOf<SmsBackupProgress>()

            runUseCase()(onProgress = { seen += it })

            assertEquals(listOf(1, 2, 3, 4), seen.map { it.done })
            assertTrue(seen.all { it.total == 4 })
        }

    // --- failures ---

    @Test
    fun `an auth failure switches backup off so the ui cannot show a stale enabled state`() =
        runTest {
            messageSource.messages = listOf(message("Body", 1000, 1))
            transport.failAppendAtIndex = 1
            transport.appendFailure = MailAppendResult.AuthFailed

            val outcome = runUseCase()()

            assertEquals(SmsBackupRunOutcome.AuthFailed, outcome)
            assertEquals(false, settingsRepository.getSettings().backupEnabled)
        }

    @Test
    fun `a permanently rejected message is recorded so the run cannot loop on it forever`() =
        runTest {
            messageSource.messages = (1L..3L).map { message("Body $it", it * 1000, it) }
            transport.failAppendAtIndex = 1
            transport.appendFailure = MailAppendResult.Permanent("malformed")

            val outcome = runUseCase()()

            assertEquals(SmsBackupRunOutcome.Completed, outcome)
            // All three are resolved: two delivered, one recorded as permanently rejected.
            assertEquals(3, stateRepository.backedUp.size)
        }

    // --- not configured ---

    @Test
    fun `does nothing when backup is disabled`() =
        runTest {
            settingsRepository.state.value = settingsRepository.state.value.copy(backupEnabled = false)
            messageSource.messages = listOf(message("Body", 1000, 1))

            val outcome = runUseCase()()

            assertEquals(SmsBackupRunOutcome.NotConfigured, outcome)
            assertTrue(transport.appended.isEmpty())
        }

    @Test
    fun `does nothing when the stored password cannot be decrypted`() =
        runTest {
            settingsRepository.credentialsUndecryptable = true
            messageSource.messages = listOf(message("Body", 1000, 1))

            val outcome = runUseCase()()

            assertEquals(SmsBackupRunOutcome.NotConfigured, outcome)
            assertTrue(transport.appended.isEmpty())
        }

    // --- credential verification ---

    @Test
    fun `verification persists the credentials only on success`() =
        runTest {
            val repository = FakeSmsBackupSettingsRepository()
            val useCase = VerifyGmailCredentialsUseCase(transport, repository)

            transport.verifyResult = MailVerificationResult.Success
            assertEquals(MailVerificationResult.Success, useCase("me@gmail.com", "abcd efgh ijkl mnop"))
            assertEquals("me@gmail.com", repository.getSettings().gmailAddress)
        }

    @Test
    fun `a rejected verification stores nothing`() =
        runTest {
            val repository = FakeSmsBackupSettingsRepository()
            val useCase = VerifyGmailCredentialsUseCase(transport, repository)
            transport.verifyResult = MailVerificationResult.InvalidCredentials

            val result = useCase("me@gmail.com", "wrongpassword")

            assertEquals(MailVerificationResult.InvalidCredentials, result)
            assertEquals("", repository.getSettings().gmailAddress)
            assertNull(repository.storedPassword)
        }

    @Test
    fun `spaces are stripped from a pasted app password`() =
        runTest {
            val repository = FakeSmsBackupSettingsRepository()
            val useCase = VerifyGmailCredentialsUseCase(transport, repository)
            transport.verifyResult = MailVerificationResult.Success

            // Google displays app passwords in groups of four; users paste them as shown.
            useCase("me@gmail.com", "abcd efgh ijkl mnop")

            assertEquals("abcdefghijklmnop", repository.storedPassword)
        }

    @Test
    fun `blank input is rejected without contacting the server`() =
        runTest {
            val repository = FakeSmsBackupSettingsRepository()
            val freshTransport = FakeMailTransport()
            val useCase = VerifyGmailCredentialsUseCase(freshTransport, repository)

            val result = useCase("", "")

            assertEquals(MailVerificationResult.InvalidCredentials, result)
            assertNull(freshTransport.verifiedWith)
        }

    // --- label normalization ---

    @Test
    fun `label normalization strips empty and stray path segments`() =
        runTest {
            val repository = FakeSmsBackupSettingsRepository()
            val useCase = SetBackupGmailLabelUseCase(repository)

            useCase("/ Archive // SMS /")

            assertEquals("Archive/SMS", repository.getSettings().gmailLabel)
        }

    // --- enable watermark ---

    @Test
    fun `enabling without backup previous records a watermark`() =
        runTest {
            val repository = FakeSmsBackupSettingsRepository()
            val useCase = SetBackupEnabledUseCase(repository, now = { 5_000L })

            useCase(enabled = true, backupPrevious = false)

            assertEquals(5_000L, repository.getSettings().backupStartedAtMillis)
        }

    @Test
    fun `enabling with backup previous clears the watermark so old messages qualify`() =
        runTest {
            val repository = FakeSmsBackupSettingsRepository()
            val useCase = SetBackupEnabledUseCase(repository, now = { 5_000L })

            useCase(enabled = true, backupPrevious = true)

            assertEquals(0L, repository.getSettings().backupStartedAtMillis)
        }
}
