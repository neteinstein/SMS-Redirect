package com.neteinstein.smsredirect.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Senders the user confirmed they trust after a spam/fraud warning: their messages are no longer
 * flagged and their links open. Kept on the device only.
 */
interface TrustedSenderRepository {
    fun observe(): Flow<Set<String>>

    suspend fun get(): Set<String>

    suspend fun trust(address: String)

    /** Removes these exact stored entries (match them first with `MessagingAddresses.same`). */
    suspend fun untrust(addresses: Set<String>)
}
