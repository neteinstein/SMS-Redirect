package com.neteinstein.smsredirect.data.messaging

import android.app.Activity
import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.neteinstein.smsredirect.MessageStatusReceiver
import com.neteinstein.smsredirect.domain.messaging.MessageSender
import com.neteinstein.smsredirect.domain.messaging.SendFailure
import com.neteinstein.smsredirect.domain.messaging.SendOutcome
import com.neteinstein.smsredirect.domain.mms.MmsCharsets
import com.neteinstein.smsredirect.domain.mms.MmsHeaders
import com.neteinstein.smsredirect.domain.mms.MmsPart
import com.neteinstein.smsredirect.domain.mms.MmsPdu
import com.neteinstein.smsredirect.domain.mms.MmsPduComposer
import com.neteinstein.smsredirect.domain.mms.MmsPduParser
import com.neteinstein.smsredirect.domain.mms.MmsSmil
import com.neteinstein.smsredirect.domain.model.OutgoingAttachment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID

/**
 * Sends SMS and MMS through `SmsManager`, as the default SMS app must: the message is first
 * written to the outbox, then handed to the radio, and [MessageStatusReceiver] moves it to "sent"
 * or "failed" when the platform reports back (see [onSmsSent] / [onMmsSent]). Only the default
 * SMS app's writes reach the provider, which is why a refused outbox insert means "not default".
 *
 * Never logs addresses or message text.
 */
