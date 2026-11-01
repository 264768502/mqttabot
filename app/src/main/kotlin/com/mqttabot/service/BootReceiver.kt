package com.mqttabot.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mqttabot.AppGraph
import com.mqttabot.log.AppLog

/** Restarts the subscription service after boot when enabled in settings. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val autoStart = AppGraph.settings.autoStartOnBoot
        AppLog.i("Boot", "Boot completed; start on boot: $autoStart")
        if (autoStart) MqttService.start(context)
    }
}
