package com.neteinstein.smsredirect

import com.neteinstein.smsredirect.domain.repository.TrustedSenderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeTrustedSenderRepository(
    initial: Set<String> = emptySet(),
) : TrustedSenderRepository {
    val state = MutableStateFlow(initial)

    override fun observe(): Flow<Set<String>> = state

    override suspend fun get(): Set<String> = state.value

    override suspend fun trust(address: String) {
        state.value = state.value + address
    }
}
