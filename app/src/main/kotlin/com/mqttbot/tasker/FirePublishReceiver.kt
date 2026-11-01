package com.mqttbot.tasker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mqttbot.AppGraph

/** Executes the Tasker "MQTT publish" action. */
class FirePublishReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TaskerApi.ACTION_FIRE_SETTING) return
        val config = PublishActionConfig.fromBundle(intent.getBundleExtra(TaskerApi.EXTRA_BUNDLE)) ?: return
        val pending = goAsync()
        AppGraph.ioExecutor.execute {
            try {
                AppGraph.publisher().publish(config.topic, config.payload, config.qos, config.retained)
            } catch (e: Exception) {
                Log.w(TAG, "Tasker publish to ${config.topic} failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "FirePublishReceiver"
    }
}
