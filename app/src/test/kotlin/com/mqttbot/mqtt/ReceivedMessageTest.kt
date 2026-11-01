package com.mqttbot.mqtt

import org.junit.Assert.assertEquals
import org.junit.Test

class ReceivedMessageTest {
    @Test
    fun statusDefaults() {
        assertEquals(ConnectionStatus(ConnectionState.DISCONNECTED, ""), ConnectionStatus.DISCONNECTED)
        assertEquals("", ConnectionStatus(ConnectionState.CONNECTED).detail)
    }

    @Test
    fun messageFields() {
        val m = ReceivedMessage("t", "p", 1, true, 5)
        assertEquals(m, m.copy())
        assertEquals("t", m.topic)
    }
}
