package com.neteinstein.smsredirect.domain.update

import com.neteinstein.smsredirect.domain.model.AppRelease
import kotlinx.coroutines.flow.Flow

/** Remembers when updates were last checked and what the latest release was. */
interface AppUpdateRepository {
    /** The latest release seen by the last successful check, whether or not it is newer. */
    val latestRelease: Flow<AppRelease?>

    /** Epoch millis of the last successful check, or 0 if never checked. */
    suspend fun lastCheckEpochMs(): Long

    suspend fun saveCheck(
        checkedAtEpochMs: Long,
        latest: AppRelease,
    )
}
