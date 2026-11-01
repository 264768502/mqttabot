package com.mqttabot.e2e

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mqttabot.AppGraph
import com.mqttabot.R
import com.mqttabot.mqtt.BrokerConfig
import com.mqttabot.mqtt.ConnectionState
import com.mqttabot.service.MqttService
import com.mqttabot.tasker.PublishActionActivity
import com.mqttabot.tasker.TAG_TASKER_SAVE
import com.mqttabot.tasker.TaskerApi
import com.mqttabot.testutil.EmbeddedBroker
import com.mqttabot.testutil.FakeTasker
import com.mqttabot.testutil.TestClient
import com.mqttabot.testutil.awaitUntil
import com.mqttabot.testutil.onMain
import com.mqttabot.testutil.text
import com.mqttabot.ui.MainActivity
import com.mqttabot.ui.TAG_PAYLOAD
import com.mqttabot.ui.TAG_PUBLISH_RESULT
import com.mqttabot.ui.TAG_QOS
import com.mqttabot.ui.TAG_RETAIN
import com.mqttabot.ui.TAG_SEND
import com.mqttabot.ui.TAG_TOPIC
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/**
 * End-to-end: sending messages. Real Paho client in the app, real MQTT broker (with
 * username/password auth) and an independent subscriber verifying what arrives.
 */
@RunWith(AndroidJUnit4::class)
class PublishEndToEndTest {
    @get:Rule val compose = createEmptyComposeRule()

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
            ActivityScenario.launchActivityForResult<PublishActionActivity>(
                Intent(TaskerApi.ACTION_EDIT_SETTING).setClass(app, PublishActionActivity::class.java),
            )
        compose.onNodeWithTag(TAG_TOPIC).performTextReplacement("home/%room/light")
        compose.onNodeWithTag(TAG_PAYLOAD).performTextReplacement("%state")
        compose.onNodeWithTag("$TAG_QOS-1").performScrollTo().performClick()
        compose.onNodeWithTag(TAG_TASKER_SAVE).performScrollTo().performClick()
        val stored = edit.result.resultData.getBundleExtra(TaskerApi.EXTRA_BUNDLE)!!

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

        val main = ActivityScenario.launch(MainActivity::class.java)
        compose.onMain(TAG_TOPIC).performTextReplacement("home/garage/door")
        compose.onMain(TAG_PAYLOAD).performTextReplacement("close")
        compose.onMain("$TAG_QOS-2").performClick()
        compose.onMain(TAG_RETAIN).performClick()
        compose.onMain(TAG_SEND).performClick()

        val ok = app.getString(R.string.publish_ok, "home/garage/door")
        awaitUntil(message = "publish result") { compose.onMain(TAG_PUBLISH_RESULT).text() == ok }
        val (topic, message) = observer.await()
        assertEquals("home/garage/door", topic)
        assertEquals("close", String(message.payload))
        assertEquals(2, message.qos)
        // Only the service's client (plus our observer) ever connected: no one-shot connection was needed.
        assertEquals(setOf("mqttabot-e2e"), broker.connectedClientIds().filterNot { it.startsWith("test-") }.toSet())
        main.close()
        service.destroy()
    }

    private companion object {
        const val USER = "tasker"
        const val PASS = "s3cret"
    }
}
