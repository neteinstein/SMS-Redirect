package com.neteinstein.smsredirect.domain.mms

/*
 * The subset of the OMA MMS encapsulation format (OMA-TS-MMS_ENC, binary WSP encoding) this app
 * reads and writes. Android hands the default SMS app raw PDUs -- the push that announces an MMS,
 * the downloaded message, the send confirmation -- and expects raw PDUs back when it sends, but
 * the platform's own PDU classes are hidden API. So this is a small, pure-Kotlin codec: no
 * Android types, fully unit-tested, and nothing in it ever logs a message's content.
 */

/** A decoded MMS PDU. Only the message types the app acts on get their own class. */
sealed interface MmsPdu {
    val transactionId: String?

    /** M-Notification.ind: "an MMS is waiting at [contentLocation]". Arrives via WAP push. */
    data class NotificationInd(
        override val transactionId: String?,
        val from: String?,
        val subject: String?,
        val contentLocation: String,
        val messageSize: Long,
        val expirySeconds: Long?,
    ) : MmsPdu

    /** M-Retrieve.conf: the downloaded message itself. */
    data class RetrieveConf(
        override val transactionId: String?,
        val messageId: String?,
        val from: String?,
        val to: List<String>,
        val cc: List<String>,
        val subject: String?,
        val dateEpochSeconds: Long?,
        val contentType: String,
        val parts: List<MmsPart>,
    ) : MmsPdu {
        /** The concatenated text parts, which is what a conversation view and a notification show. */
        val text: String
            get() = parts.filter { it.isText }.joinToString(separator = "\n") { it.textOrEmpty() }.trim()
    }

    /** M-Send.req: an outgoing message. Parsed back mostly so the composer can be tested. */
    data class SendReq(
        override val transactionId: String?,
        val to: List<String>,
        val subject: String?,
        val contentType: String,
        val parts: List<MmsPart>,
    ) : MmsPdu

    /** M-Send.conf: the MMSC's answer to a send. [responseStatus] 128 means accepted. */
    data class SendConf(
        override val transactionId: String?,
        val responseStatus: Int,
        val messageId: String?,
    ) : MmsPdu {
        val isOk: Boolean get() = responseStatus == MmsHeaders.RESPONSE_STATUS_OK
    }

    /** Any other message type, kept only so the caller can tell what arrived. */
    data class Other(
        override val transactionId: String?,
        val messageType: Int,
    ) : MmsPdu
}

/** One body part of a multipart MMS: text, an image, a SMIL layout, ... */
data class MmsPart(
    val contentType: String,
    val data: ByteArray,
    val name: String? = null,
    val fileName: String? = null,
    val contentId: String? = null,
    val contentLocation: String? = null,
    /** IANA MIBenum; 106 is UTF-8. */
    val charset: Int? = null,
) {
    val isText: Boolean get() = contentType.equals("text/plain", ignoreCase = true)
    val isSmil: Boolean get() = contentType.equals(MmsHeaders.CONTENT_TYPE_SMIL, ignoreCase = true)

    fun textOrEmpty(): String = MmsCharsets.decode(data, charset)

    // ByteArray has identity equality; compare contents so parts are usable as values in tests.
    override fun equals(other: Any?): Boolean =
        other is MmsPart &&
            contentType == other.contentType &&
            data.contentEquals(other.data) &&
            name == other.name &&
            fileName == other.fileName &&
            contentId == other.contentId &&
            contentLocation == other.contentLocation &&
            charset == other.charset

    override fun hashCode(): Int = 31 * contentType.hashCode() + data.contentHashCode()
}

/** Field codes and well-known values from OMA-TS-MMS_ENC and WAP-230-WSP. */
object MmsHeaders {
    const val BCC = 0x01
    const val CC = 0x02
    const val CONTENT_LOCATION = 0x03
    const val CONTENT_TYPE = 0x04
    const val DATE = 0x05
    const val DELIVERY_REPORT = 0x06
    const val EXPIRY = 0x08
    const val FROM = 0x09
    const val MESSAGE_CLASS = 0x0A
    const val MESSAGE_ID = 0x0B
    const val MESSAGE_TYPE = 0x0C
    const val MMS_VERSION = 0x0D
    const val MESSAGE_SIZE = 0x0E
    const val PRIORITY = 0x0F
    const val READ_REPORT = 0x10
    const val REPORT_ALLOWED = 0x11
    const val RESPONSE_STATUS = 0x12
    const val STATUS = 0x15
    const val SUBJECT = 0x16
    const val TO = 0x17
    const val TRANSACTION_ID = 0x18

    // The values below are complete octets as they appear on the wire (a short integer already
    // carrying its high bit), so they are written with WspWriter.byte(), not shortInteger().
    const val MESSAGE_TYPE_SEND_REQ = 128
    const val MESSAGE_TYPE_SEND_CONF = 129
    const val MESSAGE_TYPE_NOTIFICATION_IND = 130
    const val MESSAGE_TYPE_NOTIFYRESP_IND = 131
    const val MESSAGE_TYPE_RETRIEVE_CONF = 132
    const val MESSAGE_TYPE_ACKNOWLEDGE_IND = 133

