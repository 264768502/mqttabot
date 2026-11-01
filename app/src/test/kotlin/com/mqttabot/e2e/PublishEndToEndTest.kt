package com.mqttabot.e2e

import android.app.Application
import android.content.Intent
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mqttabot.AppGraph
import com.mqttabot.R
import com.mqttabot.mqtt.BrokerConfig
import com.mqttabot.mqtt.ConnectionState
import com.mqttabot.service.MqttService
import com.mqttabot.tasker.PublishActionActivity
import com.mqttabot.tasker.TaskerApi
import com.mqttabot.testutil.EmbeddedBroker
import com.mqttabot.testutil.FakeTasker
import com.mqttabot.testutil.TestClient
import com.mqttabot.testutil.awaitUntil
import com.mqttabot.ui.MainActivity
import com.mqttabot.ui.QosToggleGroup
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/**
 * End-to-end: sending messages. Real Paho client in the app, real MQTT broker (with
 * username/password auth) and an independent subscriber verifying what arrives.
 */
@RunWith(AndroidJUnit4::class)
class PublishEndToEndTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val broker = EmbeddedBroker(USER, PASS).start()
    private val observer = TestClient(broker.port, USER, PASS).apply { subscribe("home/#", 2) }
    private val tasker = FakeTasker(app)

    @Before
    fun configureBroker() {
        AppGraph.settings.saveBroker(
            BrokerConfig(host = "127.0.0.1", port = broker.port, username = USER, password = PASS, clientId = "mqttabot-e2e"),
        )
    }

    @After
    fun tearDown() {
        AppGraph.controller?.stop()
        tasker.close()
        observer.close()
        broker.close()
    }

    @Test
    fun taskerActionPublishesToBroker() {
        // 1. Tasker opens the plugin's edit screen; the user configures the action.
        val edit =
            Robolectric
                .buildActivity(
                    PublishActionActivity::class.java,
                    Intent(TaskerApi.ACTION_EDIT_SETTING).setClass(app, PublishActionActivity::class.java),
                ).setup()
                .get()
        edit.findViewById<EditText>(R.id.topic_input).setText("home/%room/light")
        edit.findViewById<EditText>(R.id.payload_input).setText("%state")
        edit.findViewById<QosToggleGroup>(R.id.qos_group).qos = 1
        edit.findViewById<CompoundButton>(R.id.retain_check).isChecked = false
        edit.findViewById<Button>(R.id.save_button).performClick()
        val stored = shadowOf(edit).resultIntent.getBundleExtra(TaskerApi.EXTRA_BUNDLE)!!

        // 2. When the task runs, Tasker substitutes variables and fires the action.
        tasker.fireAction(tasker.substitute(stored, mapOf("%room" to "kitchen", "%state" to "ON")))

        // 3. The message reaches an independent subscriber through the broker.
        val (topic, message) = observer.await()
        assertEquals("home/kitchen/light", topic)
        assertEquals("ON", String(message.payload))
        assertEquals(1, message.qos)
        assertFalse(message.isRetained)
    }

    @Test
    fun appPublishesThroughRunningServiceConnection() {
        // Start the foreground service so publishing reuses its long-lived connection.
        val service =
            Robolectric
                .buildService(MqttService::class.java, MqttService.intent(app, MqttService.ACTION_START))
                .create()
                .startCommand(0, 1)
        awaitUntil(message = "service connected") { AppGraph.status.value.state == ConnectionState.CONNECTED }

        val main = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        main.findViewById<EditText>(R.id.topic_input).setText("home/garage/door")
        main.findViewById<EditText>(R.id.payload_input).setText("close")
        main.findViewById<QosToggleGroup>(R.id.qos_group).qos = 2
        main.findViewById<CompoundButton>(R.id.retain_check).isChecked = true
        main.findViewById<Button>(R.id.send_button).performClick()

        val result = main.findViewById<TextView>(R.id.publish_result)
        awaitUntil(message = "publish result") { result.text.toString() == app.getString(R.string.publish_ok, "home/garage/door") }
        val (topic, message) = observer.await()
        assertEquals("home/garage/door", topic)
        assertEquals("close", String(message.payload))
        assertEquals(2, message.qos)
        // Only the service's client (plus our observer) ever connected: no one-shot connection was needed.
        assertEquals(setOf("mqttabot-e2e"), broker.connectedClientIds().filterNot { it.startsWith("test-") }.toSet())
        service.destroy()
    }

    private companion object {
        const val USER = "tasker"
        const val PASS = "s3cret"
    }
}
