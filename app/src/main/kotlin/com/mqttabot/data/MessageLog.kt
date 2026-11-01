package com.mqttabot.data

import com.mqttabot.mqtt.Observable
import com.mqttabot.mqtt.ReceivedMessage

/** Bounded, newest-first log of received messages. */
class MessageLog(
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private val items = ArrayDeque<ReceivedMessage>()
    val updates = Observable<List<ReceivedMessage>>(emptyList())

    @Synchronized
    fun add(message: ReceivedMessage) {
        items.addFirst(message)
        while (items.size > capacity) items.removeLast()
        updates.set(items.toList())
    }

    @Synchronized
    fun clear() {
        items.clear()
        updates.set(emptyList())
    }

    companion object {
        const val DEFAULT_CAPACITY = 100
    }
}
