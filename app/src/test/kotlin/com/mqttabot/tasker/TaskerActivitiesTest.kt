package com.mqttabot.tasker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mqttabot.AppGraph
import com.mqttabot.R
import com.mqttabot.data.Subscription
import com.mqttabot.mqtt.BrokerConfig
import com.mqttabot.mqtt.ConnectionController
import com.mqttabot.mqtt.ConnectionStatus
import com.mqttabot.mqtt.Observable
import com.mqttabot.testutil.FakeGateway
import com.mqttabot.testutil.ManualTaskRunner
import com.mqttabot.testutil.text
import com.mqttabot.ui.TAG_FILTER
import com.mqttabot.ui.TAG_PAYLOAD
import com.mqttabot.ui.TAG_QOS
import com.mqttabot.ui.TAG_RETAIN
import com.mqttabot.ui.TAG_TOPIC
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskerActivitiesTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var scenario: ActivityScenario<out Activity>? = null

    @After
    fun tearDown() {
        scenario?.close()
    }

    private inline fun <reified A : Activity> launch(
        action: String,
        bundle: Bundle? = null,
    ): ActivityScenario<A> {
        val intent = Intent(action).setClass(context, A::class.java)
        bundle?.let { intent.putExtra(TaskerApi.EXTRA_BUNDLE, it) }
        return ActivityScenario.launchActivityForResult<A>(intent).also { scenario = it }
    }

    private fun node(tag: String) = compose.onNodeWithTag(tag).performScrollTo()

    private fun assertNotFinishing() = scenario!!.onActivity { assertFalse(it.isFinishing) }

    @Test
    fun publishActionSavesBundleAndBlurb() {
        val s = launch<PublishActionActivity>(TaskerApi.ACTION_EDIT_SETTING)
        compose.onNodeWithText(context.getString(R.string.tasker_publish_hint)).assertExists()
        node(TAG_TOPIC).performTextReplacement(" home/%room/cmd ")
        node(TAG_PAYLOAD).performTextReplacement("%par1")
        node("$TAG_QOS-2").performClick()
        node(TAG_RETAIN).performClick()
        node(TAG_TASKER_SAVE).performClick()

        assertEquals(Activity.RESULT_OK, s.result.resultCode)
        val result = s.result.resultData
        assertEquals(
            PublishActionConfig("home/%room/cmd", "%par1", 2, true),
            PublishActionConfig.fromBundle(result.getBundleExtra(TaskerApi.EXTRA_BUNDLE)),
        )
        assertEquals("Publish to home/%room/cmd (QoS 2)", result.getStringExtra(TaskerApi.EXTRA_STRING_BLURB))
    }

    @Test
    fun publishActionPrefillsAndValidates() {
        launch<PublishActionActivity>(TaskerApi.ACTION_EDIT_SETTING, PublishActionConfig("t", "p", 9, true).toBundle())
        assertEquals("t", node(TAG_TOPIC).text())
        assertEquals("p", node(TAG_PAYLOAD).text())
        node("$TAG_QOS-2").assertIsOn()
        node(TAG_RETAIN).assertIsOn()

        node(TAG_TOPIC).performTextReplacement("bad/#")
        node(TAG_TASKER_SAVE).performClick()
        compose.onNodeWithText(context.getString(R.string.error_topic)).assertExists()
        assertNotFinishing()
    }

    @Test
    fun publishActionCancel() {
        val s = launch<PublishActionActivity>(TaskerApi.ACTION_EDIT_SETTING)
        node(TAG_CANCEL).performClick()
        assertEquals(Activity.RESULT_CANCELED, s.result.resultCode)
    }

    @Test
    fun eventConfigSavesAndSubscribes() {
        AppGraph.settings.saveBroker(BrokerConfig(host = "h"))
        val gateway = FakeGateway()
        AppGraph.controller =
            ConnectionController(
                { BrokerConfig(host = "h") },
                { emptyList() },
                { gateway },
                ManualTaskRunner(),
                Observable(ConnectionStatus.DISCONNECTED),
                {},
            ).also { it.start() }

        val s = launch<EventConfigActivity>(TaskerApi.ACTION_EDIT_EVENT)
        // New events default to QoS 1.
        node("$TAG_QOS-1").assertIsOn()
        node(TAG_FILTER).performTextReplacement("sensors/+/temp")
        node(TAG_PATTERN).performTextReplacement("^\\d+")
        node("$TAG_QOS-2").performClick()
        node(TAG_TASKER_SAVE).performClick()

        assertEquals(Activity.RESULT_OK, s.result.resultCode)
        val result = s.result.resultData
        assertEquals(
            EventFilterConfig("sensors/+/temp", "^\\d+", 2),
            EventFilterConfig.fromBundle(result.getBundleExtra(TaskerApi.EXTRA_BUNDLE)),
        )
        assertEquals("Message on sensors/+/temp", result.getStringExtra(TaskerApi.EXTRA_STRING_BLURB))
        val vars = result.getStringArrayExtra(TaskerApi.EXTRA_RELEVANT_VARIABLES)!!
        assertTrue(vars.all { it.startsWith("%mqtt_") })
        assertEquals(5, vars.size)
        assertEquals(listOf(Subscription("sensors/+/temp", 2)), AppGraph.settings.subscriptions())
        assertEquals("sensors/+/temp" to 2, gateway.subscriptions.last())
    }

    @Test
    fun eventConfigWithoutServiceStillStoresSubscription() {
        launch<EventConfigActivity>(TaskerApi.ACTION_EDIT_EVENT, EventFilterConfig("a/#", "x", 7).toBundle())
        assertEquals("a/#", node(TAG_FILTER).text())
        assertEquals("x", node(TAG_PATTERN).text())
        node("$TAG_QOS-2").assertIsOn()
        node(TAG_TASKER_SAVE).performClick()
        assertEquals(listOf(Subscription("a/#", 2)), AppGraph.settings.subscriptions())
    }

    @Test
    fun eventConfigValidation() {
        launch<EventConfigActivity>(TaskerApi.ACTION_EDIT_EVENT)
        node(TAG_FILTER).performTextReplacement("a/#/b")
        node(TAG_TASKER_SAVE).performClick()
        compose.onNodeWithText(context.getString(R.string.error_filter)).assertExists()
        assertNotFinishing()

        node(TAG_FILTER).performTextReplacement("a/#")
        node(TAG_PATTERN).performTextReplacement("[")
        node(TAG_TASKER_SAVE).performClick()
        compose.onNodeWithText(context.getString(R.string.error_filter)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.error_pattern)).assertExists()
        assertNotFinishing()
        assertTrue(AppGraph.settings.subscriptions().isEmpty())
    }

    @Test
    fun eventConfigCancel() {
        val s = launch<EventConfigActivity>(TaskerApi.ACTION_EDIT_EVENT)
        node(TAG_CANCEL).performClick()
        assertEquals(Activity.RESULT_CANCELED, s.result.resultCode)
    }

    @Test
    fun manifestExposesTaskerPlugins() {
        val pm = context.packageManager

        fun activities(action: String) =
            pm
                .queryIntentActivities(Intent(action).setPackage(context.packageName), 0)
                .map { it.activityInfo.name }

        fun receivers(action: String) =
            pm
                .queryBroadcastReceivers(Intent(action).setPackage(context.packageName), 0)
                .map { it.activityInfo.name }
        assertEquals(listOf(PublishActionActivity::class.java.name), activities(TaskerApi.ACTION_EDIT_SETTING))
        assertEquals(listOf(EventConfigActivity::class.java.name), activities(TaskerApi.ACTION_EDIT_EVENT))
        assertEquals(listOf(FirePublishReceiver::class.java.name), receivers(TaskerApi.ACTION_FIRE_SETTING))
        assertEquals(listOf(QueryEventReceiver::class.java.name), receivers(TaskerApi.ACTION_QUERY_CONDITION))
    }
}
