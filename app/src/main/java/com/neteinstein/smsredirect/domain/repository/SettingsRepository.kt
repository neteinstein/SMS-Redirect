package com.neteinstein.smsredirect.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeSendWhileRoaming(): Flow<Boolean>

    suspend fun getSendWhileRoaming(): Boolean

    suspend fun setSendWhileRoaming(enabled: Boolean)

    fun observeKeepHistory(): Flow<Boolean>

    suspend fun getKeepHistory(): Boolean

    suspend fun setKeepHistory(enabled: Boolean)

    fun observeDefaultPrefix(): Flow<String>

    suspend fun getDefaultPrefix(): String

    suspend fun setDefaultPrefix(prefix: String)
}
