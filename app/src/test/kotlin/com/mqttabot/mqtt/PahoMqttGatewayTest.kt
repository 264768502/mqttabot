package com.mqttabot.mqtt

import com.mqttabot.testutil.EmbeddedBroker
import com.mqttabot.testutil.TestClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.eclipse.paho.client.mqttv3.IMqttClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class PahoMqttGatewayTest {
    private val events = LinkedBlockingQueue<Any>()
    private val listener =
        object : MqttGateway.Listener {
            override fun onMessage(message: ReceivedMessage) {
                events.put(message)
            }

            override fun onConnectionLost(cause: Throwable?) {
                events.put("lost")
            }

            override fun onReconnected() {
                events.put("reconnected")
            }
        }
    private var broker: EmbeddedBroker? = null

    @After
    fun tearDown() {
        broker?.close()
    }

    @Test
    fun realBrokerRoundTripWithAuthAndReconnect() {
        val b = EmbeddedBroker("user", "pass").start().also { broker = it }
        val config = BrokerConfig(host = "127.0.0.1", port = b.port, username = "user", password = "pass", clientId = "gw-1")
        val gateway = PahoMqttGateway(clock = { 42L })
        assertFalse(gateway.isConnected)
        gateway.connect(config, listener)
        assertTrue(gateway.isConnected)
        gateway.subscribe("t/#", 1)
        TestClient(b.port, "user", "pass").use { it.publish("t/1", "héllo", 1, false) }
        assertEquals(ReceivedMessage("t/1", "héllo", 1, false, 42L), events.poll(10, TimeUnit.SECONDS))

        TestClient(b.port, "user", "pass").use { observer ->
            observer.subscribe("out")
            gateway.publish("out", "x".toByteArray(), 1, false)
            assertEquals("x", String(observer.await().second.payload))
        }

        b.disconnectAll()
        assertEquals("lost", events.poll(10, TimeUnit.SECONDS))
        assertEquals("reconnected", events.poll(15, TimeUnit.SECONDS))
        gateway.unsubscribe("t/#")
        gateway.disconnect()
        assertFalse(gateway.isConnected)
        gateway.disconnect()
    }

    @Test
    fun badCredentialsFailAndCloseClient() {
        val b = EmbeddedBroker("user", "pass").start().also { broker = it }
        val config = BrokerConfig(host = "127.0.0.1", port = b.port, username = "user", password = "wrong", clientId = "gw-2")
        val gateway = PahoMqttGateway()
        assertThrows(MqttException::class.java) { gateway.connect(config, listener) }
        assertFalse(gateway.isConnected)
    }

    @Test
    fun operationsRequireConnection() {
        val gateway = PahoMqttGateway()
        assertThrows(IllegalStateException::class.java) { gateway.subscribe("a", 0) }
        assertThrows(IllegalStateException::class.java) { gateway.unsubscribe("a") }
        assertThrows(IllegalStateException::class.java) { gateway.publish("a", ByteArray(0), 0, false) }
    }

    @Test
    fun callbackAndDisconnectEdgeCasesWithMock() {
        val client = mockk<IMqttClient>(relaxed = true)
        var callback: MqttCallbackExtended? = null
        every { client.setCallback(any()) } answers { callback = firstArg() }
        every { client.isConnected } returns true
        every { client.disconnect(any<Long>()) } throws MqttException(MqttException.REASON_CODE_CLIENT_ALREADY_DISCONNECTED.toInt())
        every { client.close() } throws MqttException(MqttException.REASON_CODE_CLIENT_CLOSED.toInt())
        var uri: String? = null
        val gateway =
            PahoMqttGateway({ u, _ ->
                uri = u
                client
            }, { 7L })
        gateway.connect(BrokerConfig(host = "h", port = 1, tls = true), listener)
        assertEquals("ssl://h:1", uri)
        assertTrue(gateway.isConnected)
        every { client.isConnected } returns false
        assertFalse(gateway.isConnected)

        callback!!.connectComplete(false, "x")
        callback!!.deliveryComplete(null)
        callback!!.messageArrived(
            "t",
            MqttMessage("p".toByteArray()).apply {
                qos = 2
                isRetained = true
            },
        )
        assertEquals(ReceivedMessage("t", "p", 2, true, 7L), events.poll())
        assertNull(events.poll())

        // Errors from disconnect/close are swallowed.
        gateway.disconnect()
        verify { client.close() }
    }

    @Test
    fun connectFailureClosesClientEvenIfCloseThrows() {
        val client = mockk<IMqttClient>(relaxed = true)
        every { client.connect(any()) } throws MqttException(MqttException.REASON_CODE_SERVER_CONNECT_ERROR.toInt())
        every { client.close() } throws MqttException(MqttException.REASON_CODE_CLIENT_CLOSED.toInt())
        val gateway = PahoMqttGateway({ _, _ -> client })
        assertThrows(MqttException::class.java) { gateway.connect(BrokerConfig(host = "h"), listener) }
        verify { client.close() }
    }

    @Test
    fun optionsReflectConfig() {
        val anon = PahoMqttGateway.options(BrokerConfig(cleanSession = false, keepAliveSeconds = 30))
        assertFalse(anon.isCleanSession)
        assertEquals(30, anon.keepAliveInterval)
        assertNull(anon.userName)
        assertNull(anon.password)
        assertTrue(anon.isAutomaticReconnect)
        assertEquals(PahoMqttGateway.CONNECT_TIMEOUT_SECONDS, anon.connectionTimeout)
        assertEquals(PahoMqttGateway.MAX_RECONNECT_DELAY_MS, anon.maxReconnectDelay)

        val auth = PahoMqttGateway.options(BrokerConfig(username = "u", password = "p"))
        assertEquals("u", auth.userName)
        assertArrayEquals("p".toCharArray(), auth.password)
    }
}
