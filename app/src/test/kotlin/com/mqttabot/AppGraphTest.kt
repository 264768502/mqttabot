package com.mqttabot

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mqttabot.data.ThemeMode
import com.mqttabot.mqtt.ConnectionState
import com.mqttabot.mqtt.ConnectionStatus
import com.mqttabot.mqtt.ExecutorTaskRunner
import com.mqttabot.mqtt.PahoMqttGateway
import com.mqttabot.mqtt.ReceivedMessage
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
        AppGraph.dynamicColor = false
        AppGraph.clock = { 0L }
        AppGraph.setThemeMode(ThemeMode.LIGHT)
        val seen = mutableListOf<ConnectionStatus>()
        AppGraph.status.observe { seen += it }
        AppGraph.init(context)
        assertEquals(ConnectionStatus.DISCONNECTED, AppGraph.status.value)
        assertTrue(
            AppGraph.messageLog.updates.value
                .isEmpty(),
        )
        assertNull(AppGraph.controller)
        assertTrue(AppGraph.dynamicColor)
        assertTrue(AppGraph.clock() > 0)
        // The persisted theme is restored.
        assertEquals(ThemeMode.LIGHT, AppGraph.themeMode.value)
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
