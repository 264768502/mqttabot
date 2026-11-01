package com.mqttbot.mqtt

import com.mqttbot.data.Subscription
import com.mqttbot.testutil.FakeGateway
import com.mqttbot.testutil.ManualTaskRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ConnectionControllerTest {
    private val config = BrokerConfig(host = "h")
    private var subs = listOf(Subscription("a/#", 1), Subscription("b", 0))
    private val gateways = mutableListOf<FakeGateway>()
    private var nextGateway: () -> FakeGateway = { FakeGateway() }
    private val runner = ManualTaskRunner()
    private val status = Observable(ConnectionStatus.DISCONNECTED)
    private val statuses = mutableListOf<ConnectionStatus>().also { list -> status.observe { list += it } }
    private val messages = mutableListOf<ReceivedMessage>()

    private fun controller(delays: List<Long> = listOf(1L, 2L)) = ConnectionController(
        configProvider = { config },
        subscriptionsProvider = { subs },
        gatewayFactory = { nextGateway().also { gateways += it } },
        runner = runner,
        status = status,
        onMessage = { messages += it },
        retryDelaysMillis = delays,
    )

    @Test
    fun startConnectsAndSubscribes() {
        val c = controller()
        assertNull(c.activeGateway)
        c.start()
        val g = gateways.single()
        assertEquals(config, g.connectedWith)
        assertEquals(listOf("a/#" to 1, "b" to 0), g.subscriptions)
        assertSame(g, c.activeGateway)
        assertEquals(
            listOf(ConnectionState.DISCONNECTED, ConnectionState.CONNECTING, ConnectionState.CONNECTED),
            statuses.map { it.state },
        )
        assertEquals("tcp://h:1883", status.value.detail)
    }

    @Test
    fun activeGatewayIsNullWhenNotConnected() {
        val c = controller()
        c.start()
        gateways.single().connected = false
        assertNull(c.activeGateway)
    }

    @Test
    fun startTwiceKeepsOneConnection() {
        val c = controller()
        c.start()
        c.start()
        assertEquals(1, gateways.size)
    }

    @Test
    fun connectFailureRetriesWithBackoff() {
        nextGateway = { FakeGateway(connectError = IOException("down")) }
        val c = controller(delays = listOf(10L, 20L))
        c.start()
        assertEquals(ConnectionState.WAITING_RETRY, status.value.state)
        assertTrue(status.value.detail.contains("down"))
        assertEquals(1, gateways.single().disconnects)
        assertEquals(listOf(10L), runner.scheduled.map { it.first })
        runner.runScheduled()
        runner.runScheduled()
        assertEquals(3, gateways.size)
        // Delay sticks at the last value.
        assertEquals(listOf(20L), runner.scheduled.map { it.first })
        nextGateway = { FakeGateway() }
        runner.runScheduled()
        assertEquals(ConnectionState.CONNECTED, status.value.state)
        assertTrue(runner.scheduled.isEmpty())
    }

    @Test
    fun subscribeFailureCountsAsConnectFailure() {
        nextGateway = { FakeGateway(subscribeError = IOException("nope")) }
        val c = controller()
        c.start()
        assertNull(c.activeGateway)
        assertEquals(ConnectionState.WAITING_RETRY, status.value.state)
    }

    @Test
    fun stopDisconnectsCancelsRetryAndShutsDown() {
        nextGateway = { FakeGateway(connectError = IOException("down")) }
        val c = controller()
        c.start()
        val pending = runner.scheduled.single().second
        c.stop()
        assertTrue(pending.isCancelled)
        assertTrue(runner.isShutdown)
        assertEquals(ConnectionStatus.DISCONNECTED, status.value)
        // A retry firing after stop does nothing.
        pending.run()
        assertEquals(1, gateways.size)
    }

    @Test
    fun stopBeforeQueuedConnectRunsSkipsConnect() {
        val deferred = ManualTaskRunner(deferExecute = true)
        val c = ConnectionController({ config }, { subs }, { FakeGateway().also { gateways += it } }, deferred, status, {})
        c.start()
        c.stop()
        deferred.runQueued()
        assertTrue(gateways.isEmpty())
        assertEquals(ConnectionStatus.DISCONNECTED, status.value)
    }

    @Test
    fun stopWhenConnected() {
        val c = controller()
        c.start()
        c.stop()
        assertEquals(1, gateways.single().disconnects)
        assertNull(c.activeGateway)
    }

    @Test
    fun stopWithoutStartIsSafe() {
        controller().stop()
        assertEquals(ConnectionStatus.DISCONNECTED, status.value)
    }

    @Test
    fun reconnectUsesFreshGateway() {
        val c = controller()
        c.start()
        c.reconnect()
        assertEquals(2, gateways.size)
        assertEquals(1, gateways[0].disconnects)
        assertSame(gateways[1], c.activeGateway)
    }

    @Test
    fun runtimeSubscribeAndUnsubscribe() {
        val c = controller()
        // Not connected yet: ignored.
        c.subscribe(Subscription("x", 2))
        c.unsubscribe("x")
        c.start()
        val g = gateways.single()
        c.subscribe(Subscription("x", 2))
        c.unsubscribe("x")
        assertEquals("x" to 2, g.subscriptions.last())
        assertEquals(listOf("x"), g.unsubscriptions)
        // Errors are swallowed; resubscribe happens on reconnect.
        g.subscribeError = IOException("x")
        c.subscribe(Subscription("y", 0))
        c.unsubscribe("y")
    }

    @Test
    fun listenerForwardsMessagesAndConnectionEvents() {
        val c = controller()
        c.start()
        val g = gateways.single()
        val msg = ReceivedMessage("a/1", "hi", 1, false, 0)
        g.listener!!.onMessage(msg)
        assertEquals(listOf(msg), messages)

        g.listener!!.onConnectionLost(IOException("lost"))
        assertEquals(ConnectionState.WAITING_RETRY, status.value.state)
        assertTrue(status.value.detail.contains("lost"))

        g.subscriptions.clear()
        g.listener!!.onReconnected()
        assertEquals(ConnectionState.CONNECTED, status.value.state)
        assertEquals(listOf("a/#" to 1, "b" to 0), g.subscriptions)
    }

    @Test
    fun reconnectedAfterStopOnlyUpdatesStatus() {
        val c = controller()
        c.start()
        val listener = gateways.single().listener!!
        c.stop()
        gateways.single().subscriptions.clear()
        listener.onReconnected()
        assertTrue(gateways.single().subscriptions.isEmpty())
    }

    @Test
    fun defaultRetryDelays() {
        assertEquals(5, ConnectionController.DEFAULT_RETRY_DELAYS_MS.size)
        val c = ConnectionController({ config }, { emptyList() }, { FakeGateway() }, runner, status, {})
        c.start()
        assertEquals(ConnectionState.CONNECTED, status.value.state)
    }
}
