package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.domain.hibernation.UnusedAppRestrictionChecker
import com.neteinstein.smsredirect.domain.hibernation.UnusedAppRestrictionStatus
import com.neteinstein.smsredirect.domain.repository.KeepAliveReminderRepository

/** The monthly "open the app" reminder is pointless once the user exempted the app. */
class ShouldSendKeepAliveReminderUseCase(
    private val checker: UnusedAppRestrictionChecker,
) {
    operator fun invoke(): Boolean = checker.status() != UnusedAppRestrictionStatus.DISABLED
}

/** True only the first time the user opens the app from the reminder, and only if it still applies. */
class ShouldShowUnusedAppInfoUseCase(
    private val checker: UnusedAppRestrictionChecker,
    private val repository: KeepAliveReminderRepository,
) {
    suspend operator fun invoke(): Boolean =
        !repository.isUnusedAppInfoShown() && checker.status() != UnusedAppRestrictionStatus.DISABLED
}

class MarkUnusedAppInfoShownUseCase(
    private val repository: KeepAliveReminderRepository,
) {
    suspend operator fun invoke() = repository.markUnusedAppInfoShown()
}
