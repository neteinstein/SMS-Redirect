package com.neteinstein.smsredirect.domain.model

/**
 * One SMS read from the device's message store, ready to be appended to Gmail.
 *
 * Always sourced from the content provider -- never from the SMS_RECEIVED broadcast payload.
 * The broadcast carries the SMSC timestamp while the provider stores the device receive time,
 * and the two differ by seconds to minutes. Hashing one against the other would give the same
 * message two different keys and back it up twice, so both the live path and the historical
 * backfill read the provider and derive identical keys.
 */
data class SmsMessageToBackup(
    val stableKey: String,
    val rawAddress: String,
    val body: String,
    val dateEpochMs: Long,
    val providerId: Long,
)
