package com.neteinstein.smsredirect.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeSendWhileRoaming(): Flow<Boolean>
    suspend fun getSendWhileRoaming(): Boolean
    suspend fun setSendWhileRoaming(enabled: Boolean)
    fun observeKeepHistory(): Flow<Boolean>
    suspend fun getKeepHistory(): Boolean
    suspend fun setKeepHistory(enabled: Boolean)
}
