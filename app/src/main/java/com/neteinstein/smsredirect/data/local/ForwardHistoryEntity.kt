package com.neteinstein.smsredirect.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "forward_history")
data class ForwardHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val targetNumber: String,
    val messageBody: String,
    val ruleName: String,
    val forwardedAtEpochMs: Long
)
