package com.neteinstein.smsredirect.data.contacts

import android.content.ContentResolver
import android.net.Uri
import android.provider.ContactsContract
import com.neteinstein.smsredirect.domain.contacts.ContactNameResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidContactNameResolver(
    private val contentResolver: ContentResolver,
) : ContactNameResolver {
    override suspend fun lookupNameByNumber(phoneNumber: String): String? =
        withContext(Dispatchers.IO) {
            if (phoneNumber.isBlank()) return@withContext null
            val digitsOnly = phoneNumber.filter { it.isDigit() }
            if (digitsOnly.length < 4) return@withContext null

            val uri =
                Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(phoneNumber),
                )
            contentResolver
                .query(
                    uri,
                    arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }
}
