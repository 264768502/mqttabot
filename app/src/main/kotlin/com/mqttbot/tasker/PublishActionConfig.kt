package com.mqttbot.tasker

import android.os.Bundle

/** Settings of the Tasker "MQTT publish" action, stored by Tasker as a Bundle. */
data class PublishActionConfig(val topic: String, val payload: String, val qos: Int, val retained: Boolean) {
    fun toBundle(): Bundle = Bundle().apply {
        putInt(KEY_VERSION, VERSION)
        putString(KEY_TOPIC, topic)
        putString(KEY_PAYLOAD, payload)
        putInt(KEY_QOS, qos)
        putBoolean(KEY_RETAINED, retained)
        // Let Tasker substitute %variables in topic and payload before firing.
        putString(TaskerApi.KEY_VARIABLE_REPLACE_KEYS, "$KEY_TOPIC $KEY_PAYLOAD")
    }

    companion object {
        const val KEY_VERSION = "com.mqttbot.extra.VERSION"
        const val KEY_TOPIC = "com.mqttbot.extra.TOPIC"
        const val KEY_PAYLOAD = "com.mqttbot.extra.PAYLOAD"
        const val KEY_QOS = "com.mqttbot.extra.QOS"
        const val KEY_RETAINED = "com.mqttbot.extra.RETAINED"
        private const val VERSION = 1

        fun fromBundle(bundle: Bundle?): PublishActionConfig? {
            val topic = bundle?.getString(KEY_TOPIC) ?: return null
            return PublishActionConfig(
                topic = topic,
                payload = bundle.getString(KEY_PAYLOAD, ""),
                qos = bundle.getInt(KEY_QOS, 0),
                retained = bundle.getBoolean(KEY_RETAINED, false),
            )
        }
    }
}
