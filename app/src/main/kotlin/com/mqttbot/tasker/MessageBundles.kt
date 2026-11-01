package com.mqttbot.tasker

import android.os.Bundle
import com.mqttbot.mqtt.ReceivedMessage

/** Encodes a received message as Tasker pass-through data and as event variables. */
object MessageBundles {
    private const val KEY_TOPIC = "com.mqttbot.message.TOPIC"
    private const val KEY_PAYLOAD = "com.mqttbot.message.PAYLOAD"
    private const val KEY_QOS = "com.mqttbot.message.QOS"
    private const val KEY_RETAINED = "com.mqttbot.message.RETAINED"
    private const val KEY_TIMESTAMP = "com.mqttbot.message.TIMESTAMP"

    fun toPassThrough(message: ReceivedMessage): Bundle = Bundle().apply {
        putString(KEY_TOPIC, message.topic)
        putString(KEY_PAYLOAD, message.payload)
        putInt(KEY_QOS, message.qos)
        putBoolean(KEY_RETAINED, message.retained)
        putLong(KEY_TIMESTAMP, message.timestamp)
    }

    fun fromPassThrough(bundle: Bundle?): ReceivedMessage? {
        val topic = bundle?.getString(KEY_TOPIC) ?: return null
        return ReceivedMessage(
            topic = topic,
            payload = bundle.getString(KEY_PAYLOAD, ""),
            qos = bundle.getInt(KEY_QOS, 0),
            retained = bundle.getBoolean(KEY_RETAINED, false),
            timestamp = bundle.getLong(KEY_TIMESTAMP, 0L),
        )
    }

    fun toVariables(message: ReceivedMessage): Bundle = Bundle().apply {
        putString(TaskerApi.VAR_TOPIC, message.topic)
        putString(TaskerApi.VAR_PAYLOAD, message.payload)
        putString(TaskerApi.VAR_QOS, message.qos.toString())
        putString(TaskerApi.VAR_RETAINED, message.retained.toString())
        putString(TaskerApi.VAR_RECEIVED_AT, message.timestamp.toString())
    }
}
