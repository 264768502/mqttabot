package com.mqttbot.testutil

import com.mqttbot.mqtt.BrokerConfig
import com.mqttbot.mqtt.MqttGateway
import com.mqttbot.mqtt.TaskRunner
import java.util.concurrent.Future
import java.util.concurrent.FutureTask

/** Scriptable in-memory [MqttGateway]. */
class FakeGateway(
    var connectError: Exception? = null,
    var subscribeError: Exception? = null,
    var publishError: Exception? = null,
) : MqttGateway {
    var connected = false
    var connectedWith: BrokerConfig? = null
    var listener: MqttGateway.Listener? = null
    val subscriptions = mutableListOf<Pair<String, Int>>()
    val unsubscriptions = mutableListOf<String>()
    val published = mutableListOf<Published>()
    var disconnects = 0

    data class Published(val topic: String, val payload: String, val qos: Int, val retained: Boolean)

    override val isConnected: Boolean get() = connected

    override fun connect(config: BrokerConfig, listener: MqttGateway.Listener) {
        connectError?.let { throw it }
        connectedWith = config
        this.listener = listener
        connected = true
    }

    override fun subscribe(filter: String, qos: Int) {
        subscribeError?.let { throw it }
        subscriptions += filter to qos
    }

    override fun unsubscribe(filter: String) {
        subscribeError?.let { throw it }
        unsubscriptions += filter
    }

    override fun publish(topic: String, payload: ByteArray, qos: Int, retained: Boolean) {
        publishError?.let { throw it }
        published += Published(topic, String(payload), qos, retained)
    }

    override fun disconnect() {
        disconnects++
        connected = false
    }
}

/** Runs tasks inline; delayed tasks wait until [runScheduled]. */
class ManualTaskRunner(private val deferExecute: Boolean = false) : TaskRunner {
    val scheduled = mutableListOf<Pair<Long, FutureTask<*>>>()
    val queued = mutableListOf<Runnable>()
    var isShutdown = false

    override fun execute(task: Runnable) {
        if (deferExecute) queued += task else task.run()
    }

    fun runQueued() {
        val tasks = queued.toList()
        queued.clear()
        tasks.forEach { it.run() }
    }

    override fun schedule(delayMillis: Long, task: Runnable): Future<*> =
        FutureTask<Unit>(task, Unit).also { scheduled += delayMillis to it }

    override fun shutdown() {
        isShutdown = true
    }

    fun runScheduled() {
        val tasks = scheduled.toList()
        scheduled.clear()
        tasks.forEach { it.second.run() }
    }
}
