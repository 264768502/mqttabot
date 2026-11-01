package com.mqttabot

import android.app.Application
import android.app.Notification
import android.os.Build
import android.widget.Button
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mqttabot.mqtt.BrokerConfig
import com.mqttabot.mqtt.ConnectionState
import com.mqttabot.mqtt.ReceivedMessage
import com.mqttabot.service.MqttService
import com.mqttabot.tasker.EventFilterConfig
import com.mqttabot.tasker.TaskerApi
import com.mqttabot.testutil.FakeGateway
import com.mqttabot.testutil.FakeTasker
import com.mqttabot.testutil.ManualTaskRunner
import com.mqttabot.ui.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Smoke test of the main flows on the minimum supported version, Android 7.0 (API 24). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.N])
class MinSdkCompatTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun serviceUiAndTaskerWorkOnAndroid7() {
        assertEquals(24, Build.VERSION.SDK_INT)
        AppGraph.settings.saveBroker(BrokerConfig(host = "h", clientId = "c"))
        val gateway = FakeGateway()
        AppGraph.gatewayFactory = { gateway }
        AppGraph.taskRunnerFactory = { ManualTaskRunner() }

        // Starting from the UI uses plain startService below API 26.
        val main = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        main.findViewById<Button>(R.id.service_button).performClick()
        assertEquals(MqttService.ACTION_START, shadowOf(app).nextStartedService.action)

        val service =
            Robolectric
                .buildService(MqttService::class.java, MqttService.intent(app, MqttService.ACTION_START))
                .create()
                .startCommand(0, 1)
        val notification = shadowOf(service.get()).lastForegroundNotification
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(ConnectionState.CONNECTED, AppGraph.status.value.state)

        FakeTasker(app).use { tasker ->
            gateway.listener!!.onMessage(ReceivedMessage("a/b", "x", 0, false, 1))
            shadowOf(android.os.Looper.getMainLooper()).idle()
            val result = tasker.queryEvent(EventFilterConfig("a/#", "", 0).toBundle(), tasker.requests.single())
            assertEquals(TaskerApi.RESULT_CONDITION_SATISFIED, result.resultCode)
            assertEquals("x", result.variables!!.getString(TaskerApi.VAR_PAYLOAD))
        }
        service.destroy()
        assertEquals(ConnectionState.DISCONNECTED, AppGraph.status.value.state)
    }
}
