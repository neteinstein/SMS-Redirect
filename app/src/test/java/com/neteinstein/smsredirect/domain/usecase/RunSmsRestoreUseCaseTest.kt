package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.FakeMailTransport
import com.neteinstein.smsredirect.FakeSmsBackupSettingsRepository
import com.neteinstein.smsredirect.FakeSmsBackupStateRepository
import com.neteinstein.smsredirect.FakeSmsMessageWriter
import com.neteinstein.smsredirect.domain.mail.BackedUpMail
import com.neteinstein.smsredirect.domain.mail.MailConnectException
import com.neteinstein.smsredirect.domain.mail.SmsMailFactory
import com.neteinstein.smsredirect.domain.model.SmsBackupKey
import com.neteinstein.smsredirect.domain.model.SmsBackupSettings
import com.neteinstein.smsredirect.domain.model.SmsMessageToBackup
import com.neteinstein.smsredirect.domain.model.SmsRestoreCursor
import com.neteinstein.smsredirect.domain.model.StoredSms
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class RunSmsRestoreUseCaseTest {
    private val factory = SmsMailFactory(zoneId = ZoneId.of("UTC"))
    private val transport = FakeMailTransport()
    private val writer = FakeSmsMessageWriter()
    private val backupState = FakeSmsBackupStateRepository()
    private val settings =
        FakeSmsBackupSettingsRepository(
            SmsBackupSettings(gmailAddress = "me@gmail.com", hasAppPassword = true, gmailLabel = "Texts"),
        ).apply { storedPassword = "app-password" }

    private fun useCase() =
        RunSmsRestoreUseCase(
            settingsRepository = settings,
            backupStateRepository = backupState,
            mailTransport = transport,
            smsMessageWriter = writer,
            now = { 42L },
        )

    /** Stores a mail exactly as the backup would have written it. */
    private fun storeBackup(
        uid: Long,
        address: String = "+351912345678",
        body: String = "Message $uid",
        date: Long = 1_700_000_000_000 + uid * 60_000,
    ): SmsMessageToBackup {
        val sms = SmsMessageToBackup(SmsBackupKey.of(address, body, date), address, body, date, providerId = uid)
        val mail = factory.build("me@gmail.com", "", sms, null)
        transport.stored +=
            BackedUpMail(
                uid = uid,
                messageId = mail.messageId,
                headers = mail.extraHeaders.mapKeys { it.key.lowercase() },
                sentDateEpochMs = date,
                bodyText = mail.body.replace("\n", "\r\n"),
                fromAddress = mail.from,
            )
        return sms
    }

    @Test
    fun `restores every backed-up message from the configured label, read-only`() =
        runTest {
            // Given
            storeBackup(uid = 1)
            storeBackup(uid = 2)

            // When
            val outcome = useCase()()

            // Then
            assertEquals(SmsRestoreRunOutcome.Completed(SmsRestoreCursor(afterUid = 2, processed = 2, restored = 2)), outcome)
            assertEquals(listOf("Message 1", "Message 2"), writer.inserted.map { it.body })
            assertEquals(listOf("Texts"), transport.openedFolders)
            assertEquals(listOf(true), transport.readOnlyOpens)
        }

    @Test
    fun `messages already on the phone are skipped`() =
        runTest {
            val first = storeBackup(uid = 1)
            storeBackup(uid = 2)
            // Stored on this phone under a local-format number, half a second off (a legacy
            // backup's second-precision timestamp would look like this).
            writer.stored += StoredSms("912345678", first.body, first.dateEpochMs + 500)

            val outcome = useCase()() as SmsRestoreRunOutcome.Completed

            assertEquals(1, outcome.cursor.restored)
            assertEquals(1, outcome.cursor.alreadyOnDevice)
            assertEquals(listOf("Message 2"), writer.inserted.map { it.body })
        }

    @Test
    fun `running a restore twice never duplicates anything`() =
        runTest {
            storeBackup(uid = 1)
            storeBackup(uid = 2)

            useCase()()
            val second = useCase()() as SmsRestoreRunOutcome.Completed

            assertEquals(2, writer.inserted.size)
            assertEquals(2, second.cursor.alreadyOnDevice)
        }

    @Test
    fun `restored messages are marked backed up so the backup does not upload them again`() =
        runTest {
            val sms = storeBackup(uid = 1)

            useCase()()

            assertEquals(setOf(sms.stableKey), backupState.backedUp)
        }

    @Test
    fun `other mail filed under the label is ignored but still counted as examined`() =
        runTest {
            storeBackup(uid = 1)
            transport.stored += BackedUpMail(2, "<CAF@mail.gmail.com>", emptyMap(), 1L, "Hi\n\nthere")

            val outcome = useCase()() as SmsRestoreRunOutcome.Completed

            assertEquals(SmsRestoreCursor(afterUid = 2, processed = 2, restored = 1, notBackups = 1), outcome.cursor)
        }

    @Test
    fun `a run stops at its budget and the next run resumes from the cursor`() =
        runTest {
            (1L..5L).forEach { storeBackup(uid = it) }

            val first = useCase()(maxMessages = 2) as SmsRestoreRunOutcome.ChunkDone
            assertEquals(SmsRestoreCursor(afterUid = 2, processed = 2, restored = 2), first.cursor)

            val second = useCase()(start = first.cursor) as SmsRestoreRunOutcome.Completed
            assertEquals(SmsRestoreCursor(afterUid = 5, processed = 5, restored = 5), second.cursor)
            assertEquals((1..5).map { "Message $it" }, writer.inserted.map { it.body })
        }

    @Test
    fun `nothing is attempted unless this app is the default SMS app`() =
        runTest {
            storeBackup(uid = 1)
            writer.isDefaultSmsApp = false

            assertEquals(SmsRestoreRunOutcome.NotDefaultSmsApp, useCase()())
            assertTrue(transport.openedFolders.isEmpty())
        }

    @Test
    fun `losing the SMS role mid-restore stops instead of counting every message as failed`() =
        runTest {
            (1L..3L).forEach { storeBackup(uid = it) }
            writer.loseRoleAfterInserts = 1

            val outcome = useCase()()

            assertEquals(SmsRestoreRunOutcome.NotDefaultSmsApp, outcome)
            assertEquals(1, writer.inserted.size)
        }

    @Test
    fun `a row the provider rejects is counted as failed and the restore moves on`() =
        runTest {
            storeBackup(uid = 1)
            writer.rejectInserts = true

            val outcome = useCase()() as SmsRestoreRunOutcome.Completed

            assertEquals(1, outcome.cursor.failed)
            assertTrue(backupState.backedUp.isEmpty())
        }

    @Test
    fun `a missing label is reported, not created`() =
        runTest {
            transport.connectFailure = MailConnectException.FolderNotFound()

            assertEquals(SmsRestoreRunOutcome.LabelNotFound, useCase()())
        }

    @Test
    fun `rejected credentials are reported`() =
        runTest {
            transport.connectFailure = MailConnectException.AuthFailed()

            assertEquals(SmsRestoreRunOutcome.AuthFailed, useCase()())
        }

    @Test
    fun `a network failure keeps the cursor reached so far`() =
        runTest {
            (1L..3L).forEach { storeBackup(uid = it) }
            transport.failReadAfterUid = 1

            val outcome = useCase()(start = SmsRestoreCursor()) as SmsRestoreRunOutcome.Transient

            // The first batch (all three) was restored before the follow-up read failed.
            assertEquals(3L, outcome.cursor.afterUid)
            assertEquals(3, outcome.cursor.restored)
        }

    @Test
    fun `no credentials means not configured`() =
        runTest {
            settings.storedPassword = null

            assertEquals(SmsRestoreRunOutcome.NotConfigured, useCase()())
        }

    @Test
    fun `a forged backup under the label is skipped and counted, never written`() =
        runTest {
            // Given: a genuine backup, plus a copy of one emailed in by someone else.
            val genuine = storeBackup(uid = 1)
            storeBackup(uid = 2, address = "+351000000000", body = "Your account is locked, call us")
            transport.stored[1] = transport.stored[1].copy(fromAddress = "attacker@example.com", arrivedOverSmtp = true)

            // When
            val outcome = useCase()() as SmsRestoreRunOutcome.Completed

            // Then
            assertEquals(listOf(genuine.body), writer.inserted.map { it.body })
            assertEquals(1, outcome.cursor.notBackups)
        }
}
