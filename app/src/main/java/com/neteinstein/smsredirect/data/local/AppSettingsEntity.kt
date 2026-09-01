package com.neteinstein.smsredirect.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(defaultValue = "0") val sendWhileRoaming: Boolean = false,
    @ColumnInfo(defaultValue = "1") val keepHistory: Boolean = true,
    @ColumnInfo(defaultValue = "+351") val defaultPrefix: String = "+351",
    @ColumnInfo(defaultValue = "SMS Toolbox:") val forwardPrefix: String = "SMS Toolbox:",
    @ColumnInfo(defaultValue = "0") val showNotifications: Boolean = false,
    @ColumnInfo(defaultValue = "1") val keepScheduleHistory: Boolean = true,
    @ColumnInfo(defaultValue = "BACKUP") val landingScreen: String = "BACKUP",
)
