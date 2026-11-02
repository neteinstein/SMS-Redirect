package com.neteinstein.smsredirect

import com.neteinstein.smsredirect.domain.sms.DefaultSmsAppChecker

class FakeDefaultSmsAppChecker(
    var isDefault: Boolean = false,
) : DefaultSmsAppChecker {
    override fun isDefaultSmsApp(): Boolean = isDefault
}
