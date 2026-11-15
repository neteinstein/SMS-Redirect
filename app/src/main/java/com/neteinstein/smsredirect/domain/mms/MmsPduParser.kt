package com.neteinstein.smsredirect.domain.mms

/** Thrown internally for a truncated or malformed PDU; [MmsPduParser.parse] turns it into null. */
class MmsPduException(
    message: String,
) : Exception(message)

/**
 * Decodes binary MMS PDUs (OMA-TS-MMS_ENC, WSP encoding). Lenient where carriers are known to be
 * sloppy -- unknown headers are skipped, application headers are tolerated -- and strict about
 * bounds: a truncated PDU yields null, never an out-of-range read.
 */
class MmsPduParser {
    fun parse(bytes: ByteArray): MmsPdu? =
        try {
            parseOrThrow(bytes)
        } catch (_: MmsPduException) {
            null
        }

    private fun parseOrThrow(bytes: ByteArray): MmsPdu {
        val reader = WspReader(bytes)
        val headers = Headers()
        var contentType: ContentType? = null

        while (reader.hasMore()) {
            val octet = reader.readByte()
            if (octet < 0x80) {
                // Application-header: Token-text name, then a text value. Skipped.
                reader.unread()
                reader.readTextString()
                reader.skipValue()
                continue
            }
            when (octet and 0x7F) {
                MmsHeaders.MESSAGE_TYPE -> {
                    headers.messageType = reader.readByte()
                }

                MmsHeaders.TRANSACTION_ID -> {
                    headers.transactionId = reader.readTextString()
                }

                MmsHeaders.MESSAGE_ID -> {
                    headers.messageId = reader.readTextString()
                }

                MmsHeaders.CONTENT_LOCATION -> {
                    headers.contentLocation = reader.readTextString()
                }

                MmsHeaders.SUBJECT -> {
                    headers.subject = reader.readEncodedString()
                }

                MmsHeaders.FROM -> {
                    headers.from = readFrom(reader)
                }

                MmsHeaders.TO -> {
                    headers.to += cleanAddress(reader.readEncodedString())
                }

                MmsHeaders.CC -> {
                    headers.cc += cleanAddress(reader.readEncodedString())
                }

                MmsHeaders.DATE -> {
                    headers.date = reader.readLongInteger()
                }

                MmsHeaders.MESSAGE_SIZE -> {
                    headers.messageSize = reader.readLongInteger()
                }

                MmsHeaders.RESPONSE_STATUS -> {
                    headers.responseStatus = reader.readByte()
                }

                MmsHeaders.STATUS -> {
                    headers.status = reader.readByte()
                }

                MmsHeaders.READ_STATUS -> {
                    headers.readStatus = reader.readByte()
                }

                MmsHeaders.READ_REPORT -> {
                    headers.readReport = reader.readByte()
                }

                MmsHeaders.EXPIRY -> {
                    headers.expiry = readExpiry(reader)
                }

                MmsHeaders.CONTENT_TYPE -> {
                    contentType = reader.readContentType()
                    // Content-Type is always the last header; the body follows.
                    break
                }

                else -> {
                    reader.skipValue()
                }
            }
        }

        return when (headers.messageType) {
            MmsHeaders.MESSAGE_TYPE_NOTIFICATION_IND -> {
                MmsPdu.NotificationInd(
                    transactionId = headers.transactionId,
                    from = headers.from,
                    subject = headers.subject,
                    contentLocation =
                        headers.contentLocation?.takeIf { it.isNotBlank() }
                            ?: throw MmsPduException("Notification without a content location"),
                    messageSize = headers.messageSize ?: 0L,
                    expirySeconds = headers.expiry,
                )
            }

            MmsHeaders.MESSAGE_TYPE_RETRIEVE_CONF -> {
                val type = contentType ?: throw MmsPduException("Retrieve.conf without a content type")
                MmsPdu.RetrieveConf(
                    transactionId = headers.transactionId,
                    messageId = headers.messageId,
                    from = headers.from,
                    to = headers.to,
                    cc = headers.cc,
                    subject = headers.subject,
                    dateEpochSeconds = headers.date,
                    contentType = type.mediaType,
                    parts = readBody(reader, type),
                    readReportRequested = headers.readReport == MmsHeaders.VALUE_YES,
                )
            }

            MmsHeaders.MESSAGE_TYPE_DELIVERY_IND -> {
                MmsPdu.DeliveryInd(
                    messageId = headers.messageId ?: throw MmsPduException("Delivery.ind without a message id"),
                    to = headers.to.firstOrNull(),
                    status = headers.status ?: throw MmsPduException("Delivery.ind without a status"),
                    dateEpochSeconds = headers.date,
                )
            }

            MmsHeaders.MESSAGE_TYPE_READ_ORIG_IND -> {
                MmsPdu.ReadOrigInd(
                    messageId = headers.messageId ?: throw MmsPduException("Read-Orig.ind without a message id"),
                    from = headers.from,
                    readStatus = headers.readStatus ?: throw MmsPduException("Read-Orig.ind without a read status"),
                    dateEpochSeconds = headers.date,
                )
            }

            MmsHeaders.MESSAGE_TYPE_SEND_REQ -> {
                val type = contentType ?: throw MmsPduException("Send.req without a content type")
                MmsPdu.SendReq(
                    transactionId = headers.transactionId,
                    to = headers.to,
                    subject = headers.subject,
                    contentType = type.mediaType,
                    parts = readBody(reader, type),
                )
            }

            MmsHeaders.MESSAGE_TYPE_SEND_CONF -> {
                MmsPdu.SendConf(
                    transactionId = headers.transactionId,
                    responseStatus = headers.responseStatus ?: -1,
                    messageId = headers.messageId,
                )
            }

            null -> {
                throw MmsPduException("No message type")
            }

            else -> {
                MmsPdu.Other(headers.transactionId, headers.messageType ?: -1)
            }
        }
    }

