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
    @ColumnInfo(defaultValue = "") val category: String = "",
    // v12: automation. Each column must match AppDatabase.RULES_V12_COLUMNS exactly.
    @ColumnInfo(defaultValue = "REDIRECT") val ruleType: String = "REDIRECT",
    @ColumnInfo(defaultValue = "") val messageText: String = "",
    @ColumnInfo(defaultValue = "") val phoneAction: String = "",
    @ColumnInfo(defaultValue = "") val phoneActionValue: String = "",
    @ColumnInfo(defaultValue = "") val phoneActionLabel: String = "",
)
