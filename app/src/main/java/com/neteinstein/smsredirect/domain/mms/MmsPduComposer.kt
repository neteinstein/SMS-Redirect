package com.neteinstein.smsredirect.domain.mms

import java.io.ByteArrayOutputStream

/**
 * Encodes the PDUs this app sends: M-Send.req (an outgoing MMS), and the M-Acknowledge.ind /
 * M-NotifyResp.ind that tell the carrier's MMSC a downloaded message arrived.
 *
 * The output is what `SmsManager.sendMultimediaMessage` expects; [MmsPduParser] reads it back, and
 * the round trip is what the unit tests pin down.
 */
class MmsPduComposer {
    /**
     * @param recipients phone numbers or email addresses, at least one.
     * @param parts the body. A SMIL part, if present, must come first and becomes the `start` part.
     */
    fun composeSendReq(
        transactionId: String,
        recipients: List<String>,
        subject: String?,
        parts: List<MmsPart>,
    ): ByteArray {
        require(recipients.isNotEmpty()) { "An MMS needs at least one recipient" }
        require(parts.isNotEmpty()) { "An MMS needs at least one part" }
        val out = WspWriter()
        out.header(MmsHeaders.MESSAGE_TYPE) { byte(MmsHeaders.MESSAGE_TYPE_SEND_REQ) }
        out.header(MmsHeaders.TRANSACTION_ID) { textString(transactionId) }
        out.header(MmsHeaders.MMS_VERSION) { shortInteger(MmsHeaders.MMS_VERSION_1_2) }
        // Insert-address-token: the MMSC fills in this phone's own number.
        out.header(MmsHeaders.FROM) {
            valueLength(1)
            byte(MmsHeaders.FROM_INSERT_ADDRESS)
        }
        recipients.forEach { recipient ->
            out.header(MmsHeaders.TO) { encodedString(toMmsAddress(recipient)) }
        }
        if (!subject.isNullOrBlank()) out.header(MmsHeaders.SUBJECT) { encodedString(subject) }
        out.header(MmsHeaders.MESSAGE_CLASS) { byte(MmsHeaders.MESSAGE_CLASS_PERSONAL) }
        out.header(MmsHeaders.PRIORITY) { byte(MmsHeaders.PRIORITY_NORMAL) }
        out.header(MmsHeaders.DELIVERY_REPORT) { byte(MmsHeaders.VALUE_NO) }
        out.header(MmsHeaders.READ_REPORT) { byte(MmsHeaders.VALUE_NO) }

        val smil = parts.firstOrNull()?.takeIf { it.isSmil }
        out.header(MmsHeaders.CONTENT_TYPE) {
            if (smil != null) {
                contentType(
                    MmsHeaders.CONTENT_TYPE_MULTIPART_RELATED,
                    WspWriter()
                        .apply {
                            shortInteger(MmsHeaders.PARAM_TYPE_MULTIPART)
                            textString(MmsHeaders.CONTENT_TYPE_SMIL)
                            shortInteger(MmsHeaders.PARAM_START)
                            textString("<${smil.contentId ?: SMIL_CONTENT_ID}>")
                        }.toByteArray(),
                )
            } else {
                contentType(MmsHeaders.CONTENT_TYPE_MULTIPART_MIXED, ByteArray(0))
            }
        }
        out.multipart(parts)
        return out.toByteArray()
    }

    /** M-Acknowledge.ind: sent after a successful download, so the MMSC stops holding the message. */
    fun composeAcknowledgeInd(transactionId: String): ByteArray {
        val out = WspWriter()
        out.header(MmsHeaders.MESSAGE_TYPE) { byte(MmsHeaders.MESSAGE_TYPE_ACKNOWLEDGE_IND) }
        out.header(MmsHeaders.TRANSACTION_ID) { textString(transactionId) }
        out.header(MmsHeaders.MMS_VERSION) { shortInteger(MmsHeaders.MMS_VERSION_1_2) }
        // No delivery report back to the sender: that would tell them when the message was read.
        out.header(MmsHeaders.REPORT_ALLOWED) { byte(MmsHeaders.VALUE_NO) }
        return out.toByteArray()
    }

    private fun WspWriter.multipart(parts: List<MmsPart>) {
        uintvar(parts.size)
        parts.forEach { part ->
            val headers =
                WspWriter()
                    .apply {
                        val params =
                            WspWriter()
                                .apply {
                                    part.charset?.let {
                                        shortInteger(MmsHeaders.PARAM_CHARSET)
                                        integerValue(it.toLong())
                                    }
                                    part.name?.let {
                                        shortInteger(MmsHeaders.PARAM_NAME)
                                        textString(it)
                                    }
                                }.toByteArray()
                        contentType(part.contentType, params)
                        part.contentLocation?.let {
                            shortInteger(MmsHeaders.PART_CONTENT_LOCATION)
                            textString(it)
                        }
                        part.contentId?.let {
                            shortInteger(MmsHeaders.PART_CONTENT_ID)
                            quotedString("<$it>")
                        }
                    }.toByteArray()
            uintvar(headers.size)
            uintvar(part.data.size)
            bytes(headers)
            bytes(part.data)
        }
    }

