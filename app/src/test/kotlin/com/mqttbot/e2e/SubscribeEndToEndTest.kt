package com.mqttbot.e2e

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mqttbot.AppGraph
import com.mqttbot.R
import com.mqttbot.data.Subscription
import com.mqttbot.mqtt.BrokerConfig
import com.mqttbot.mqtt.ConnectionState
import com.mqttbot.service.MqttService
import com.mqttbot.service.ServiceNotifications
import com.mqttbot.tasker.EventConfigActivity
import com.mqttbot.tasker.TaskerApi
import com.mqttbot.testutil.EmbeddedBroker
import com.mqttbot.testutil.FakeTasker
import com.mqttbot.testutil.TestClient
import com.mqttbot.testutil.awaitUntil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/**
 * End-to-end: subscribing. A message published by another client to a real broker is received
 * by the foreground service and triggers the Tasker event with the expected variables.
 */
@RunWith(AndroidJUnit4::class)
class SubscribeEndToEndTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val broker = EmbeddedBroker(USER, PASS).start()
    private val tasker = FakeTasker(app)

    @After
    fun tearDown() {
        AppGraph.controller?.stop()
        tasker.close()
        broker.close()
    }

    @Test
    fun brokerMessageTriggersTaskerEvent() {
        AppGraph.settings.saveBroker(
            BrokerConfig(host = "127.0.0.1", port = broker.port, username = USER, password = PASS, clientId = "mqttbot-e2e"),
        )

        // 1. The user adds the "MQTT message received" event in Tasker and configures it.
        val edit = Robolectric.buildActivity(
            EventConfigActivity::class.java,
            Intent(TaskerApi.ACTION_EDIT_EVENT).setClass(app, EventConfigActivity::class.java),
        ).setup().get()
        edit.findViewById<EditText>(R.id.filter_input).setText("sensors/+/temperature")
        edit.findViewById<EditText>(R.id.pattern_input).setText("^\\d+(\\.\\d+)?$")
        edit.findViewById<Spinner>(R.id.qos_spinner).setSelection(1)
        edit.findViewById<Button>(R.id.save_button).performClick()
        val eventConfig = shadowOf(edit).resultIntent.getBundleExtra(TaskerApi.EXTRA_BUNDLE)!!
        assertEquals(listOf(Subscription("sensors/+/temperature", 1)), AppGraph.settings.subscriptions())

        // 2. The foreground service connects and subscribes.
        val service = Robolectric.buildService(MqttService::class.java, MqttService.intent(app, MqttService.ACTION_START))
            .create().startCommand(0, 1)
        val shadowService = shadowOf(service.get())
        assertTrue(shadowService.lastForegroundNotification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        awaitUntil(message = "service connected") { AppGraph.status.value.state == ConnectionState.CONNECTED }

        // 3. Another device publishes: one match, one payload mismatch, one unsubscribed topic.
        TestClient(broker.port, USER, PASS).use { device ->
            device.publish("sensors/kitchen/temperature", "n/a")
            device.publish("sensors/livingroom/humidity", "40")
            device.publish("sensors/livingroom/temperature", "21.5")
        }
        awaitUntil(message = "2 Tasker requests") { tasker.requests.size == 2 }

        // 4. Tasker queries the plugin for each request; only the matching message fires the event.
        val results = tasker.requests.map { tasker.queryEvent(eventConfig, it) }
        assertEquals(
            listOf(TaskerApi.RESULT_CONDITION_UNSATISFIED, TaskerApi.RESULT_CONDITION_SATISFIED),
            results.map { it.resultCode },
        )
        val vars = results[1].variables!!
        assertEquals("sensors/livingroom/temperature", vars.getString(TaskerApi.VAR_TOPIC))
        assertEquals("21.5", vars.getString(TaskerApi.VAR_PAYLOAD))
        assertEquals("1", vars.getString(TaskerApi.VAR_QOS))
        assertEquals("false", vars.getString(TaskerApi.VAR_RETAINED))
        assertEquals(listOf("21.5", "n/a"), AppGraph.messageLog.updates.value.map { it.payload })

        // 5. After the broker drops the connection, the service reconnects and resubscribes.
        broker.disconnectAll()
        awaitUntil(message = "connection lost") { AppGraph.status.value.state != ConnectionState.CONNECTED }
        awaitUntil(timeoutMillis = 20_000, message = "reconnected") { AppGraph.status.value.state == ConnectionState.CONNECTED }
        TestClient(broker.port, USER, PASS).use { it.publish("sensors/attic/temperature", "30") }
        awaitUntil(message = "3rd Tasker request") { tasker.requests.size == 3 }
        val third = tasker.queryEvent(eventConfig, tasker.requests[2])
        assertEquals(TaskerApi.RESULT_CONDITION_SATISFIED, third.resultCode)
        assertEquals("sensors/attic/temperature", third.variables!!.getString(TaskerApi.VAR_TOPIC))

        val nm = app.getSystemService(NotificationManager::class.java)
        val text = shadowOf(nm).getNotification(ServiceNotifications.NOTIFICATION_ID).extras.getString(Notification.EXTRA_TEXT)
        assertEquals(app.getString(R.string.status_connected, "tcp://127.0.0.1:${broker.port}"), text)

        // 6. Stopping the service disconnects from the broker.
        service.destroy()
        awaitUntil(message = "disconnected") { "mqttbot-e2e" !in broker.connectedClientIds() }
        assertEquals(ConnectionState.DISCONNECTED, AppGraph.status.value.state)
    }

    private companion object {
        const val USER = "device"
        const val PASS = "pa55"
    }
}
