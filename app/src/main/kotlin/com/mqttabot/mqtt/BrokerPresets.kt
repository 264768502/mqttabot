package com.mqttabot.mqtt

/** A well-known public broker endpoint. */
data class BrokerPreset(
    val name: String,
    val host: String,
    val port: Int,
    val tls: Boolean,
    val username: String = "",
    val password: String = "",
)

object BrokerPresets {
    val all: List<BrokerPreset> =
        listOf(
            BrokerPreset("Mosquitto test (TCP 1883)", "test.mosquitto.org", 1883, false),
            BrokerPreset("Mosquitto test (TLS 8886)", "test.mosquitto.org", 8886, true),
            BrokerPreset("Mosquitto test, authenticated (TCP 1884)", "test.mosquitto.org", 1884, false, "rw", "readwrite"),
            BrokerPreset("HiveMQ public (TCP 1883)", "broker.hivemq.com", 1883, false),
            BrokerPreset("HiveMQ public (TLS 8883)", "broker.hivemq.com", 8883, true),
            BrokerPreset("EMQX public (TCP 1883)", "broker.emqx.io", 1883, false),
            BrokerPreset("EMQX public (TLS 8883)", "broker.emqx.io", 8883, true),
            BrokerPreset("Eclipse IoT (TCP 1883)", "mqtt.eclipseprojects.io", 1883, false),
            BrokerPreset("Eclipse IoT (TLS 8883)", "mqtt.eclipseprojects.io", 8883, true),
        )

    /** Index of the preset matching [config]'s endpoint, or -1. */
    fun indexOf(config: BrokerConfig): Int = all.indexOfFirst { it.host == config.host && it.port == config.port && it.tls == config.tls }
}
