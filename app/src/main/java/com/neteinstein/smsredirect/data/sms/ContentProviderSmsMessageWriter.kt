package com.neteinstein.smsredirect.data.sms

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.Telephony
import android.util.Log
import com.neteinstein.smsredirect.domain.model.SmsBox
import com.neteinstein.smsredirect.domain.model.SmsMessageToRestore
import com.neteinstein.smsredirect.domain.model.StoredSms
import com.neteinstein.smsredirect.domain.sms.DefaultSmsAppChecker
import com.neteinstein.smsredirect.domain.sms.SmsMessageWriter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidDefaultSmsAppChecker(
    private val context: Context,
) : DefaultSmsAppChecker {
    override fun isDefaultSmsApp(): Boolean = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
}

/**
 * Writes to `content://sms`. Only effective while this app is the default SMS app: Android
 * silently drops other apps' writes, which is why [canWrite] is checked rather than trusting the
 * insert's return value alone.
 *
 * The provider assigns `thread_id` from the address on insert, so restored messages land in the
 * right conversation without this class touching threads at all.
 */
class ContentProviderSmsMessageWriter(
    private val contentResolver: ContentResolver,
    private val defaultSmsAppChecker: DefaultSmsAppChecker,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SmsMessageWriter {
    override fun canWrite(): Boolean = defaultSmsAppChecker.isDefaultSmsApp()

    override suspend fun findAround(
        dateEpochMs: Long,
        toleranceMs: Long,
    ): List<StoredSms> =
        withContext(dispatcher) {
            val cursor =
                try {
                    contentResolver.query(
                        Telephony.Sms.CONTENT_URI,
                        arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                        "${Telephony.Sms.DATE} BETWEEN ? AND ?",
                        arrayOf((dateEpochMs - toleranceMs).toString(), (dateEpochMs + toleranceMs).toString()),
                        null,
                    )
                } catch (e: SecurityException) {
                    Log.w(TAG, "SMS provider query denied", e)
                    null
                } ?: return@withContext emptyList()
            cursor.use { readStored(it) }
        }

    override suspend fun insert(
        message: SmsMessageToRestore,
        read: Boolean,
    ): Boolean =
        withContext(dispatcher) {
            val values =
                ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, message.address)
                    put(Telephony.Sms.BODY, message.body)
                    put(Telephony.Sms.DATE, message.dateEpochMs)
                    if (message.dateSentEpochMs > 0) put(Telephony.Sms.DATE_SENT, message.dateSentEpochMs)
                    put(Telephony.Sms.READ, if (read) 1 else 0)
                    put(Telephony.Sms.SEEN, if (read) 1 else 0)
                    put(
                        Telephony.Sms.TYPE,
                        when (message.box) {
                            SmsBox.INBOX -> Telephony.Sms.MESSAGE_TYPE_INBOX
                            SmsBox.SENT -> Telephony.Sms.MESSAGE_TYPE_SENT
                        },
                    )
                }
            try {
                contentResolver.insert(Telephony.Sms.CONTENT_URI, values) != null
            } catch (e: SecurityException) {
                Log.w(TAG, "SMS provider insert denied", e)
                false
            } catch (e: IllegalArgumentException) {
                // OEM providers reject rows they consider malformed; never fatal for a restore.
                Log.w(TAG, "SMS provider rejected a row", e)
                false
            }
        }

    private fun readStored(cursor: Cursor): List<StoredSms> {
        val addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
        val bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY)
        val dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE)
        if (addressIndex < 0 || bodyIndex < 0 || dateIndex < 0) return emptyList()
        val result = mutableListOf<StoredSms>()
        while (cursor.moveToNext()) {
            result +=
                StoredSms(
                    address = cursor.getString(addressIndex).orEmpty(),
                    body = cursor.getString(bodyIndex).orEmpty(),
                    dateEpochMs = cursor.getLong(dateIndex),
                )
        }
        return result
    }

    private companion object {
        const val TAG = "SmsMessageWriter"
    }
}
