package com.neteinstein.smsredirect

import com.neteinstein.smsredirect.domain.device.SoundModeAccessChecker

class FakeSoundModeAccessChecker(
    var granted: Boolean = false,
) : SoundModeAccessChecker {
    override fun canChangeSoundMode(): Boolean = granted
}
