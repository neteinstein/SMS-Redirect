package com.neteinstein.smsredirect.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "") val name: String = "",
    val senderText: String,
    val phoneNumber: String,
    val targetNumber: String,
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
)
