package com.neteinstein.smsredirect.data.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import com.neteinstein.smsredirect.domain.repository.TrustedSenderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

/**
 * The senders trusted after a spam/fraud warning, as a string set in SharedPreferences: a short
 * device-local list, not worth a Room migration. Never exported (ExportBackupUseCase is an
 * allowlist) and never sent anywhere.
 */
class SharedPreferencesTrustedSenderRepository(
    private val prefs: SharedPreferences,
) : TrustedSenderRepository {
    private val state = MutableStateFlow(prefs.getStringSet(KEY_TRUSTED, emptySet()).orEmpty().toSet())

    override fun observe(): Flow<Set<String>> = state.asStateFlow()

    override suspend fun get(): Set<String> = state.value

    override suspend fun trust(address: String) {
        val updated = state.updateAndGet { it + address.trim() }
        // A new set every time: SharedPreferences must not be handed back the set it returned.
        prefs.edit { putStringSet(KEY_TRUSTED, HashSet(updated)) }
    }

    override suspend fun untrust(addresses: Set<String>) {
        val updated = state.updateAndGet { it - addresses }
        prefs.edit { putStringSet(KEY_TRUSTED, HashSet(updated)) }
    }

    companion object {
        const val PREFS_NAME = "trusted_senders"
        private const val KEY_TRUSTED = "trusted_senders"
    }
}
