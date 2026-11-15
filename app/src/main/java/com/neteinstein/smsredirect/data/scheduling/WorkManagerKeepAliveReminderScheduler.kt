package com.neteinstein.smsredirect.data.scheduling

import android.content.Context
import com.neteinstein.smsredirect.KeepAliveReminderWorker
import com.neteinstein.smsredirect.domain.scheduling.KeepAliveReminderScheduler

class WorkManagerKeepAliveReminderScheduler(
    private val context: Context,
) : KeepAliveReminderScheduler {
    override fun schedule() = KeepAliveReminderWorker.schedule(context)
}
