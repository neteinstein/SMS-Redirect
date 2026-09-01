package com.neteinstein.smsredirect

import com.neteinstein.smsredirect.domain.mail.MailAppendResult
import com.neteinstein.smsredirect.domain.mail.MailConnectException
import com.neteinstein.smsredirect.domain.mail.MailFolder
import com.neteinstein.smsredirect.domain.mail.MailTransport
import com.neteinstein.smsredirect.domain.mail.MailVerificationResult
import com.neteinstein.smsredirect.domain.mail.OutgoingMail
import com.neteinstein.smsredirect.domain.model.GmailCredentials

class FakeMailTransport(
    var verifyResult: MailVerificationResult = MailVerificationResult.Success,
) : MailTransport {
    val appended = mutableListOf<OutgoingMail>()
    val openedFolders = mutableListOf<String>()
    var verifiedWith: GmailCredentials? = null

    /** Scripts a failure for the n-th append (1-based); everything else succeeds. */
    var failAppendAtIndex: Int? = null

    /** Scripts every append to fail, unlike [failAppendAtIndex] which scripts a single one. */
    var failAllAppends: Boolean = false
    var appendFailure: MailAppendResult = MailAppendResult.Transient("scripted")

    /** Scripts [withFolder] itself failing before the block ever runs -- i.e. connect time. */
    var connectFailure: MailConnectException? = null

    override suspend fun verify(credentials: GmailCredentials): MailVerificationResult {
        verifiedWith = credentials
        return verifyResult
    }

    override suspend fun <T> withFolder(
        credentials: GmailCredentials,
        folderName: String,
        block: suspend (MailFolder) -> T,
    ): T {
        connectFailure?.let { throw it }
        openedFolders += folderName
        return block(FakeMailFolder())
    }

    private inner class FakeMailFolder : MailFolder {
        override suspend fun append(mail: OutgoingMail): MailAppendResult {
            if (failAllAppends || appended.size + 1 == failAppendAtIndex) return appendFailure
            appended += mail
            return MailAppendResult.Success
        }
    }
}
