package com.mqttabot.mqtt

import java.util.concurrent.CopyOnWriteArraySet

/** Thread-safe value holder; listeners run on the thread that sets the value. */
class Observable<T>(
    initial: T,
) {
    @Volatile
    var value: T = initial
        private set

    private val listeners = CopyOnWriteArraySet<(T) -> Unit>()

    fun set(newValue: T) {
        value = newValue
        listeners.forEach { it(newValue) }
    }

    /** Registers [listener] and immediately delivers the current value. */
    fun observe(listener: (T) -> Unit) {
        listeners.add(listener)
        listener(value)
    }

    fun remove(listener: (T) -> Unit) {
        listeners.remove(listener)
    }

    fun clearListeners() = listeners.clear()
}
