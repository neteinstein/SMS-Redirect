package com.neteinstein.smsredirect.data.repository

import com.neteinstein.smsredirect.data.local.SmsBackupStateDao
import com.neteinstein.smsredirect.data.local.SmsBackupStateEntity
import com.neteinstein.smsredirect.domain.repository.SmsBackupStateRepository

class SmsBackupStateRepositoryImpl(
    private val dao: SmsBackupStateDao,
) : SmsBackupStateRepository {
    override suspend fun filterAlreadyBackedUp(stableKeys: List<String>): Set<String> {
        if (stableKeys.isEmpty()) return emptySet()
        return dao.existingKeys(stableKeys).toSet()
    }

    override suspend fun markBackedUp(
        stableKeys: List<String>,
        backedUpAtMillis: Long,
    ) {
        if (stableKeys.isEmpty()) return
        dao.insertAll(stableKeys.map { SmsBackupStateEntity(it, backedUpAtMillis) })
    }

    override suspend fun count(): Int = dao.count()

    override suspend fun clear() = dao.clear()
}
