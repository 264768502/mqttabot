package com.mqttbot.mqtt

/** MQTT 3.1.1 topic name / filter rules. */
object Topics {
    private const val MAX_BYTES = 65535

    fun isValidTopicName(topic: String): Boolean =
        isValidString(topic) && topic.none { it == '+' || it == '#' }

    fun isValidFilter(filter: String): Boolean {
        if (!isValidString(filter)) return false
        val levels = filter.split('/')
        for ((i, level) in levels.withIndex()) {
            if ('#' in level && (level != "#" || i != levels.lastIndex)) return false
            if ('+' in level && level != "+") return false
        }
        return true
    }

    /** Whether [topic] matches the subscription [filter]. */
    fun matches(filter: String, topic: String): Boolean {
        // Wildcards at the first level must not match $-prefixed system topics.
        if (topic.startsWith('$') && (filter.startsWith('+') || filter.startsWith('#'))) return false
        val f = filter.split('/')
        val t = topic.split('/')
        for ((i, level) in f.withIndex()) {
            if (level == "#") return true
            if (i >= t.size) return false
            if (level != "+" && level != t[i]) return false
        }
        return f.size == t.size
    }

    private fun isValidString(s: String): Boolean =
        s.isNotEmpty() && '\u0000' !in s && s.toByteArray(Charsets.UTF_8).size <= MAX_BYTES
}
