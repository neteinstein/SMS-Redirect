package com.neteinstein.smsredirect.data.repository

import com.neteinstein.smsredirect.FakeSecretCipher
import com.neteinstein.smsredirect.data.local.BackupSettingsDao
import com.neteinstein.smsredirect.data.local.BackupSettingsEntity
import com.neteinstein.smsredirect.domain.model.SmsBackupSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsBackupSettingsRepositoryImplTest {
    private class FakeBackupSettingsDao(
        initial: BackupSettingsEntity? = null,
    ) : BackupSettingsDao {
        val row = MutableStateFlow(initial)

        override fun observeSettings(): Flow<BackupSettingsEntity?> = row

        override suspend fun getSettings(): BackupSettingsEntity? = row.value

        override suspend fun upsert(settings: BackupSettingsEntity) {
            row.value = settings
        }
    }

    private val cipher = FakeSecretCipher()

    @Test
    fun `a missing row yields the domain defaults rather than blanks`() =
        runTest {
            val repository = SmsBackupSettingsRepositoryImpl(FakeBackupSettingsDao(), cipher)

            val settings = repository.getSettings()

            assertEquals(SmsBackupSettings.DEFAULT_SUBJECT_TEMPLATE, settings.subjectTemplate)
            assertEquals(SmsBackupSettings.DEFAULT_LABEL, settings.gmailLabel)
            assertFalse(settings.backupEnabled)
            assertFalse(settings.hasCredentials)
        }

    @Test
    fun `blank columns fall back to the domain defaults`() =
        runTest {
            // The table stores no SQL defaults, so blank is the on-disk representation of "unset".
            val dao = FakeBackupSettingsDao(BackupSettingsEntity(subjectTemplate = "", gmailLabel = ""))
            val repository = SmsBackupSettingsRepositoryImpl(dao, cipher)

            val settings = repository.getSettings()

            assertEquals(SmsBackupSettings.DEFAULT_SUBJECT_TEMPLATE, settings.subjectTemplate)
            assertEquals(SmsBackupSettings.DEFAULT_LABEL, settings.gmailLabel)
        }

    @Test
    fun `configured values win over the defaults`() =
        runTest {
            val dao =
                FakeBackupSettingsDao(
                    BackupSettingsEntity(subjectTemplate = "Custom", gmailLabel = "Archive/SMS"),
                )
            val repository = SmsBackupSettingsRepositoryImpl(dao, cipher)

            val settings = repository.getSettings()

            assertEquals("Custom", settings.subjectTemplate)
            assertEquals("Archive/SMS", settings.gmailLabel)
        }

    @Test
    fun `the app password is stored encrypted, never in the clear`() =
        runTest {
            val dao = FakeBackupSettingsDao()
            val repository = SmsBackupSettingsRepositoryImpl(dao, cipher)

            repository.setCredentials("me@gmail.com", "abcdefghijklmnop")

            val stored = dao.getSettings()!!.gmailAppPasswordCipher
            assertNotEquals("abcdefghijklmnop", stored)
            assertTrue(stored.startsWith(FakeSecretCipher.PREFIX))
        }

    @Test
    fun `credentials round trip through the cipher`() =
        runTest {
            val repository = SmsBackupSettingsRepositoryImpl(FakeBackupSettingsDao(), cipher)
            repository.setCredentials("me@gmail.com", "abcdefghijklmnop")

            val credentials = repository.getCredentials()

            assertEquals("me@gmail.com", credentials?.address)
            assertEquals("abcdefghijklmnop", credentials?.appPassword)
        }

    @Test
    fun `an undecryptable password reads as no credentials at all`() =
        runTest {
            val repository = SmsBackupSettingsRepositoryImpl(FakeBackupSettingsDao(), cipher)
            repository.setCredentials("me@gmail.com", "abcdefghijklmnop")

            // The database was restored onto a device whose keystore lacks the key.
            cipher.failDecrypt = true

            assertNull(repository.getCredentials())
            // The address is still on record, which is what lets the UI say "re-enter your
            // password" instead of reporting an authentication failure.
            assertEquals("me@gmail.com", repository.getSettings().gmailAddress)
            // Ciphertext is still on record too, but it's unusable -- the UI must not claim a
            // password is configured when it cannot actually be decrypted.
            assertFalse(repository.getSettings().hasAppPassword)
        }

    @Test
    fun `hasAppPassword reflects stored ciphertext without exposing the plaintext`() =
        runTest {
            val repository = SmsBackupSettingsRepositoryImpl(FakeBackupSettingsDao(), cipher)
            assertFalse(repository.getSettings().hasAppPassword)

            repository.setCredentials("me@gmail.com", "abcdefghijklmnop")

            assertTrue(repository.getSettings().hasAppPassword)
        }

    @Test
    fun `clearing credentials also switches backup off`() =
        runTest {
            val repository = SmsBackupSettingsRepositoryImpl(FakeBackupSettingsDao(), cipher)
            repository.setCredentials("me@gmail.com", "abcdefghijklmnop")
            repository.setBackupEnabled(enabled = true, startedAtMillis = 1_000L)

            repository.clearCredentials()

            val settings = repository.getSettings()
            assertEquals("", settings.gmailAddress)
            assertFalse(settings.hasAppPassword)
            assertFalse(settings.backupEnabled)
        }

    @Test
    fun `setters preserve the other fields`() =
        runTest {
            val repository = SmsBackupSettingsRepositoryImpl(FakeBackupSettingsDao(), cipher)
            repository.setCredentials("me@gmail.com", "abcdefghijklmnop")

            repository.setSubjectTemplate("Custom")
            repository.setGmailLabel("Archive")
            repository.setBackupPreviousEnabled(true)
            repository.setBackupEnabled(enabled = true, startedAtMillis = 7_000L)

            val settings = repository.getSettings()
            assertEquals("me@gmail.com", settings.gmailAddress)
            assertTrue(settings.hasAppPassword)
            assertEquals("Custom", settings.subjectTemplate)
            assertEquals("Archive", settings.gmailLabel)
            assertTrue(settings.backupPreviousEnabled)
            assertTrue(settings.backupEnabled)
            assertEquals(7_000L, settings.backupStartedAtMillis)
        }
}
