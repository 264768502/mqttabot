package com.mqttbot.mqtt

import com.mqttbot.data.Subscription
import java.util.concurrent.Future

/**
 * Keeps one long-lived broker connection with its subscriptions. All state changes run on [runner].
 */
class ConnectionController(
    private val configProvider: () -> BrokerConfig,
    private val subscriptionsProvider: () -> List<Subscription>,
    private val gatewayFactory: () -> MqttGateway,
    private val runner: TaskRunner,
    private val status: Observable<ConnectionStatus>,
    private val onMessage: (ReceivedMessage) -> Unit,
    private val retryDelaysMillis: List<Long> = DEFAULT_RETRY_DELAYS_MS,
) {
    @Volatile
    private var gateway: MqttGateway? = null

    @Volatile
    private var running = false
    private var attempt = 0
    private var retry: Future<*>? = null

    /** The connected gateway, usable for publishing, or null. */
    val activeGateway: MqttGateway?
        get() = gateway?.takeIf { it.isConnected }

    private val listener = object : MqttGateway.Listener {
        override fun onMessage(message: ReceivedMessage) = this@ConnectionController.onMessage(message)

        override fun onConnectionLost(cause: Throwable?) =
            status.set(ConnectionStatus(ConnectionState.WAITING_RETRY, cause.toString()))

        override fun onReconnected() = runner.execute {
            // Clean sessions drop subscriptions on reconnect.
            gateway?.let { subscribeAll(it) }
            status.set(ConnectionStatus(ConnectionState.CONNECTED, configProvider().serverUri))
        }
    }

    fun start() {
        running = true
        runner.execute { connect() }
    }

    fun stop() {
        running = false
        runner.execute {
            cancelRetry()
            closeGateway()
            status.set(ConnectionStatus.DISCONNECTED)
        }
        runner.shutdown()
    }

    /** Drops the current connection and connects again with fresh settings. */
    fun reconnect() = runner.execute {
        cancelRetry()
        closeGateway()
        attempt = 0
        connect()
    }

    fun subscribe(subscription: Subscription) = runner.execute {
        gateway?.let { runCatching { it.subscribe(subscription.filter, subscription.qos) } }
    }

    fun unsubscribe(filter: String) = runner.execute {
        gateway?.let { runCatching { it.unsubscribe(filter) } }
    }

    private fun connect() {
        if (!running || gateway != null) return
        val config = configProvider()
        status.set(ConnectionStatus(ConnectionState.CONNECTING, config.serverUri))
        val g = gatewayFactory()
        try {
            g.connect(config, listener)
            gateway = g
            subscribeAll(g)
            attempt = 0
            status.set(ConnectionStatus(ConnectionState.CONNECTED, config.serverUri))
        } catch (e: Exception) {
            gateway = null
            g.disconnect()
            scheduleRetry(e)
        }
    }

    private fun subscribeAll(g: MqttGateway) {
        for (s in subscriptionsProvider()) g.subscribe(s.filter, s.qos)
    }

    private fun scheduleRetry(error: Exception) {
        val delay = retryDelaysMillis[minOf(attempt, retryDelaysMillis.lastIndex)]
        attempt++
        status.set(ConnectionStatus(ConnectionState.WAITING_RETRY, error.toString()))
        retry = runner.schedule(delay) { connect() }
    }

    private fun cancelRetry() {
        retry?.cancel(false)
        retry = null
    }

    private fun closeGateway() {
        gateway?.disconnect()
        gateway = null
    }

    companion object {
        val DEFAULT_RETRY_DELAYS_MS = listOf(2_000L, 5_000L, 15_000L, 30_000L, 60_000L)
    }
}
