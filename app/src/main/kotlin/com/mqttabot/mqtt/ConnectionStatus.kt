package com.mqttabot.mqtt

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, WAITING_RETRY }

data class ConnectionStatus(
    val state: ConnectionState,
    val detail: String = "",
) {
    companion object {
        val DISCONNECTED = ConnectionStatus(ConnectionState.DISCONNECTED)
    }
}
