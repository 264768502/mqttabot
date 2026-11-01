package com.mqttabot.tasker

import android.content.Context
import android.content.Intent
import com.mqttabot.log.AppLog
import com.mqttabot.mqtt.ReceivedMessage
import java.util.concurrent.atomic.AtomicInteger

/** Asks Tasker to evaluate "MQTT message received" event profiles for a new message. */
object TaskerEventDispatcher {
    private const val TAG = "TaskerDispatch"
    private val nextId = AtomicInteger((System.nanoTime() and 0x3fffffff).toInt())

    fun requestQuery(
        message: ReceivedMessage,
        messageId: Int,
    ): Intent {
        val data = MessageBundles.toPassThrough(message)
        data.putInt(TaskerApi.KEY_PASS_THROUGH_MESSAGE_ID, messageId)
        return Intent(TaskerApi.ACTION_REQUEST_QUERY)
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            .putExtra(TaskerApi.EXTRA_ACTIVITY, EventConfigActivity::class.java.name)
            .putExtra(TaskerApi.EXTRA_PASS_THROUGH_DATA, data)
    }

    /** Delivers REQUEST_QUERY the way Tasker's own plugin library does. */
    fun dispatch(
        context: Context,
        message: ReceivedMessage,
    ) {
        // Mask keeps ids positive, as Tasker expects.
        val id = nextId.incrementAndGet() and Int.MAX_VALUE
        val request = requestQuery(message, id)
        AppLog.d(TAG, "Asking Tasker to check events for ${message.topic} (request $id)")
        val pm = context.packageManager
        val probe = Intent(TaskerApi.ACTION_REQUEST_QUERY)

        // Tasker 5.1.5b+ takes REQUEST_QUERY through an exported service.
        val handled = mutableSetOf<String>()
        for (info in pm.queryIntentServices(probe, 0)) {
            val service = info.serviceInfo
            try {
                context.startService(Intent(request).setClassName(service.packageName, service.name))
                handled += service.packageName
                AppLog.d(TAG, "Request $id sent to the service ${service.packageName}/${service.name}")
            } catch (e: Exception) {
                AppLog.w(TAG, "Cannot start ${service.packageName}/${service.name}", e)
            }
        }
        if (handled.isEmpty()) {
            AppLog.d(TAG, "No host service for request $id; sending an implicit broadcast")
            context.sendBroadcast(request)
            return
        }
        // Android 8+ drops implicit broadcasts to manifest receivers, so address other hosts directly.
        for (info in pm.queryBroadcastReceivers(probe, 0)) {
            val receiver = info.activityInfo
            if (receiver.packageName in handled) continue
            AppLog.d(TAG, "Request $id sent to the receiver ${receiver.packageName}/${receiver.name}")
            context.sendBroadcast(Intent(request).setClassName(receiver.packageName, receiver.name))
        }
    }
}
