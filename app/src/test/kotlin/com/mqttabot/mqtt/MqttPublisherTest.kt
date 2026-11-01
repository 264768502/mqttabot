package com.mqttabot.mqtt

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mqttabot.testutil.FakeGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

// Robolectric: the code under test logs through android.util.Log.
@RunWith(AndroidJUnit4::class)
class MqttPublisherTest {
    private val config = BrokerConfig(host = "h", clientId = "main", cleanSession = false)

    @Test
    fun usesSharedGatewayWhenAvailable() {
        val shared = FakeGateway()
        var created = 0
        MqttPublisher({ config }, {
            created++
            FakeGateway()
        }, { shared }).publish("t", "p", 1, true)
        assertEquals(listOf(FakeGateway.Published("t", "p", 1, true)), shared.published)
        assertEquals(0, created)
    }

    @Test
    fun oneShotConnectionWhenNoSharedGateway() {
        val g = FakeGateway()
        MqttPublisher({ config }, { g }, { null }).publish("t", "ü", 2, false)
        assertEquals(listOf(FakeGateway.Published("t", "ü", 2, false)), g.published)
        assertEquals(1, g.disconnects)
        val used = g.connectedWith!!
        assertNotEquals("main", used.clientId)
        assertTrue(used.cleanSession)
        assertEquals("h", used.host)
    }

    @Test
    fun oneShotDisconnectsOnFailure() {
        val g = FakeGateway(connectError = IOException("x"))
        assertThrows(IOException::class.java) { MqttPublisher({ config }, { g }, { null }).publish("t", "p", 0, false) }
        assertEquals(1, g.disconnects)
    }

    @Test
    fun rejectsInvalidInput() {
        val p = MqttPublisher({ config }, { FakeGateway() }, { null })
        assertThrows(IllegalArgumentException::class.java) { p.publish("a/#", "p", 0, false) }
        assertThrows(IllegalArgumentException::class.java) { p.publish("a", "p", 3, false) }
        assertThrows(IllegalArgumentException::class.java) { p.publish("a", "p", -1, false) }
    }

    @Test
    fun noopListenerIgnoresEvents() {
        MqttPublisher.NoopListener.onMessage(ReceivedMessage("t", "p", 0, false, 0))
        MqttPublisher.NoopListener.onConnectionLost(null)
        MqttPublisher.NoopListener.onReconnected()
        assertTrue(MqttPublisher.oneShotClientId().startsWith("mqttabot-pub-"))
    }
}
