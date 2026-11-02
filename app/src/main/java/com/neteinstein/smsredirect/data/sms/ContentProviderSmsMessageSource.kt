package com.neteinstein.smsredirect.data.sms

import android.content.ContentResolver
import android.database.Cursor
import android.os.Bundle
import android.provider.Telephony
import android.util.Log
import com.neteinstein.smsredirect.domain.model.SmsBackupKey
import com.neteinstein.smsredirect.domain.model.SmsBox
import com.neteinstein.smsredirect.domain.model.SmsMessageToBackup
import com.neteinstein.smsredirect.domain.sms.SmsMessageSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the SMS inbox through `content://sms/inbox`.
 *
 * Follows the shape of [com.neteinstein.smsredirect.data.contacts.AndroidContactNameResolver]:
 * a [ContentResolver] in, IO dispatcher inside, no other logic.
 */
class ContentProviderSmsMessageSource(
    private val contentResolver: ContentResolver,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SmsMessageSource {
    override suspend fun count(sinceEpochMs: Long): Int =
        withContext(dispatcher) {
            queryCatching(
                projection = arrayOf(Telephony.Sms._ID),
                selection = if (sinceEpochMs > 0) "${Telephony.Sms.DATE} >= ?" else null,
                selectionArgs = if (sinceEpochMs > 0) arrayOf(sinceEpochMs.toString()) else null,
                sortOrder = null,
                limit = null,
            )?.use { it.count } ?: 0
        }

    override suspend fun readBatch(
        sinceEpochMs: Long,
        afterDateEpochMs: Long,
        afterId: Long,
        limit: Int,
    ): List<SmsMessageToBackup> =
        withContext(dispatcher) {
            // Keyset pagination on (date, _id) rather than an OFFSET, so the scan stays stable
            // while new messages arrive mid-backfill.
            val conditions = mutableListOf<String>()
            val args = mutableListOf<String>()

            conditions += "((${Telephony.Sms.DATE} > ?) OR (${Telephony.Sms.DATE} = ? AND ${Telephony.Sms._ID} > ?))"
            args += afterDateEpochMs.toString()
            args += afterDateEpochMs.toString()
            args += afterId.toString()

            if (sinceEpochMs > 0) {
                conditions += "${Telephony.Sms.DATE} >= ?"
                args += sinceEpochMs.toString()
            }

            val cursor =
                queryCatching(
                    projection =
                        arrayOf(
                            Telephony.Sms._ID,
                            Telephony.Sms.ADDRESS,
                            Telephony.Sms.BODY,
                            Telephony.Sms.DATE,
                            Telephony.Sms.DATE_SENT,
                        ),
                    selection = conditions.joinToString(" AND "),
                    selectionArgs = args.toTypedArray(),
                    sortOrder = "${Telephony.Sms.DATE} ASC, ${Telephony.Sms._ID} ASC",
                    limit = limit,
                ) ?: return@withContext emptyList()

            cursor.use { readMessages(it) }
        }

    private fun readMessages(cursor: Cursor): List<SmsMessageToBackup> {
        val idIndex = cursor.getColumnIndex(Telephony.Sms._ID)
        val addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
        val bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY)
        val dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE)
        // Optional: some OEM providers omit it, and a restore can do without.
        val dateSentIndex = cursor.getColumnIndex(Telephony.Sms.DATE_SENT)
        if (idIndex < 0 || addressIndex < 0 || bodyIndex < 0 || dateIndex < 0) return emptyList()

        val result = mutableListOf<SmsMessageToBackup>()
        while (cursor.moveToNext()) {
            val address = cursor.getString(addressIndex).orEmpty()
            val body = cursor.getString(bodyIndex).orEmpty()
            val date = cursor.getLong(dateIndex)
            if (address.isBlank() || body.isBlank()) continue

            result +=
                SmsMessageToBackup(
                    // DATE (device receive time) is always populated; DATE_SENT is 0 on many
                    // OEM builds. Hashing one column consistently is what keeps the key stable.
                    stableKey = SmsBackupKey.of(address, body, date),
                    rawAddress = address,
                    body = body,
                    dateEpochMs = date,
                    providerId = cursor.getLong(idIndex),
                    dateSentEpochMs = if (dateSentIndex >= 0) cursor.getLong(dateSentIndex) else 0L,
                    box = SmsBox.INBOX,
                )
        }
        return result
    }

    private fun queryCatching(
        projection: Array<String>,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
        limit: Int?,
    ): Cursor? =
        try {
            val queryArgs =
                Bundle().apply {
                    selection?.let { putString(ContentResolver.QUERY_ARG_SQL_SELECTION, it) }
                    selectionArgs?.let { putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, it) }
                    sortOrder?.let { putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, it) }
                    // Passed as a query arg rather than appended to the sort order. The
                    // "ORDER BY ... LIMIT n" trick works on AOSP's SmsProvider but is
                    // undocumented and has broken on OEM providers.
                    limit?.let { putInt(ContentResolver.QUERY_ARG_LIMIT, it) }
                }
            contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI, projection, queryArgs, null)
        } catch (e: SecurityException) {
            // READ_SMS not granted. Return nothing so the worker no-ops instead of crashing.
            Log.w(TAG, "SMS provider query denied", e)
            null
        }

    companion object {
        private const val TAG = "SmsMessageSource"
    }
}
