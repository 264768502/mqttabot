package com.mqttabot.mqtt

import java.util.UUID

/** Publishes via the shared connection when available, otherwise via a short-lived one. */
class MqttPublisher(
    private val configProvider: () -> BrokerConfig,
    private val gatewayFactory: () -> MqttGateway,
    private val sharedGateway: () -> MqttGateway?,
) {
    fun publish(
        topic: String,
        payload: String,
        qos: Int,
        retained: Boolean,
    ) {
        require(Topics.isValidTopicName(topic)) { "Invalid topic: $topic" }
        require(qos in 0..2) { "Invalid QoS: $qos" }
        val bytes = payload.toByteArray(Charsets.UTF_8)
        val shared = sharedGateway()
        if (shared != null) {
            shared.publish(topic, bytes, qos, retained)
            return
        }
        // Distinct client id so we never kick the long-lived connection off the broker.
        val config = configProvider().copy(clientId = oneShotClientId(), cleanSession = true)
        val g = gatewayFactory()
        try {
            g.connect(config, NoopListener)
            g.publish(topic, bytes, qos, retained)
        } finally {
            g.disconnect()
        }
    }

    internal object NoopListener : MqttGateway.Listener {
        override fun onMessage(message: ReceivedMessage) = Unit

        override fun onConnectionLost(cause: Throwable?) = Unit

        override fun onReconnected() = Unit
    }

    companion object {
        fun oneShotClientId(): String = "mqttabot-pub-" + UUID.randomUUID().toString().substring(0, 8)
    }
}
