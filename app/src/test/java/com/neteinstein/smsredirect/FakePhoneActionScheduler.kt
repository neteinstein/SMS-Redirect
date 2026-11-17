package com.neteinstein.smsredirect

import com.neteinstein.smsredirect.domain.device.PhoneActionScheduler
import com.neteinstein.smsredirect.domain.model.PhoneAction

class FakePhoneActionScheduler : PhoneActionScheduler {
    val scheduled = mutableListOf<Pair<PhoneAction, String>>()

    override fun schedule(
        action: PhoneAction,
        ruleName: String,
    ) {
        scheduled += action to ruleName
    }
}
