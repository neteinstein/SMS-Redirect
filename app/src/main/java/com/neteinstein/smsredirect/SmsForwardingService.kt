package com.neteinstein.smsredirect

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SmsForwardingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sender = intent?.getStringExtra(EXTRA_SENDER)
        val body = intent?.getStringExtra(EXTRA_BODY).orEmpty()

        serviceScope.launch {
            handleMessage(sender, body)
            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    private suspend fun handleMessage(sender: String?, body: String) {
        if (body.isBlank()) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val appContainer = (application as SmsRedirectApplication).appContainer
        val isRoaming = isNetworkRoaming()
        val canForward = appContainer.canForwardSmsUseCase(isRoaming)
        if (!canForward) return

        val rule = appContainer.findMatchingRuleUseCase(sender) ?: return
        SmsManager.getDefault().sendTextMessage(rule.targetNumber, null, body, null, null)
    }

    private fun isNetworkRoaming(): Boolean {
        val manager = getSystemService(TELEPHONY_SERVICE) as? TelephonyManager ?: return false
        return manager.isNetworkRoaming
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SENDER = "extra_sender"
        const val EXTRA_BODY = "extra_body"
    }
}
