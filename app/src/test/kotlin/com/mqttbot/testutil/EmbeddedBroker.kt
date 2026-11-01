package com.mqttbot.testutil

import io.moquette.broker.Server
import io.moquette.broker.config.MemoryConfig
import io.moquette.broker.security.IAuthenticator
import io.moquette.broker.security.PermitAllAuthorizatorPolicy
import java.net.ServerSocket
import java.util.Properties

/** In-process MQTT broker (Moquette) on a free localhost port. */
class EmbeddedBroker(
    private val username: String? = null,
    private val password: String? = null,
) : AutoCloseable {
    private val server = Server()
    val port: Int = ServerSocket(0).use { it.localPort }

    fun start(): EmbeddedBroker {
        val props = Properties().apply {
            setProperty("host", "127.0.0.1")
            setProperty("port", port.toString())
            setProperty("websocket_port", "disabled")
            setProperty("persistence_enabled", "false")
            setProperty("allow_anonymous", (username == null).toString())
        }
        val auth = IAuthenticator { _, user, pass ->
            username == null || (user == username && pass != null && String(pass) == password)
        }
        server.startServer(MemoryConfig(props), emptyList(), null, auth, PermitAllAuthorizatorPolicy())
        return this
    }

    /** Drops all client connections, e.g. to exercise reconnect logic. */
    fun disconnectAll() {
        server.listConnectedClients().forEach { server.disconnectClient(it.clientID) }
    }

    fun connectedClientIds(): List<String> = server.listConnectedClients().map { it.clientID }

    override fun close() = server.stopServer()
}