    private fun readBody(
        reader: WspReader,
        type: ContentType,
    ): List<MmsPart> {
        if (!reader.hasMore()) return emptyList()
        if (!type.mediaType.startsWith("application/vnd.wap.multipart", ignoreCase = true) &&
            !type.mediaType.startsWith("multipart/", ignoreCase = true)
        ) {
            // A single-part message: the whole remainder is the one part.
            return listOf(MmsPart(type.mediaType, reader.readRemaining(), charset = type.charset, name = type.name))
        }
        val count = reader.readUintvar()
        val parts = ArrayList<MmsPart>(count.coerceAtMost(MAX_PARTS))
        repeat(count.coerceAtMost(MAX_PARTS)) {
            val headersLength = reader.readUintvar()
            val dataLength = reader.readUintvar()
            val headersEnd = reader.position + headersLength
            if (headersEnd > reader.size) throw MmsPduException("Part headers overrun")

            val partType = reader.readContentType()
            var contentLocation: String? = null
            var contentId: String? = null
            var dispositionFileName: String? = null
            while (reader.position < headersEnd) {
                val octet = reader.readByte()
                if (octet < 0x80) {
                    reader.unread()
                    reader.readTextString()
                    reader.skipValue()
                    continue
                }
                when (octet and 0x7F) {
                    MmsHeaders.PART_CONTENT_LOCATION -> {
                        contentLocation = reader.readTextString()
                    }

                    MmsHeaders.PART_CONTENT_ID -> {
                        contentId = reader.readTextString().trim('"', '<', '>')
                    }

                    MmsHeaders.PART_CONTENT_DISPOSITION_1_1, MmsHeaders.PART_CONTENT_DISPOSITION_1_4 -> {
                        dispositionFileName = reader.readContentDispositionFileName()
                    }

                    else -> {
                        reader.skipValue()
                    }
                }
            }
            reader.position = headersEnd
            val data = reader.readBytes(dataLength)
            parts +=
                MmsPart(
                    contentType = partType.mediaType,
                    data = data,
                    name = partType.name,
                    fileName = dispositionFileName ?: partType.fileName,
                    contentId = contentId,
                    contentLocation = contentLocation,
                    charset = partType.charset,
                )
        }
        return parts
    }

