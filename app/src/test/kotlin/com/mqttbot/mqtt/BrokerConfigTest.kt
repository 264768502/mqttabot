package com.mqttbot.mqtt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrokerConfigTest {
    @Test
    fun serverUriUsesSchemeForTls() {
        assertEquals("tcp://h:1883", BrokerConfig(host = "h").serverUri)
        assertEquals("ssl://h:8883", BrokerConfig(host = "h", port = 8883, tls = true).serverUri)
    }

    @Test
    fun defaultsAreValid() {
        assertNull(BrokerConfig().validate())
        assertNull(BrokerConfig(username = "u", password = "p", keepAliveSeconds = 0).validate())
        assertNull(BrokerConfig(username = "u").validate())
    }

    @Test
    fun validateReportsFirstError() {
        assertEquals(ConfigError.HOST, BrokerConfig(host = " ").validate())
        assertEquals(ConfigError.HOST, BrokerConfig(host = "a b").validate())
        assertEquals(ConfigError.PORT, BrokerConfig(port = 0).validate())
        assertEquals(ConfigError.PORT, BrokerConfig(port = 65536).validate())
        assertEquals(ConfigError.KEEP_ALIVE, BrokerConfig(keepAliveSeconds = -1).validate())
        assertEquals(ConfigError.KEEP_ALIVE, BrokerConfig(keepAliveSeconds = 65536).validate())
        assertEquals(ConfigError.USERNAME, BrokerConfig(password = "p").validate())
    }

    @Test
    fun toStringHidesPassword() {
        val s = BrokerConfig(host = "h", username = "u", password = "secret", clientId = "c").toString()
        assertTrue(s.contains("tcp://h:1883"))
        assertTrue(s.contains("u"))
        assertFalse(s.contains("secret"))
    }

    @Test
    fun dataClassSemantics() {
        val a = BrokerConfig(host = "h")
        assertEquals(a, a.copy())
        assertEquals(a.hashCode(), a.copy().hashCode())
    }
}
