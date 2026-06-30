package com.neteinstein.smsredirect.domain.contacts

interface ContactNameResolver {
    suspend fun lookupNameByNumber(phoneNumber: String): String?
}