    /** MMS 1.2, encoded as a short integer: major in bits 4-6, minor in bits 0-3. */
    const val MMS_VERSION_1_2 = 0x12

    const val VALUE_YES = 128
    const val VALUE_NO = 129
    const val MESSAGE_CLASS_PERSONAL = 128
    const val PRIORITY_NORMAL = 129
    const val RESPONSE_STATUS_OK = 128
    const val STATUS_RETRIEVED = 129

    const val FROM_ADDRESS_PRESENT = 128
    const val FROM_INSERT_ADDRESS = 129

    // Part header field codes (WSP well-known header names).
    const val PART_CONTENT_LOCATION = 0x0E
    const val PART_CONTENT_ID = 0x40
    const val PART_CONTENT_DISPOSITION_1_1 = 0x2E
    const val PART_CONTENT_DISPOSITION_1_4 = 0x45

    // Content-Type parameter codes (WSP well-known parameters, 1.1 and 1.4 encodings).
    const val PARAM_CHARSET = 0x01
    const val PARAM_TYPE = 0x03
    const val PARAM_NAME = 0x05
    const val PARAM_FILENAME = 0x06
    const val PARAM_TYPE_MULTIPART = 0x09
    const val PARAM_START = 0x0A
    const val PARAM_START_INFO = 0x0B
    const val PARAM_NAME_1_4 = 0x17
    const val PARAM_FILENAME_1_4 = 0x18
    const val PARAM_START_1_4 = 0x19

    const val CONTENT_TYPE_MULTIPART_RELATED = "application/vnd.wap.multipart.related"
    const val CONTENT_TYPE_MULTIPART_MIXED = "application/vnd.wap.multipart.mixed"
    const val CONTENT_TYPE_SMIL = "application/smil"
    const val CONTENT_TYPE_TEXT = "text/plain"

    /** WSP assigned numbers for well-known content types, indexed by code. */
    val WELL_KNOWN_CONTENT_TYPES: List<String> =
        listOf(
            "*/*",
            "text/*",
            "text/html",
            "text/plain",
            "text/x-hdml",
            "text/x-ttml",
            "text/x-vCalendar",
            "text/x-vCard",
            "text/vnd.wap.wml",
            "text/vnd.wap.wmlscript",
            "text/vnd.wap.wta-event",
            "multipart/*",
            "multipart/mixed",
            "multipart/form-data",
            "multipart/byterantes",
            "multipart/alternative",
            "application/*",
            "application/java-vm",
            "application/x-www-form-urlencoded",
            "application/x-hdmlc",
            "application/vnd.wap.wmlc",
            "application/vnd.wap.wmlscriptc",
            "application/vnd.wap.wta-eventc",
            "application/vnd.wap.uaprof",
            "application/vnd.wap.wtls-ca-certificate",
            "application/vnd.wap.wtls-user-certificate",
            "application/x-x509-ca-cert",
            "application/x-x509-user-cert",
            "image/*",
            "image/gif",
            "image/jpeg",
            "image/tiff",
            "image/png",
            "image/vnd.wap.wbmp",
            "application/vnd.wap.multipart.*",
            "application/vnd.wap.multipart.mixed",
            "application/vnd.wap.multipart.form-data",
            "application/vnd.wap.multipart.byteranges",
            "application/vnd.wap.multipart.alternative",
            "application/xml",
            "text/xml",
            "application/vnd.wap.wbxml",
            "application/x-x968-cross-cert",
            "application/x-x968-ca-cert",
            "application/x-x968-user-cert",
            "text/vnd.wap.si",
            "application/vnd.wap.sic",
            "text/vnd.wap.sl",
            "application/vnd.wap.slc",
            "text/vnd.wap.co",
            "application/vnd.wap.coc",
            "application/vnd.wap.multipart.related",
            "application/vnd.wap.sia",
            "text/vnd.wap.connectivity-xml",
            "application/vnd.wap.connectivity-wbxml",
            "application/pkcs7-mime",
            "application/vnd.wap.hashed-certificate",
            "application/vnd.wap.signed-certificate",
            "application/vnd.wap.cert-response",
            "application/xhtml+xml",
            "application/wml+xml",
            "text/css",
            "application/vnd.wap.mms-message",
        )

    fun wellKnownContentTypeCode(contentType: String): Int? =
        WELL_KNOWN_CONTENT_TYPES.indexOfFirst { it.equals(contentType, ignoreCase = true) }.takeIf { it >= 0 }
}

/** IANA MIBenum <-> JDK charset, for the handful of charsets MMS text actually arrives in. */
object MmsCharsets {
    const val UTF_8 = 106
    private const val US_ASCII = 3
    private const val ISO_8859_1 = 4
    private const val UCS_2 = 1000
    private const val UTF_16 = 1015

    fun decode(
        bytes: ByteArray,
        mibEnum: Int?,
    ): String {
        val charset =
            when (mibEnum) {
                US_ASCII -> Charsets.US_ASCII
                ISO_8859_1 -> Charsets.ISO_8859_1
                UCS_2, UTF_16 -> Charsets.UTF_16
                else -> Charsets.UTF_8
            }
        return String(bytes, charset)
    }
}