class AndroidMessageSender(
    private val context: Context,
    private val store: ContentProviderMessageStore,
    private val composer: MmsPduComposer = MmsPduComposer(),
    private val parser: MmsPduParser = MmsPduParser(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MessageSender {
    private val resolver get() = context.contentResolver

    override suspend fun sendSms(
        address: String,
        body: String,
    ): SendOutcome =
        withContext(dispatcher) {
            val smsManager = smsManager() ?: return@withContext SendOutcome.Failed(SendFailure.NO_SERVICE)
            val threadId =
                runCatching { Telephony.Threads.getOrCreateThreadId(context, address) }.getOrNull()
                    ?: return@withContext SendOutcome.Failed(SendFailure.NOT_DEFAULT_SMS_APP)
            val values =
                ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, address)
                    put(Telephony.Sms.BODY, body)
                    put(Telephony.Sms.DATE, System.currentTimeMillis())
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.SEEN, 1)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
                    put(Telephony.Sms.THREAD_ID, threadId)
                    put(Telephony.Sms.SUBSCRIPTION_ID, SmsManager.getDefaultSmsSubscriptionId())
                }
            val messageUri =
                runCatching { resolver.insert(Telephony.Sms.CONTENT_URI, values) }.getOrNull()
                    ?: return@withContext SendOutcome.Failed(SendFailure.NOT_DEFAULT_SMS_APP)
            try {
                val parts = smsManager.divideMessage(body)
                val sentIntents =
                    ArrayList(
                        parts.indices.map { index ->
                            statusIntent(
                                MessageStatusReceiver.ACTION_SMS_SENT,
                                messageUri,
                                requestCode = index,
                            ) {
                                putExtra(MessageStatusReceiver.EXTRA_PART_INDEX, index)
                                putExtra(MessageStatusReceiver.EXTRA_PART_COUNT, parts.size)
                            }
                        },
                    )
                smsManager.sendMultipartTextMessage(address, null, parts, sentIntents, null)
                SendOutcome.Queued(threadId)
            } catch (e: Exception) {
                // IllegalArgumentException for an unusable address, SecurityException without
                // SEND_SMS, or a radio-side failure: the message never left, so mark it failed.
                Log.w(TAG, "SMS send failed: ${e.javaClass.simpleName}")
                markSmsFailed(messageUri, errorCode = SmsManager.RESULT_ERROR_GENERIC_FAILURE)
                SendOutcome.Failed(SendFailure.NO_SERVICE)
            }
        }

    override suspend fun sendMms(
        addresses: List<String>,
        body: String,
        attachments: List<OutgoingAttachment>,
    ): SendOutcome =
        withContext(dispatcher) {
            val smsManager = smsManager() ?: return@withContext SendOutcome.Failed(SendFailure.NO_SERVICE)
            val maxSize =
                runCatching { smsManager.carrierConfigValues.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE) }
                    .getOrNull()
                    ?.takeIf { it > 0 } ?: DEFAULT_MAX_MMS_SIZE

            val parts =
                when (val built = buildParts(body, attachments, maxSize)) {
                    is PartsResult.Failed -> return@withContext SendOutcome.Failed(built.reason)
                    is PartsResult.Built -> built.parts
                }
            val transactionId =
                "T" +
                    UUID
                        .randomUUID()
                        .toString()
                        .replace("-", "")
                        .take(TRANSACTION_ID_LENGTH)
            val allParts = listOf(MmsSmil.build(parts)) + parts
            val pdu = composer.composeSendReq(transactionId, addresses, subject = null, parts = allParts)

            val threadId =
                runCatching { Telephony.Threads.getOrCreateThreadId(context, addresses.toSet()) }.getOrNull()
                    ?: return@withContext SendOutcome.Failed(SendFailure.NOT_DEFAULT_SMS_APP)
            val messageUri =
                insertOutgoingMms(threadId, transactionId, addresses, allParts)
                    ?: return@withContext SendOutcome.Failed(SendFailure.NOT_DEFAULT_SMS_APP)

            val file = File(mmsDir(), "send-${UUID.randomUUID()}.pdu")
            try {
                file.writeBytes(pdu)
                val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val sentIntent =
                    statusIntent(MessageStatusReceiver.ACTION_MMS_SENT, messageUri, requestCode = 0) {
                        putExtra(MessageStatusReceiver.EXTRA_PDU_FILE, file.absolutePath)
                    }
                smsManager.sendMultimediaMessage(context, contentUri, null, null, sentIntent)
                SendOutcome.Queued(threadId)
            } catch (e: Exception) {
                Log.w(TAG, "MMS send failed: ${e.javaClass.simpleName}")
                file.delete()
                setMmsBox(messageUri, Telephony.Mms.MESSAGE_BOX_FAILED)
                SendOutcome.Failed(SendFailure.NO_SERVICE)
            }
        }

    private sealed interface PartsResult {
        data class Built(
            val parts: List<MmsPart>,
        ) : PartsResult

        data class Failed(
            val reason: SendFailure,
        ) : PartsResult
    }

    private fun buildParts(
        body: String,
        attachments: List<OutgoingAttachment>,
        maxSize: Int,
    ): PartsResult {
        val parts = mutableListOf<MmsPart>()
        val text = body.toByteArray(Charsets.UTF_8)
        // Headers, SMIL and part headers; generous so a carrier's limit is never exceeded.
        val budget = (maxSize * BUDGET_FRACTION).toInt() - text.size - OVERHEAD_BYTES
        val perAttachment = if (attachments.isEmpty()) 0 else budget / attachments.size
        attachments.forEachIndexed { index, attachment ->
            val raw = readBytes(attachment.uri) ?: return PartsResult.Failed(SendFailure.ATTACHMENT_UNREADABLE)
            val isCompressibleImage = attachment.contentType.startsWith("image/") && attachment.contentType != "image/gif"
            val (bytes, type) =
                if (isCompressibleImage && raw.size > perAttachment) {
                    (compressImage(raw, perAttachment) ?: return PartsResult.Failed(SendFailure.ATTACHMENT_TOO_LARGE)) to "image/jpeg"
                } else {
                    raw to attachment.contentType
                }
            if (bytes.size > perAttachment) return PartsResult.Failed(SendFailure.ATTACHMENT_TOO_LARGE)
            val extension = type.substringAfter('/').substringBefore(';').ifBlank { "bin" }
            val name = "${type.substringBefore('/')}_$index.$extension"
            parts += MmsPart(contentType = type, data = bytes, name = name, contentId = name.substringBefore('.'), contentLocation = name)
        }
        if (text.isNotEmpty()) {
            parts +=
                MmsPart(
                    contentType = MmsHeaders.CONTENT_TYPE_TEXT,
                    data = text,
                    name = "text_0.txt",
                    contentId = "text_0",
                    contentLocation = "text_0.txt",
                    charset = MmsCharsets.UTF_8,
                )
        }
        return PartsResult.Built(parts)
    }

    private fun readBytes(uri: String): ByteArray? =
        runCatching {
            resolver.openInputStream(uri.toUri())?.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(BUFFER_SIZE)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    // Far beyond any carrier limit; stop rather than risk running out of memory.
                    if (total > MAX_ATTACHMENT_READ) return@runCatching null
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
        }.getOrNull()

    /** Re-encodes an image as JPEG, shrinking quality then dimensions until it fits [maxBytes]. */
    private fun compressImage(
        bytes: ByteArray,
        maxBytes: Int,
    ): ByteArray? {
        if (maxBytes <= 0) return null
        var maxDimension = INITIAL_MAX_DIMENSION
        while (maxDimension >= MIN_DIMENSION) {
            val bitmap =
                runCatching {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                        val largest = maxOf(info.size.width, info.size.height)
                        if (largest > maxDimension) {
                            val scale = maxDimension.toFloat() / largest
                            decoder.setTargetSize(
                                (info.size.width * scale).toInt().coerceAtLeast(1),
                                (info.size.height * scale).toInt().coerceAtLeast(1),
                            )
                        }
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                }.getOrNull() ?: return null
            for (quality in JPEG_QUALITIES) {
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                if (out.size() <= maxBytes) {
                    bitmap.recycle()
                    return out.toByteArray()
                }
            }
            bitmap.recycle()
            maxDimension = (maxDimension * DIMENSION_STEP).toInt()
        }
        return null
    }

    private fun insertOutgoingMms(
        threadId: Long,
        transactionId: String,
        addresses: List<String>,
        parts: List<MmsPart>,
    ): Uri? {
        val values =
            ContentValues().apply {
                put(Telephony.Mms.THREAD_ID, threadId)
                put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000)
                put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_OUTBOX)
                put(Telephony.Mms.READ, 1)
                put(Telephony.Mms.SEEN, 1)
                put(Telephony.Mms.MESSAGE_TYPE, MmsHeaders.MESSAGE_TYPE_SEND_REQ)
                put(Telephony.Mms.MMS_VERSION, MmsHeaders.MMS_VERSION_1_2)
                put(Telephony.Mms.CONTENT_TYPE, MmsHeaders.CONTENT_TYPE_MULTIPART_RELATED)
                put(Telephony.Mms.TRANSACTION_ID, transactionId)
                put(Telephony.Mms.MESSAGE_CLASS, "personal")
                put(Telephony.Mms.PRIORITY, MmsHeaders.PRIORITY_NORMAL)
                put(Telephony.Mms.DELIVERY_REPORT, MmsHeaders.VALUE_NO)
                put(Telephony.Mms.READ_REPORT, MmsHeaders.VALUE_NO)
                put(Telephony.Mms.SUBSCRIPTION_ID, SmsManager.getDefaultSmsSubscriptionId())
            }
        val messageUri = runCatching { resolver.insert(Telephony.Mms.CONTENT_URI, values) }.getOrNull() ?: return null
        val messageId = ContentUris.parseId(messageUri)
        parts.forEachIndexed { index, part -> store.insertPart(messageId, part, index) }
        store.insertAddress(messageId, INSERT_ADDRESS_TOKEN, ContentProviderMessageStore.ADDR_TYPE_FROM)
        addresses.forEach { store.insertAddress(messageId, it, ContentProviderMessageStore.ADDR_TYPE_TO) }
        return messageUri
    }

    /**
     * One part of a multipart SMS was reported on. Returns true when the message has just failed,
     * so the caller can tell the user.
     */
    suspend fun onSmsSent(
        messageUri: Uri,
        resultCode: Int,
        partIndex: Int,
        partCount: Int,
    ): Boolean =
        withContext(dispatcher) {
            if (resultCode != Activity.RESULT_OK) {
                markSmsFailed(messageUri, resultCode)
                return@withContext true
            }
            if (partIndex == partCount - 1) {
                val values = ContentValues().apply { put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT) }
                // An earlier part may already have failed the message; never overwrite that.
                runCatching {
                    resolver.update(messageUri, values, "${Telephony.Sms.TYPE} != ?", arrayOf(Telephony.Sms.MESSAGE_TYPE_FAILED.toString()))
                }
            }
            false
        }

    /** Returns true when the MMS failed. */
    suspend fun onMmsSent(
        messageUri: Uri,
        resultCode: Int,
        response: ByteArray?,
        pduFile: File?,
    ): Boolean =
        withContext(dispatcher) {
            pduFile?.delete()
            val conf = response?.let(parser::parse) as? MmsPdu.SendConf
            val accepted = resultCode == Activity.RESULT_OK && (conf == null || conf.isOk)
            val values =
                ContentValues().apply {
                    put(Telephony.Mms.MESSAGE_BOX, if (accepted) Telephony.Mms.MESSAGE_BOX_SENT else Telephony.Mms.MESSAGE_BOX_FAILED)
                    conf?.messageId?.let { put(Telephony.Mms.MESSAGE_ID, it) }
                }
            runCatching { resolver.update(messageUri, values, null, null) }
            !accepted
        }

    private fun markSmsFailed(
        messageUri: Uri,
        errorCode: Int,
    ) {
        val values =
            ContentValues().apply {
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED)
                put(Telephony.Sms.ERROR_CODE, errorCode)
            }
        runCatching { resolver.update(messageUri, values, null, null) }
    }

    private fun setMmsBox(
        messageUri: Uri,
        box: Int,
    ) {
        runCatching { resolver.update(messageUri, ContentValues().apply { put(Telephony.Mms.MESSAGE_BOX, box) }, null, null) }
    }

    private fun statusIntent(
        action: String,
        messageUri: Uri,
        requestCode: Int,
        extras: Intent.() -> Unit,
    ): PendingIntent {
        // The message URI as data makes every message's intent distinct; the request code
        // distinguishes the parts of one message.
        val intent = Intent(action, messageUri, context, MessageStatusReceiver::class.java).apply(extras)
        // Mutable so the platform can fill in the MMSC response; explicit, so nothing else can
        // receive it.
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }

    private fun mmsDir(): File = File(context.cacheDir, MMS_CACHE_DIR).apply { mkdirs() }

    private fun smsManager(): SmsManager? = runCatching { context.getSystemService(SmsManager::class.java) }.getOrNull()

    companion object {
        private const val TAG = "MessageSender"
        const val MMS_CACHE_DIR = "mms"

        /** The MMSC replaces this with the sender's own number. */
        private const val INSERT_ADDRESS_TOKEN = "insert-address-token"

        private const val DEFAULT_MAX_MMS_SIZE = 300 * 1024
        private const val BUDGET_FRACTION = 0.9
        private const val OVERHEAD_BYTES = 4 * 1024
        private const val MAX_ATTACHMENT_READ = 32 * 1024 * 1024
        private const val BUFFER_SIZE = 64 * 1024
        private const val TRANSACTION_ID_LENGTH = 12
        private const val INITIAL_MAX_DIMENSION = 1600
        private const val MIN_DIMENSION = 200
        private const val DIMENSION_STEP = 0.75
        private val JPEG_QUALITIES = listOf(85, 70, 55, 40)
    }
}
