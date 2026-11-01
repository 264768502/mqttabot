package com.mqttbot.mqtt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrokerPresetsTest {
    @Test
    fun presetsAreValidConfigs() {
        assertTrue(BrokerPresets.all.size >= 8)
        for (p in BrokerPresets.all) {
            val config = BrokerConfig(host = p.host, port = p.port, tls = p.tls, username = p.username, password = p.password)
            assertEquals(p.name, null, config.validate())
        }
    }

    @Test
    fun indexOfMatchesHostPortAndTls() {
        val hive = BrokerPresets.all.indexOfFirst { it.host == "broker.hivemq.com" && !it.tls }
        assertEquals(hive, BrokerPresets.indexOf(BrokerConfig(host = "broker.hivemq.com", port = 1883)))
        assertEquals(-1, BrokerPresets.indexOf(BrokerConfig(host = "broker.hivemq.com", port = 1883, tls = true)))
        assertEquals(-1, BrokerPresets.indexOf(BrokerConfig(host = "broker.hivemq.com", port = 1)))
        assertEquals(-1, BrokerPresets.indexOf(BrokerConfig(host = "example.com")))
    }

    @Test
    fun authenticatedPresetCarriesCredentials() {
        val p = BrokerPresets.all.single { it.port == 1884 }
        assertEquals("rw", p.username)
        assertEquals("readwrite", p.password)
    }
}
