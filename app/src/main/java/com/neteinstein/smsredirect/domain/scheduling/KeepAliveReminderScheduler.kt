package com.neteinstein.smsredirect.domain.scheduling

/** Keeps WorkManager out of the domain, like [SmsBackupScheduler]. */
interface KeepAliveReminderScheduler {
    /**
     * (Re)starts the monthly countdown. Called whenever the app is opened, so the reminder only
     * ever fires after a full month of not opening it. Safe to call repeatedly.
     */
    fun schedule()
}
