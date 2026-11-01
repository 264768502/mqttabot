package com.mqttabot.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mqttabot.R
import com.mqttabot.mqtt.ConnectionState
import com.mqttabot.mqtt.ConnectionStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatusTextTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun describesEveryState() {
        assertEquals(context.getString(R.string.status_disconnected), ConnectionStatus.DISCONNECTED.describe(context))
        assertEquals(
            context.getString(R.string.status_connecting, "u"),
            ConnectionStatus(ConnectionState.CONNECTING, "u").describe(context),
        )
        assertEquals(context.getString(R.string.status_connected, "u"), ConnectionStatus(ConnectionState.CONNECTED, "u").describe(context))
        assertEquals(
            context.getString(R.string.status_waiting_retry, "e"),
            ConnectionStatus(ConnectionState.WAITING_RETRY, "e").describe(context),
        )
    }

    @Test
    fun everyStateHasItsOwnStyle() {
        val styles = ConnectionState.entries.map { it.style }
        assertEquals(ConnectionState.entries.size, styles.map { it.icon }.toSet().size)
        assertEquals(ConnectionState.entries.size, styles.map { it.container }.toSet().size)
        assertEquals(listOf(ConnectionState.CONNECTING), ConnectionState.entries.filter { it.style.busy })
        assertEquals(listOf(ConnectionState.WAITING_RETRY), ConnectionState.entries.filter { it.style.showDetail })
        assertEquals(R.string.status_disconnected, ConnectionState.DISCONNECTED.style.headline)
        assertEquals(R.string.state_connected, ConnectionState.CONNECTED.style.headline)
        assertEquals(styles.size, styles.map { it.onContainer }.toSet().size)
    }
}
