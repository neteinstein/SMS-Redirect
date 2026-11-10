package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.model.AppRelease
import com.neteinstein.smsredirect.domain.update.AppReleaseSource
import com.neteinstein.smsredirect.domain.update.AppUpdateRepository
import com.neteinstein.smsredirect.domain.update.AppVersions
import kotlinx.coroutines.CancellationException

/**
 * Looks up the latest release, at most once per [CHECK_INTERVAL_MS] unless forced. A failed
 * lookup is not recorded, so the next app start tries again rather than waiting a day.
 */
class CheckForAppUpdateUseCase(
    private val source: AppReleaseSource,
    private val repository: AppUpdateRepository,
    private val currentVersionName: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend operator fun invoke(force: Boolean = false): AppUpdateCheckResult {
        val now = clock()
        val sinceLastCheck = now - repository.lastCheckEpochMs()
        if (!force && sinceLastCheck in 0 until CHECK_INTERVAL_MS) return AppUpdateCheckResult.Skipped

        val latest =
            try {
                source.fetchLatestRelease()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return AppUpdateCheckResult.Failed
            }
        repository.saveCheck(now, latest)
        return if (AppVersions.isNewer(latest.versionName, currentVersionName)) {
            AppUpdateCheckResult.UpdateAvailable(latest)
        } else {
            AppUpdateCheckResult.UpToDate
        }
    }

    companion object {
        const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    }
}

sealed interface AppUpdateCheckResult {
    /** Checked less than a day ago; the stored result still stands. */
    data object Skipped : AppUpdateCheckResult

    data object UpToDate : AppUpdateCheckResult

    data class UpdateAvailable(
        val release: AppRelease,
    ) : AppUpdateCheckResult

    data object Failed : AppUpdateCheckResult
}
