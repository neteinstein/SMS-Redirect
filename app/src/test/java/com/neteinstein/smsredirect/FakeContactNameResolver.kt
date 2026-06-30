package com.neteinstein.smsredirect

import com.neteinstein.smsredirect.domain.contacts.ContactNameResolver

class FakeContactNameResolver(
    private val contacts: Map<String, String> = emptyMap(),
) : ContactNameResolver {
    override suspend fun lookupNameByNumber(phoneNumber: String): String? = contacts[phoneNumber]
}
