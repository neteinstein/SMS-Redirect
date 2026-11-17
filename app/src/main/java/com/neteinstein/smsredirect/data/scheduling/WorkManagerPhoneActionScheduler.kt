package com.neteinstein.smsredirect.data.scheduling

import android.content.Context
import com.neteinstein.smsredirect.PhoneActionWorker
import com.neteinstein.smsredirect.domain.device.PhoneActionScheduler
import com.neteinstein.smsredirect.domain.model.PhoneAction

/** Keeps WorkManager out of the domain and the ViewModels. */
class WorkManagerPhoneActionScheduler(
    private val context: Context,
) : PhoneActionScheduler {
    override fun schedule(
        action: PhoneAction,
        ruleName: String,
    ) = PhoneActionWorker.enqueue(context, action, ruleName)
}
