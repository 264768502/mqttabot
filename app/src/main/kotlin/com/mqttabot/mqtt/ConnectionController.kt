package com.mqttabot.mqtt

import com.mqttabot.data.Subscription
import com.mqttabot.log.AppLog
import com.mqttabot.log.Diagnostics
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

    private val listener =
        object : MqttGateway.Listener {
            override fun onMessage(message: ReceivedMessage) {
                AppLog.d(
                    TAG,
                    "Message on ${message.topic} (QoS ${message.qos}, retained=${message.retained}, " +
                        "${message.payload.length} chars): ${AppLog.preview(message.payload)}",
                )
                this@ConnectionController.onMessage(message)
            }

            override fun onConnectionLost(cause: Throwable?) {
                AppLog.w(TAG, "Connection lost; Paho reconnects automatically", cause)
                status.set(ConnectionStatus(ConnectionState.WAITING_RETRY, cause.toString()))
            }

            override fun onReconnected() =
                runner.execute {
                    AppLog.i(TAG, "Reconnected automatically; subscribing again")
                    // Clean sessions drop subscriptions on reconnect.
                    gateway?.let { subscribeAll(it) }
                    status.set(ConnectionStatus(ConnectionState.CONNECTED, configProvider().serverUri))
                }
        }

    fun start() {
        AppLog.i(TAG, "Start")
        running = true
        runner.execute { connect() }
    }

    fun stop() {
        AppLog.i(TAG, "Stop")
        running = false
        runner.execute {
            cancelRetry()
            closeGateway()
            status.set(ConnectionStatus.DISCONNECTED)
        }
        runner.shutdown()
    }

    /** Drops the current connection and connects again with fresh settings. */
    fun reconnect() =
        runner.execute {
            AppLog.i(TAG, "Reconnect with new settings")
            cancelRetry()
            closeGateway()
            attempt = 0
            connect()
        }

    fun subscribe(subscription: Subscription) =
        runner.execute {
            gateway?.let { g ->
                logged("Subscribe ${subscription.filter} (QoS ${subscription.qos})") { g.subscribe(subscription.filter, subscription.qos) }
            }
        }

    fun unsubscribe(filter: String) =
        runner.execute {
            gateway?.let { g -> logged("Unsubscribe $filter") { g.unsubscribe(filter) } }
        }

    // Subscription changes are best effort: a failure is logged and the connection stays up.
    private fun logged(
        action: String,
        block: () -> Unit,
    ) {
        try {
            block()
            AppLog.i(TAG, "$action: ok")
        } catch (e: Exception) {
            AppLog.w(TAG, "$action: failed", e)
        }
    }

    private fun connect() {
        if (!running || gateway != null) return
        val config = configProvider()
        AppLog.i(TAG, "Connecting (attempt ${attempt + 1}): ${Diagnostics.describe(config)}")
        status.set(ConnectionStatus(ConnectionState.CONNECTING, config.serverUri))
        val g = gatewayFactory()
        try {
            g.connect(config, listener)
            gateway = g
            subscribeAll(g)
            attempt = 0
            AppLog.i(TAG, "Connected to ${config.serverUri}")
            status.set(ConnectionStatus(ConnectionState.CONNECTED, config.serverUri))
        } catch (e: Exception) {
            AppLog.w(TAG, "Connect to ${config.serverUri} failed", e)
            gateway = null
            g.disconnect()
            scheduleRetry(e)
        }
    }

    private fun subscribeAll(g: MqttGateway) {
        val subscriptions = subscriptionsProvider()
        AppLog.i(TAG, "Subscribing to ${subscriptions.size}: ${subscriptions.joinToString { "${it.filter} (QoS ${it.qos})" }}")
        for (s in subscriptions) g.subscribe(s.filter, s.qos)
    }

    private fun scheduleRetry(error: Exception) {
        val delay = retryDelaysMillis[minOf(attempt, retryDelaysMillis.lastIndex)]
        attempt++
        AppLog.i(TAG, "Retry $attempt in ${delay}ms")
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
        private const val TAG = "Connection"
        val DEFAULT_RETRY_DELAYS_MS = listOf(2_000L, 5_000L, 15_000L, 30_000L, 60_000L)
    }
}
