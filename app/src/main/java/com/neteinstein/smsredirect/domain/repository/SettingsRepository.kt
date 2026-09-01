package com.neteinstein.smsredirect.domain.repository

import com.neteinstein.smsredirect.domain.model.LandingScreen
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

    fun observeForwardPrefix(): Flow<String>

    suspend fun getForwardPrefix(): String

    suspend fun setForwardPrefix(prefix: String)

    fun observeShowNotifications(): Flow<Boolean>

    suspend fun getShowNotifications(): Boolean

    suspend fun setShowNotifications(enabled: Boolean)

    fun observeKeepScheduleHistory(): Flow<Boolean>

    suspend fun getKeepScheduleHistory(): Boolean

    suspend fun setKeepScheduleHistory(enabled: Boolean)

    fun observeLandingScreen(): Flow<LandingScreen>

    suspend fun getLandingScreen(): LandingScreen

    suspend fun setLandingScreen(screen: LandingScreen)
}
