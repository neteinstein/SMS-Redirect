package com.neteinstein.smsredirect.domain.messaging

import com.neteinstein.smsredirect.domain.mms.MmsHeaders
import com.neteinstein.smsredirect.domain.model.MessageReceipt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageReceiptsTest {
    @Test
    fun `sms provider status maps to a receipt`() {
        assertEquals(MessageReceipt.NONE, MessageReceipts.forSms(MessageReceipts.SMS_STATUS_NONE))
        assertEquals(MessageReceipt.DELIVERED, MessageReceipts.forSms(MessageReceipts.SMS_STATUS_COMPLETE))
        assertEquals(MessageReceipt.PENDING, MessageReceipts.forSms(MessageReceipts.SMS_STATUS_PENDING))
        assertEquals(MessageReceipt.NOT_DELIVERED, MessageReceipts.forSms(MessageReceipts.SMS_STATUS_FAILED))
    }

    @Test
    fun `sms status report TP-Status ranges map to provider status`() {
        assertEquals(MessageReceipts.SMS_STATUS_COMPLETE, MessageReceipts.smsStatusForReport(0x00))
        assertEquals(MessageReceipts.SMS_STATUS_COMPLETE, MessageReceipts.smsStatusForReport(0x02))
        assertEquals(MessageReceipts.SMS_STATUS_PENDING, MessageReceipts.smsStatusForReport(0x20))
        assertEquals(MessageReceipts.SMS_STATUS_FAILED, MessageReceipts.smsStatusForReport(0x41))
        assertEquals(MessageReceipts.SMS_STATUS_FAILED, MessageReceipts.smsStatusForReport(0x60))
    }

    @Test
    fun `mms read beats delivered, and delivered beats pending`() {
        assertEquals(MessageReceipt.READ, MessageReceipts.forMms(true, MmsHeaders.STATUS_RETRIEVED, MmsHeaders.READ_STATUS_READ))
        assertEquals(MessageReceipt.DELIVERED, MessageReceipts.forMms(true, MmsHeaders.STATUS_RETRIEVED, null))
        assertEquals(MessageReceipt.DELIVERED, MessageReceipts.forMms(true, null, MmsHeaders.READ_STATUS_DELETED_WITHOUT_READING))
        assertEquals(MessageReceipt.NOT_DELIVERED, MessageReceipts.forMms(true, MmsHeaders.STATUS_EXPIRED, null))
        assertEquals(MessageReceipt.PENDING, MessageReceipts.forMms(true, MmsHeaders.STATUS_DEFERRED, null))
        assertEquals(MessageReceipt.NONE, MessageReceipts.forMms(false, null, null))
    }

    @Test
    fun `a later deferral never hides an earlier delivery in a group`() {
        assertTrue(MessageReceipts.shouldRecordMmsDelivery(null, MmsHeaders.STATUS_DEFERRED))
        assertTrue(MessageReceipts.shouldRecordMmsDelivery(MmsHeaders.STATUS_DEFERRED, MmsHeaders.STATUS_RETRIEVED))
        assertFalse(MessageReceipts.shouldRecordMmsDelivery(MmsHeaders.STATUS_RETRIEVED, MmsHeaders.STATUS_DEFERRED))
        assertTrue(MessageReceipts.shouldRecordMmsDelivery(MmsHeaders.STATUS_RETRIEVED, MmsHeaders.STATUS_FORWARDED))
    }

    @Test
    fun `cdma status reports are decoded by error class, not as a gsm TP-Status`() {
        // SmsMessage.getStatus() for 3gpp2: (errorClass << 8 | messageStatus) << 16.
        val delivered = (0 shl 8 or 0x02) shl 16
        val temporary = (2 shl 8 or 0x04) shl 16
        val permanent = (3 shl 8 or 0x06) shl 16

        assertEquals(MessageReceipts.SMS_STATUS_COMPLETE, MessageReceipts.smsStatusForCdmaReport(delivered))
        assertEquals(MessageReceipts.SMS_STATUS_PENDING, MessageReceipts.smsStatusForCdmaReport(temporary))
        assertEquals(MessageReceipts.SMS_STATUS_FAILED, MessageReceipts.smsStatusForCdmaReport(permanent))
    }
}
