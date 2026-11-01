package com.mqttbot.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mqttbot.R
import com.mqttbot.mqtt.ConnectionState
import com.mqttbot.mqtt.ConnectionStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatusTextTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun describesEveryState() {
        assertEquals(context.getString(R.string.status_disconnected), ConnectionStatus.DISCONNECTED.describe(context))
        assertEquals(context.getString(R.string.status_connecting, "u"), ConnectionStatus(ConnectionState.CONNECTING, "u").describe(context))
        assertEquals(context.getString(R.string.status_connected, "u"), ConnectionStatus(ConnectionState.CONNECTED, "u").describe(context))
        assertEquals(context.getString(R.string.status_waiting_retry, "e"), ConnectionStatus(ConnectionState.WAITING_RETRY, "e").describe(context))
    }

    @Test
    fun qosAdapterHasThreeLevels() {
        assertEquals(3, QosAdapter.create(context).count)
    }
}
