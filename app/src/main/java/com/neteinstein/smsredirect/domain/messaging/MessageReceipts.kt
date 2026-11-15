package com.neteinstein.smsredirect.domain.messaging

import com.neteinstein.smsredirect.domain.mms.MmsHeaders
import com.neteinstein.smsredirect.domain.model.MessageReceipt

/**
 * Turns what the SMS/MMS store records about a sent message into the receipt a conversation
 * shows. Pure functions, so the mapping is unit-tested; the column values are the Telephony
 * provider's (`Telephony.Sms.STATUS_*`) and the MMS PDU's (X-Mms-Status, X-Mms-Read-Status).
 */
object MessageReceipts {
    /** `Telephony.Sms.STATUS_NONE`, `_COMPLETE`, `_PENDING`, `_FAILED`. */
    const val SMS_STATUS_NONE = -1
    const val SMS_STATUS_COMPLETE = 0
    const val SMS_STATUS_PENDING = 32
    const val SMS_STATUS_FAILED = 64

    fun forSms(providerStatus: Int): MessageReceipt =
        when {
            providerStatus == SMS_STATUS_NONE -> MessageReceipt.NONE
            providerStatus >= SMS_STATUS_FAILED -> MessageReceipt.NOT_DELIVERED
            providerStatus >= SMS_STATUS_PENDING -> MessageReceipt.PENDING
            else -> MessageReceipt.DELIVERED
        }

    /**
     * The provider status for an SMS status report's TP-Status (3GPP TS 23.040 9.2.3.15):
     * 0x00-0x1F transaction completed, 0x20-0x3F still trying, 0x40 and up permanent or
     * temporary failure, no more attempts.
     */
    fun smsStatusForReport(tpStatus: Int): Int =
        when {
            tpStatus < 0 -> SMS_STATUS_NONE
            tpStatus < 0x20 -> SMS_STATUS_COMPLETE
            tpStatus < 0x40 -> SMS_STATUS_PENDING
            else -> SMS_STATUS_FAILED
        }

    /**
     * @param reportRequested whether a delivery or read report was asked for when it was sent.
     * @param deliveryStatus the latest X-Mms-Status from an M-Delivery.ind, or null/0.
     * @param readStatus the X-Mms-Read-Status from an M-Read-Orig.ind, or null/0.
     */
    fun forMms(
        reportRequested: Boolean,
        deliveryStatus: Int?,
        readStatus: Int?,
    ): MessageReceipt =
        when {
            readStatus == MmsHeaders.READ_STATUS_READ -> MessageReceipt.READ

            deliveryStatus == MmsHeaders.STATUS_RETRIEVED || deliveryStatus == MmsHeaders.STATUS_FORWARDED -> MessageReceipt.DELIVERED

            // Deleted unread still means it reached the phone.
            readStatus == MmsHeaders.READ_STATUS_DELETED_WITHOUT_READING -> MessageReceipt.DELIVERED

            deliveryStatus in FAILED_MMS_STATUSES -> MessageReceipt.NOT_DELIVERED

            reportRequested -> MessageReceipt.PENDING

            else -> MessageReceipt.NONE
        }

    /** Whether a new M-Delivery.ind status may replace [current] (a later "deferred" must not hide "delivered"). */
    fun shouldRecordMmsDelivery(
        current: Int?,
        new: Int,
    ): Boolean = current == null || current == 0 || current !in DELIVERED_MMS_STATUSES || new in DELIVERED_MMS_STATUSES

    private val DELIVERED_MMS_STATUSES = setOf(MmsHeaders.STATUS_RETRIEVED, MmsHeaders.STATUS_FORWARDED)
    private val FAILED_MMS_STATUSES =
        setOf(MmsHeaders.STATUS_EXPIRED, MmsHeaders.STATUS_REJECTED, MmsHeaders.STATUS_UNREACHABLE, MmsHeaders.STATUS_UNRECOGNISED)
}
