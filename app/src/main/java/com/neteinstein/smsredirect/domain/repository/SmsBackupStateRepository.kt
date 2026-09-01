package com.neteinstein.smsredirect.domain.repository

/**
 * Tracks which messages have already reached Gmail, so a backfill killed mid-run resumes instead
 * of restarting, and never sends anything twice.
 */
interface SmsBackupStateRepository {
    /** Batch membership check -- one query per chunk rather than one per message. */
    suspend fun filterAlreadyBackedUp(stableKeys: List<String>): Set<String>

    suspend fun markBackedUp(
        stableKeys: List<String>,
        backedUpAtMillis: Long,
    )

    suspend fun count(): Int

    suspend fun clear()
}