    private fun readFrom(reader: WspReader): String? {
        val length = reader.readValueLength()
        val end = reader.position + length
        val token = reader.readByte()
        val address =
            if (token == MmsHeaders.FROM_ADDRESS_PRESENT) cleanAddress(reader.readEncodedString()) else null
        reader.position = end
        return address
    }

    /** Absolute expiry as epoch seconds, or null for a relative one (not needed by this app). */
    private fun readExpiry(reader: WspReader): Long? {
        val length = reader.readValueLength()
        val end = reader.position + length
        val token = reader.readByte()
        val value = if (token == ABSOLUTE_TOKEN) reader.readLongInteger() else null
        reader.position = end
        return value
    }

    private class Headers {
        var messageType: Int? = null
        var transactionId: String? = null
        var messageId: String? = null
        var contentLocation: String? = null
        var subject: String? = null
        var from: String? = null
        val to = mutableListOf<String>()
        val cc = mutableListOf<String>()
        var date: Long? = null
        var messageSize: Long? = null
        var responseStatus: Int? = null
        var expiry: Long? = null
        var status: Int? = null
        var readStatus: Int? = null
        var readReport: Int? = null
    }

    companion object {
        private const val ABSOLUTE_TOKEN = 128

        // A real MMS has a handful of parts; this only stops a corrupt count from allocating GBs.
        private const val MAX_PARTS = 64

        /** "+15551234567/TYPE=PLMN" -> "+15551234567". Email addresses pass through. */
        fun cleanAddress(raw: String): String = raw.substringBefore("/TYPE=", raw).trim()
    }
}

internal data class ContentType(
    val mediaType: String,
    val charset: Int? = null,
    val name: String? = null,
    val fileName: String? = null,
)

