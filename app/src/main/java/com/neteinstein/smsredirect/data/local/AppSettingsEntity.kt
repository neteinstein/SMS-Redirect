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
    // The SQL default stays 'BACKUP' on purpose: changing it would change the schema, and with it
    // the table rebuild Room demands. Rows are always written from Kotlin, so only the Kotlin
    // default matters for new installs; MIGRATION_10_11 moves existing rows.
    @ColumnInfo(defaultValue = "BACKUP") val landingScreen: String = "MESSAGES",
)
