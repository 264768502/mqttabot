package com.mqttabot.tasker

import android.os.Bundle
import com.mqttabot.mqtt.ReceivedMessage
import com.mqttabot.mqtt.Topics
import java.util.regex.PatternSyntaxException

/** Settings of the Tasker "MQTT message received" event. */
data class EventFilterConfig(
    val topicFilter: String,
    val payloadPattern: String,
    val qos: Int,
) {
    fun matches(message: ReceivedMessage): Boolean = Topics.matches(topicFilter, message.topic) && payloadMatches(message.payload)

    private fun payloadMatches(payload: String): Boolean {
        if (payloadPattern.isEmpty()) return true
        return try {
            Regex(payloadPattern).containsMatchIn(payload)
        } catch (e: PatternSyntaxException) {
            false
        }
    }

    fun toBundle(): Bundle =
        Bundle().apply {
            putInt(KEY_VERSION, VERSION)
            putString(KEY_TOPIC_FILTER, topicFilter)
            putString(KEY_PAYLOAD_PATTERN, payloadPattern)
            putInt(KEY_QOS, qos)
        }

    companion object {
        const val KEY_VERSION = "com.mqttabot.extra.VERSION"
        const val KEY_TOPIC_FILTER = "com.mqttabot.extra.TOPIC_FILTER"
        const val KEY_PAYLOAD_PATTERN = "com.mqttabot.extra.PAYLOAD_PATTERN"
        const val KEY_QOS = "com.mqttabot.extra.QOS"
        private const val VERSION = 1

        fun fromBundle(bundle: Bundle?): EventFilterConfig? {
            val filter = bundle?.getString(KEY_TOPIC_FILTER) ?: return null
            return EventFilterConfig(filter, bundle.getString(KEY_PAYLOAD_PATTERN, ""), bundle.getInt(KEY_QOS, 0))
        }

        fun isValidPattern(pattern: String): Boolean =
            try {
                Regex(pattern)
                true
            } catch (e: PatternSyntaxException) {
                false
            }
    }
}
