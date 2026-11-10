package com.neteinstein.smsredirect.domain.usecase

import app.cash.turbine.test
import com.neteinstein.smsredirect.FakeAppReleaseSource
import com.neteinstein.smsredirect.FakeAppUpdateRepository
import com.neteinstein.smsredirect.domain.model.AppRelease
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdateUseCasesTest {
    private val day = CheckForAppUpdateUseCase.CHECK_INTERVAL_MS
    private val now = 10 * day
    private val newer = release("2.0.11")

    @Test
    fun `first check fetches and reports a newer release`() =
        runTest {
            // Given
            val source = FakeAppReleaseSource(newer)
            val repository = FakeAppUpdateRepository()
            val useCase = CheckForAppUpdateUseCase(source, repository, "2.0.10") { now }

            // When
            val result = useCase()

            // Then
            assertEquals(AppUpdateCheckResult.UpdateAvailable(newer), result)
            assertEquals(now, repository.lastCheck)
            assertEquals(newer, repository.latestState.value)
        }

    @Test
    fun `checks at most once a day`() =
        runTest {
            val source = FakeAppReleaseSource(newer)
            val repository = FakeAppUpdateRepository(lastCheck = now - day + 1)
            val useCase = CheckForAppUpdateUseCase(source, repository, "2.0.10") { now }

            assertEquals(AppUpdateCheckResult.Skipped, useCase())
            assertEquals(0, source.fetchCalls)
        }

    @Test
    fun `checks again once a day has passed`() =
        runTest {
            val source = FakeAppReleaseSource(newer)
            val repository = FakeAppUpdateRepository(lastCheck = now - day)
            val useCase = CheckForAppUpdateUseCase(source, repository, "2.0.10") { now }

            useCase()

            assertEquals(1, source.fetchCalls)
        }

    @Test
    fun `forced check ignores the daily limit`() =
        runTest {
            val source = FakeAppReleaseSource(release("2.0.10"))
            val repository = FakeAppUpdateRepository(lastCheck = now - 1)
            val useCase = CheckForAppUpdateUseCase(source, repository, "2.0.10") { now }

            assertEquals(AppUpdateCheckResult.UpToDate, useCase(force = true))
            assertEquals(1, source.fetchCalls)
        }

    @Test
    fun `clock moved backwards does not block checks`() =
        runTest {
            val source = FakeAppReleaseSource(newer)
            val repository = FakeAppUpdateRepository(lastCheck = now + day)
            val useCase = CheckForAppUpdateUseCase(source, repository, "2.0.10") { now }

            useCase()

            assertEquals(1, source.fetchCalls)
        }

    @Test
    fun `failed check is not recorded so the next launch retries`() =
        runTest {
            val source = FakeAppReleaseSource(release = null)
            val repository = FakeAppUpdateRepository(lastCheck = 0L)
            val useCase = CheckForAppUpdateUseCase(source, repository, "2.0.10") { now }

            assertEquals(AppUpdateCheckResult.Failed, useCase())
            assertEquals(0L, repository.lastCheck)
        }

    @Test
    fun `available update is only emitted while newer than the running app`() =
        runTest {
            val repository = FakeAppUpdateRepository()
            val useCase = GetAvailableAppUpdateUseCase(repository, "2.0.10")

            useCase().test {
                assertNull(awaitItem())
                repository.latestState.value = newer
                assertEquals(newer, awaitItem())
                repository.latestState.value = release("2.0.10")
                assertNull(awaitItem())
            }
        }

    @Test
    fun `stored update is hidden after installing it`() =
        runTest {
            val repository = FakeAppUpdateRepository(latest = newer)

            GetAvailableAppUpdateUseCase(repository, "2.0.11")().test {
                assertNull(awaitItem())
            }
        }

    private fun release(version: String) =
        AppRelease(
            versionName = version,
            releaseUrl = "https://github.com/neteinstein/SMS-Redirect/releases/tag/v$version",
            apkUrl = "https://github.com/neteinstein/SMS-Redirect/releases/download/v$version/SMS-Toolbox_v$version.apk",
            notes = "",
        )
}
