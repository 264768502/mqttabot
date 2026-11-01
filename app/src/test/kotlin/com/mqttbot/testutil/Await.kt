package com.mqttbot.testutil

import android.os.Looper
import org.robolectric.Shadows.shadowOf

/** Polls [condition], draining the Robolectric main looper between checks. */
fun awaitUntil(timeoutMillis: Long = 10_000, message: String = "condition", condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (true) {
        shadowOf(Looper.getMainLooper()).idle()
        if (condition()) return
        check(System.currentTimeMillis() < deadline) { "Timed out waiting for $message" }
        Thread.sleep(20)
    }
}