    companion object {
        const val SMIL_CONTENT_ID = "smil"

        /** Phone numbers get the `/TYPE=PLMN` suffix the MMSC expects; email addresses do not. */
        fun toMmsAddress(recipient: String): String {
            val trimmed = recipient.trim()
            if (trimmed.contains('@')) return trimmed
            val digits = trimmed.filterIndexed { index, c -> c.isDigit() || (c == '+' && index == 0) }
            return "$digits/TYPE=PLMN"
        }
    }
}

/** Builds the SMIL layout part most phones need to show an MMS's image above its text. */
object MmsSmil {
    fun build(parts: List<MmsPart>): MmsPart {
        val body =
            buildString {
                append("<smil><head><layout><root-layout/>")
                append("<region id=\"Image\" fit=\"meet\" top=\"0\" left=\"0\" height=\"80%\" width=\"100%\"/>")
                append("<region id=\"Text\" top=\"80%\" left=\"0\" height=\"20%\" width=\"100%\"/>")
                append("</layout></head><body>")
                parts.forEach { part ->
                    val src = part.contentLocation ?: part.name ?: return@forEach
                    append("<par dur=\"5000ms\">")
                    when {
                        part.isText -> append("<text src=\"$src\" region=\"Text\"/>")
                        part.contentType.startsWith("image/") -> append("<img src=\"$src\" region=\"Image\"/>")
                        part.contentType.startsWith("video/") -> append("<video src=\"$src\" region=\"Image\"/>")
                        part.contentType.startsWith("audio/") -> append("<audio src=\"$src\"/>")
                        else -> append("<ref src=\"$src\"/>")
                    }
                    append("</par>")
                }
                append("</body></smil>")
            }
        return MmsPart(
            contentType = MmsHeaders.CONTENT_TYPE_SMIL,
            data = body.toByteArray(Charsets.UTF_8),
            name = "smil.xml",
            contentId = MmsPduComposer.SMIL_CONTENT_ID,
            contentLocation = "smil.xml",
            charset = MmsCharsets.UTF_8,
        )
    }
}

internal class WspWriter {
    private val out = ByteArrayOutputStream()

    fun toByteArray(): ByteArray = out.toByteArray()

    fun byte(value: Int) = out.write(value and 0xFF)

    fun bytes(value: ByteArray) = out.write(value)

    fun header(
        field: Int,
        value: WspWriter.() -> Unit,
    ) {
        shortInteger(field)
        value()
    }

    fun shortInteger(value: Int) {
        require(value in 0..127) { "Short integer out of range: $value" }
        byte(0x80 or value)
    }

    fun longInteger(value: Long) {
        var remaining = value
        val octets = ArrayDeque<Int>()
        do {
            octets.addFirst((remaining and 0xFF).toInt())
            remaining = remaining ushr 8
        } while (remaining != 0L)
        byte(octets.size)
        octets.forEach { byte(it) }
    }

    fun integerValue(value: Long) = if (value in 0..127) shortInteger(value.toInt()) else longInteger(value)

    fun uintvar(value: Int) {
        require(value >= 0)
        val groups = ArrayDeque<Int>()
        var remaining = value
        do {
            groups.addFirst(remaining and 0x7F)
            remaining = remaining ushr 7
        } while (remaining != 0)
        groups.forEachIndexed { index, group -> byte(if (index < groups.size - 1) group or 0x80 else group) }
    }

    fun valueLength(length: Int) {
        if (length <= 30) {
            byte(length)
        } else {
            byte(31)
            uintvar(length)
        }
    }

    fun textString(value: String) {
        val encoded = value.toByteArray(Charsets.ISO_8859_1)
        if (encoded.isNotEmpty() && (encoded[0].toInt() and 0xFF) >= 128) byte(127)
        bytes(encoded)
        byte(0)
    }

    fun quotedString(value: String) {
        byte(34)
        bytes(value.toByteArray(Charsets.ISO_8859_1))
        byte(0)
    }

    /** Plain text when ASCII; otherwise value-length + UTF-8 charset + text. */
    fun encodedString(value: String) {
        if (value.all { it.code < 128 }) {
            textString(value)
            return
        }
        val text = value.toByteArray(Charsets.UTF_8)
        val needsQuote = text.isNotEmpty() && (text[0].toInt() and 0xFF) >= 128
        // charset octet + optional quote + text + NUL
        valueLength(1 + (if (needsQuote) 1 else 0) + text.size + 1)
        shortInteger(MmsCharsets.UTF_8)
        if (needsQuote) byte(127)
        bytes(text)
        byte(0)
    }

    /** Content-general-form when there are parameters, otherwise the compact form. */
    fun contentType(
        mediaType: String,
        params: ByteArray,
    ) {
        val code = MmsHeaders.wellKnownContentTypeCode(mediaType)
        val media =
            WspWriter()
                .apply {
                    if (code != null) shortInteger(code) else textString(mediaType)
                }.toByteArray()
        if (params.isEmpty()) {
            bytes(media)
            return
        }
        valueLength(media.size + params.size)
        bytes(media)
        bytes(params)
    }
}
