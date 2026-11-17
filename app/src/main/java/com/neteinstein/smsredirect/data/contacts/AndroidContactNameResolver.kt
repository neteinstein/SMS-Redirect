package com.neteinstein.smsredirect.data.contacts

import android.content.ContentResolver
import android.net.Uri
import android.provider.ContactsContract
import com.neteinstein.smsredirect.domain.contacts.ContactNameResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Returns null, never throws, when contacts access is missing: rules and schedules can be edited
 * before the user grants READ_CONTACTS (or after they revoke it), and a name is only a nicety.
 */
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
            try {
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
            } catch (e: SecurityException) {
                null
            }
        }
}
