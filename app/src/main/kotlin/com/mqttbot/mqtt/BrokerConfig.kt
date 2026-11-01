package com.mqttbot.mqtt

/** Connection settings for an MQTT broker. */
data class BrokerConfig(
    val host: String = DEFAULT_HOST,
    val port: Int = DEFAULT_PORT,
    val tls: Boolean = false,
    val username: String = "",
    val password: String = "",
    val clientId: String = "",
    val keepAliveSeconds: Int = DEFAULT_KEEP_ALIVE,
    val cleanSession: Boolean = true,
) {
    val serverUri: String
        get() = "${if (tls) "ssl" else "tcp"}://$host:$port"

    /** Returns the first invalid field, or null when the config is usable. */
    fun validate(): ConfigError? = when {
        host.isBlank() || host.any(Char::isWhitespace) -> ConfigError.HOST
        port !in 1..MAX_U16 -> ConfigError.PORT
        keepAliveSeconds !in 0..MAX_U16 -> ConfigError.KEEP_ALIVE
        password.isNotEmpty() && username.isEmpty() -> ConfigError.USERNAME
        else -> null
    }

    // Keep the password out of logs.
    override fun toString(): String = "BrokerConfig($serverUri, user=$username, clientId=$clientId)"

    companion object {
        const val DEFAULT_HOST = "test.mosquitto.org"
        const val DEFAULT_PORT = 1883
        const val DEFAULT_KEEP_ALIVE = 60
        private const val MAX_U16 = 65535
    }
}

enum class ConfigError { HOST, PORT, KEEP_ALIVE, USERNAME }
