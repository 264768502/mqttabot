package com.mqttabot.tasker

import android.content.Context
import android.content.Intent
import com.mqttabot.mqtt.ReceivedMessage
import java.util.concurrent.atomic.AtomicInteger

/** Asks Tasker to evaluate "MQTT message received" event profiles for a new message. */
object TaskerEventDispatcher {
    private val nextId = AtomicInteger((System.nanoTime() and 0x3fffffff).toInt())

    fun requestQuery(
        message: ReceivedMessage,
        messageId: Int,
    ): Intent {
        val data = MessageBundles.toPassThrough(message)
        data.putInt(TaskerApi.KEY_PASS_THROUGH_MESSAGE_ID, messageId)
        return Intent(TaskerApi.ACTION_REQUEST_QUERY)
            .putExtra(TaskerApi.EXTRA_ACTIVITY, EventConfigActivity::class.java.name)
            .putExtra(TaskerApi.EXTRA_PASS_THROUGH_DATA, data)
    }

    fun dispatch(
        context: Context,
        message: ReceivedMessage,
    ) {
        // Mask keeps ids positive, as Tasker expects.
        context.sendBroadcast(requestQuery(message, nextId.incrementAndGet() and Int.MAX_VALUE))
    }
}
