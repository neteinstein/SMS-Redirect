package com.neteinstein.smsredirect.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scheduled_sms_history")
data class ScheduledSmsHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduledSmsId: Long,
    val scheduledSmsName: String,
    val contactNumber: String,
    val messageBody: String,
    val sentAtEpochMs: Long,
)
