package com.neteinstein.smsredirect

import com.neteinstein.smsredirect.domain.repository.SmsBackupStateRepository

class FakeSmsBackupStateRepository(
    initial: Set<String> = emptySet(),
) : SmsBackupStateRepository {
    val backedUp = initial.toMutableSet()

    override suspend fun filterAlreadyBackedUp(stableKeys: List<String>): Set<String> = stableKeys.filter { it in backedUp }.toSet()

    override suspend fun markBackedUp(
        stableKeys: List<String>,
        backedUpAtMillis: Long,
    ) {
        backedUp += stableKeys
    }

    override suspend fun count(): Int = backedUp.size

    override suspend fun clear() = backedUp.clear()
}
