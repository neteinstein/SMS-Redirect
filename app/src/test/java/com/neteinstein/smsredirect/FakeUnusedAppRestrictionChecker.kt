package com.neteinstein.smsredirect

import com.neteinstein.smsredirect.domain.hibernation.UnusedAppRestrictionChecker
import com.neteinstein.smsredirect.domain.hibernation.UnusedAppRestrictionStatus

class FakeUnusedAppRestrictionChecker(
    var status: UnusedAppRestrictionStatus = UnusedAppRestrictionStatus.ENABLED,
) : UnusedAppRestrictionChecker {
    override fun status(): UnusedAppRestrictionStatus = status
}
