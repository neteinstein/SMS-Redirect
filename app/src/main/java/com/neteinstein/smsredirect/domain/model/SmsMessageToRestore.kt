package com.neteinstein.smsredirect.domain.model

/** One SMS recovered from a Gmail backup, ready to be written back to the device's SMS store. */
data class SmsMessageToRestore(
    val address: String,
    val body: String,
    /** Millisecond-exact for current-format backups; second precision for legacy ones. */
    val dateEpochMs: Long,
    val dateSentEpochMs: Long,
    val box: SmsBox,
)

/** An SMS already present in the device's store, used to avoid restoring a duplicate. */
data class StoredSms(
    val address: String,
    val body: String,
    val dateEpochMs: Long,
)
