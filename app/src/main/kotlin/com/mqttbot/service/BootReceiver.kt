package com.mqttbot.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mqttbot.AppGraph

/** Restarts the subscription service after boot when enabled in settings. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && AppGraph.settings.autoStartOnBoot) {
            MqttService.start(context)
        }
    }
}
