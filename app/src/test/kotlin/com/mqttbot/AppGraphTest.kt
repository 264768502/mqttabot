package com.mqttbot

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mqttbot.mqtt.ConnectionState
import com.mqttbot.mqtt.ConnectionStatus
import com.mqttbot.mqtt.ExecutorTaskRunner
import com.mqttbot.mqtt.PahoMqttGateway
import com.mqttbot.mqtt.ReceivedMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppGraphTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun initResetsState() {
        AppGraph.status.set(ConnectionStatus(ConnectionState.CONNECTED))
        AppGraph.messageLog.add(ReceivedMessage("t", "p", 0, false, 0))
        AppGraph.controller = AppGraph.newController {}
        val seen = mutableListOf<ConnectionStatus>()
        AppGraph.status.observe { seen += it }
        AppGraph.init(context)
        assertEquals(ConnectionStatus.DISCONNECTED, AppGraph.status.value)
        assertTrue(AppGraph.messageLog.updates.value.isEmpty())
        assertNull(AppGraph.controller)
        AppGraph.status.set(ConnectionStatus(ConnectionState.CONNECTING))
        assertEquals(1, seen.size)
    }

    @Test
    fun defaultFactories() {
        assertTrue(AppGraph.gatewayFactory() is PahoMqttGateway)
        val runner = AppGraph.taskRunnerFactory()
        assertTrue(runner is ExecutorTaskRunner)
        runner.shutdown()
        AppGraph.publisher()
    }
}
