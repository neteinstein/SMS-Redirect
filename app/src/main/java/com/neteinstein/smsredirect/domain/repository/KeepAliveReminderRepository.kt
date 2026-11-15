package com.neteinstein.smsredirect.domain.repository

interface KeepAliveReminderRepository {
    /** True once the one-time "you can turn this off" info has been offered. */
    suspend fun isUnusedAppInfoShown(): Boolean

    suspend fun markUnusedAppInfoShown()
}
