package com.mqttabot.mqtt

/** Blocking MQTT client abstraction; implementations throw on failure. */
interface MqttGateway {
    val isConnected: Boolean

    fun connect(
        config: BrokerConfig,
        listener: Listener,
    )

    fun subscribe(
        filter: String,
        qos: Int,
    )

    fun unsubscribe(filter: String)

    fun publish(
        topic: String,
        payload: ByteArray,
        qos: Int,
        retained: Boolean,
    )

    /** Disconnects and releases resources; never throws. */
    fun disconnect()

    interface Listener {
        fun onMessage(message: ReceivedMessage)

        fun onConnectionLost(cause: Throwable?)

        fun onReconnected()
    }
}
