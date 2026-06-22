package com.neteinstein.smsredirect.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val senderText: String,
    val phoneNumber: String
)
