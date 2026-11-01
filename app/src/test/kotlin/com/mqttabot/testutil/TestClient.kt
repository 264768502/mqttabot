package com.mqttabot.testutil

import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Independent Paho client used by tests to observe / inject broker traffic. */
class TestClient(
    port: Int,
    username: String? = null,
    password: String? = null,
) : AutoCloseable {
    private val client = MqttClient("tcp://127.0.0.1:$port", "test-" + UUID.randomUUID(), MemoryPersistence())
    val received = LinkedBlockingQueue<Pair<String, MqttMessage>>()

    init {
        val opts =
            MqttConnectOptions().apply {
                isCleanSession = true
                if (username != null) userName = username
                if (password != null) this.password = password.toCharArray()
            }
        client.connect(opts)
    }

    fun subscribe(
        filter: String,
        qos: Int = 1,
    ) {
        client.subscribe(filter, qos) { topic, message -> received.put(topic to message) }
    }

    fun publish(
        topic: String,
        payload: String,
        qos: Int = 1,
        retained: Boolean = false,
    ) {
        client.publish(topic, payload.toByteArray(), qos, retained)
    }

    fun await(timeoutSeconds: Long = 10): Pair<String, MqttMessage> =
        checkNotNull(received.poll(timeoutSeconds, TimeUnit.SECONDS)) { "No message within ${timeoutSeconds}s" }

    override fun close() {
        runCatching { client.disconnect() }
        client.close()
    }
}
