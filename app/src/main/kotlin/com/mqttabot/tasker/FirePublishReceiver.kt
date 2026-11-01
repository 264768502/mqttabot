package com.mqttabot.tasker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mqttabot.AppGraph
import com.mqttabot.log.AppLog

/** Executes the Tasker "MQTT publish" action. */
class FirePublishReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != TaskerApi.ACTION_FIRE_SETTING) return
        val config = PublishActionConfig.fromBundle(intent.getBundleExtra(TaskerApi.EXTRA_BUNDLE))
        if (config == null) {
            AppLog.w(TAG, "Fire without a valid action bundle; ignored")
            return
        }
        AppLog.i(TAG, "Tasker action fired: publish to ${config.topic}")
        val pending = goAsync()
        AppGraph.ioExecutor.execute {
            try {
                AppGraph.publisher().publish(config.topic, config.payload, config.qos, config.retained)
                AppLog.i(TAG, "Tasker publish to ${config.topic}: ok")
            } catch (e: Exception) {
                AppLog.w(TAG, "Tasker publish to ${config.topic} failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "FirePublishReceiver"
    }
}