/** Cursor over a WSP-encoded byte array. Every read is bounds-checked. */
internal class WspReader(
    private val bytes: ByteArray,
) {
    var position: Int = 0
        set(value) {
            if (value < 0 || value > bytes.size) throw MmsPduException("Seek out of range")
            field = value
        }

    val size: Int get() = bytes.size

    fun hasMore(): Boolean = position < bytes.size

    fun readByte(): Int {
        if (position >= bytes.size) throw MmsPduException("Unexpected end of PDU")
        return bytes[position++].toInt() and 0xFF
    }

    private fun peekByte(): Int {
        if (position >= bytes.size) throw MmsPduException("Unexpected end of PDU")
        return bytes[position].toInt() and 0xFF
    }

    fun unread() {
        position -= 1
    }

    fun readBytes(count: Int): ByteArray {
        if (count < 0 || position + count > bytes.size) throw MmsPduException("Data overrun")
        return bytes.copyOfRange(position, position + count).also { position += count }
    }

    fun readRemaining(): ByteArray = readBytes(bytes.size - position)

    fun readUintvar(): Int {
        var value = 0L
        repeat(5) {
            val octet = readByte()
            value = (value shl 7) or (octet and 0x7F).toLong()
            if (octet and 0x80 == 0) {
                if (value > Int.MAX_VALUE) throw MmsPduException("Uintvar too large")
                return value.toInt()
            }
        }
        throw MmsPduException("Uintvar too long")
    }

    /** Value-length: a short length (0-30), or 31 followed by a uintvar. */
    fun readValueLength(): Int {
        val octet = readByte()
        return when {
            octet <= 30 -> octet
            octet == 31 -> readUintvar()
            else -> throw MmsPduException("Not a value length")
        }
    }

    fun readLongInteger(): Long {
        val length = readByte()
        if (length > 8) throw MmsPduException("Long integer too long")
        var value = 0L
        repeat(length) { value = (value shl 8) or readByte().toLong() }
        return value
    }

    /** Integer-value: a short integer (high bit set) or a long integer. */
    fun readIntegerValue(): Long {
        val octet = peekByte()
        return if (octet and 0x80 != 0) {
            position++
            (octet and 0x7F).toLong()
        } else {
            readLongInteger()
        }
    }

    /** Text-string, Token-text or Quoted-string: optional quote octet, text, NUL. */
    fun readTextString(): String = String(readRawText(), Charsets.ISO_8859_1)

    private fun readRawText(): ByteArray {
        if (peekByte() == QUOTE || peekByte() == QUOTED_STRING) position++
        val start = position
        while (readByte() != 0) {
            // advance to the terminating NUL
        }
        return bytes.copyOfRange(start, position - 1)
    }

    /** Encoded-string-value: either a plain text string, or value-length + charset + text. */
    fun readEncodedString(): String {
        val octet = peekByte()
        if (octet > 31) return String(readRawText(), Charsets.UTF_8)
        val length = readValueLength()
        val end = position + length
        if (end > bytes.size) throw MmsPduException("Encoded string overrun")
        val charset = readIntegerValue().toInt()
        val text = MmsCharsets.decode(readRawText(), charset)
        position = end
        return text
    }

    /** Skips one header/parameter value of any WSP shape. */
    fun skipValue() {
        val octet = peekByte()
        when {
            octet <= 30 -> {
                position = checkedEnd(1 + octet)
            }

            octet == 31 -> {
                position++
                val length = readUintvar()
                position = checkedEnd(length)
            }

            octet < 128 -> {
                readRawText()
            }

            else -> {
                position++
            }
        }
    }

    private fun checkedEnd(advance: Int): Int {
        val end = position + advance
        if (end > bytes.size) throw MmsPduException("Value overrun")
        return end
    }

    fun readContentType(): ContentType {
        val octet = peekByte()
        if (octet >= 0x80) {
            position++
            return ContentType(wellKnownType(octet and 0x7F))
        }
        if (octet > 31) return ContentType(readTextString())

        val length = readValueLength()
        val end = position + length
        if (end > bytes.size) throw MmsPduException("Content type overrun")
        val media =
            when {
                peekByte() >= 0x80 -> wellKnownType(readByte() and 0x7F)
                peekByte() <= 30 -> wellKnownType(readLongInteger().toInt())
                else -> readTextString()
            }
        var type = ContentType(media)
        while (position < end) {
            type = readParameter(type)
        }
        position = end
        return type
    }

    /** Content-Disposition: value-length, disposition token, parameters. Returns the filename. */
    fun readContentDispositionFileName(): String? {
        val octet = peekByte()
        if (octet > 30 && octet != 31) {
            skipValue()
            return null
        }
        val length = readValueLength()
        val end = position + length
        if (end > bytes.size) throw MmsPduException("Disposition overrun")
        if (position < end) {
            if (peekByte() >= 0x80) position++ else readTextString()
        }
        var type = ContentType("")
        while (position < end) {
            type = readParameter(type)
        }
        position = end
        return type.fileName ?: type.name
    }

    private fun readParameter(type: ContentType): ContentType {
        val octet = peekByte()
        if (octet < 0x80) {
            // Untyped parameter: Token-text name, then an integer or text value.
            val name = readTextString().lowercase()
            val value =
                if (peekByte() >= 0x80 || peekByte() <= 30) {
                    readIntegerValue().toString()
                } else {
                    readTextString()
                }
            return when (name) {
                "charset" -> type.copy(charset = value.toIntOrNull() ?: type.charset)
                "name" -> type.copy(name = value)
                "filename" -> type.copy(fileName = value)
                else -> type
            }
        }
        position++
        return when (octet and 0x7F) {
            MmsHeaders.PARAM_CHARSET -> {
                if (peekByte() in 32..127) {
                    readTextString()
                    type
                } else {
                    type.copy(charset = readIntegerValue().toInt())
                }
            }

            MmsHeaders.PARAM_NAME, MmsHeaders.PARAM_NAME_1_4 -> {
                type.copy(name = readTextString())
            }

            MmsHeaders.PARAM_FILENAME, MmsHeaders.PARAM_FILENAME_1_4 -> {
                type.copy(fileName = readTextString())
            }

            else -> {
                skipValue()
                type
            }
        }
    }

    private fun wellKnownType(code: Int): String = MmsHeaders.WELL_KNOWN_CONTENT_TYPES.getOrNull(code) ?: "application/octet-stream"

    private companion object {
        const val QUOTE = 127
        const val QUOTED_STRING = 34
    }
}
