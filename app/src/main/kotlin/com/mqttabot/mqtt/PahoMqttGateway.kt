package com.mqttabot.mqtt

import org.eclipse.paho.client.mqttv3.IMqttClient
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

/** [MqttGateway] backed by the pure-Java Eclipse Paho client. */
class PahoMqttGateway(
    private val clientFactory: (serverUri: String, clientId: String) -> IMqttClient = ::defaultClient,
    private val clock: () -> Long = System::currentTimeMillis,
) : MqttGateway {
    @Volatile
    private var client: IMqttClient? = null

    override val isConnected: Boolean
        get() = client?.isConnected == true

    override fun connect(
        config: BrokerConfig,
        listener: MqttGateway.Listener,
    ) {
        val c = clientFactory(config.serverUri, config.clientId)
        c.setCallback(Callback(listener))
        try {
            c.connect(options(config))
        } catch (e: Exception) {
            runCatching { c.close() }
            throw e
        }
        client = c
    }

    override fun subscribe(
        filter: String,
        qos: Int,
    ) = requireClient().subscribe(filter, qos)

    override fun unsubscribe(filter: String) = requireClient().unsubscribe(filter)

    override fun publish(
        topic: String,
        payload: ByteArray,
        qos: Int,
        retained: Boolean,
    ) = requireClient().publish(topic, payload, qos, retained)

    override fun disconnect() {
        val c = client ?: return
        client = null
        // disconnect() also stops Paho's automatic reconnect cycle.
        runCatching { c.disconnect(DISCONNECT_TIMEOUT_MS) }
        runCatching { c.close() }
    }

    private fun requireClient(): IMqttClient = client ?: throw IllegalStateException("Not connected")

    private inner class Callback(
        private val listener: MqttGateway.Listener,
    ) : MqttCallbackExtended {
        override fun connectComplete(
            reconnect: Boolean,
            serverURI: String?,
        ) {
            if (reconnect) listener.onReconnected()
        }

        override fun connectionLost(cause: Throwable?) = listener.onConnectionLost(cause)

        override fun messageArrived(
            topic: String,
            message: MqttMessage,
        ) = listener.onMessage(
            ReceivedMessage(topic, String(message.payload, Charsets.UTF_8), message.qos, message.isRetained, clock()),
        )

        override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
    }

    companion object {
        const val CONNECT_TIMEOUT_SECONDS = 10
        const val MAX_RECONNECT_DELAY_MS = 60_000
        private const val DISCONNECT_TIMEOUT_MS = 2_000L

        internal fun options(config: BrokerConfig) =
            MqttConnectOptions().apply {
                isCleanSession = config.cleanSession
                keepAliveInterval = config.keepAliveSeconds
                connectionTimeout = CONNECT_TIMEOUT_SECONDS
                isAutomaticReconnect = true
                maxReconnectDelay = MAX_RECONNECT_DELAY_MS
                if (config.username.isNotEmpty()) userName = config.username
                if (config.password.isNotEmpty()) password = config.password.toCharArray()
            }

        private fun defaultClient(
            serverUri: String,
            clientId: String,
        ): IMqttClient = MqttClient(serverUri, clientId, MemoryPersistence())
    }
}
