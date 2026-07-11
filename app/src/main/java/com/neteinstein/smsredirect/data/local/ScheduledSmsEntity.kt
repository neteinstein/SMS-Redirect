package com.neteinstein.smsredirect.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scheduled_sms")
data class ScheduledSmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "") val name: String = "",
    @ColumnInfo(defaultValue = "") val category: String = "",
    val contactNumber: String,
    val message: String,
    val dateEpochMs: Long,
    @ColumnInfo(defaultValue = "NONE") val repeatType: String = "NONE",
    val stopAtEpochMs: Long? = null,
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
)
