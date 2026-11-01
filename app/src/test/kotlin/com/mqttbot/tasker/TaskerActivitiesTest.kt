package com.mqttbot.tasker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.textfield.TextInputLayout
import com.mqttbot.AppGraph
import com.mqttbot.R
import com.mqttbot.data.Subscription
import com.mqttbot.mqtt.BrokerConfig
import com.mqttbot.mqtt.ConnectionController
import com.mqttbot.mqtt.ConnectionStatus
import com.mqttbot.mqtt.Observable
import com.mqttbot.testutil.FakeGateway
import com.mqttbot.testutil.ManualTaskRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class TaskerActivitiesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private inline fun <reified A : Activity> launch(action: String, bundle: android.os.Bundle? = null): A {
        val intent = Intent(action).setClass(context, A::class.java)
        bundle?.let { intent.putExtra(TaskerApi.EXTRA_BUNDLE, it) }
        return Robolectric.buildActivity(A::class.java, intent).setup().get()
    }

    @Test
    fun publishActionSavesBundleAndBlurb() {
        val a = launch<PublishActionActivity>(TaskerApi.ACTION_EDIT_SETTING)
        a.findViewById<EditText>(R.id.topic_input).setText(" home/%room/cmd ")
        a.findViewById<EditText>(R.id.payload_input).setText("%par1")
        a.findViewById<Spinner>(R.id.qos_spinner).setSelection(2)
        a.findViewById<CheckBox>(R.id.retain_check).isChecked = true
        a.findViewById<Button>(R.id.save_button).performClick()

        val shadow = shadowOf(a)
        assertTrue(a.isFinishing)
        assertEquals(Activity.RESULT_OK, shadow.resultCode)
        val result = shadow.resultIntent
        assertEquals(
            PublishActionConfig("home/%room/cmd", "%par1", 2, true),
            PublishActionConfig.fromBundle(result.getBundleExtra(TaskerApi.EXTRA_BUNDLE)),
        )
        assertEquals("Publish to home/%room/cmd (QoS 2)", result.getStringExtra(TaskerApi.EXTRA_STRING_BLURB))
    }

    @Test
    fun publishActionPrefillsAndValidates() {
        val a = launch<PublishActionActivity>(TaskerApi.ACTION_EDIT_SETTING, PublishActionConfig("t", "p", 9, true).toBundle())
        assertEquals("t", a.findViewById<EditText>(R.id.topic_input).text.toString())
        assertEquals("p", a.findViewById<EditText>(R.id.payload_input).text.toString())
        assertEquals(2, a.findViewById<Spinner>(R.id.qos_spinner).selectedItemPosition)
        assertTrue(a.findViewById<CheckBox>(R.id.retain_check).isChecked)

        a.findViewById<EditText>(R.id.topic_input).setText("bad/#")
        a.findViewById<Button>(R.id.save_button).performClick()
        assertFalse(a.isFinishing)
        assertNotNull(a.findViewById<TextInputLayout>(R.id.topic_layout).error)
    }

    @Test
    fun publishActionCancel() {
        val a = launch<PublishActionActivity>(TaskerApi.ACTION_EDIT_SETTING)
        a.findViewById<Button>(R.id.cancel_button).performClick()
        assertTrue(a.isFinishing)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(a).resultCode)
    }

    @Test
    fun eventConfigSavesAndSubscribes() {
        AppGraph.settings.saveBroker(BrokerConfig(host = "h"))
        val gateway = FakeGateway()
        val controller = ConnectionController(
            { BrokerConfig(host = "h") }, { emptyList() }, { gateway }, ManualTaskRunner(), Observable(ConnectionStatus.DISCONNECTED), {},
        ).also { it.start() }
        AppGraph.controller = controller

        val a = launch<EventConfigActivity>(TaskerApi.ACTION_EDIT_EVENT)
        assertEquals(1, a.findViewById<Spinner>(R.id.qos_spinner).selectedItemPosition)
        a.findViewById<EditText>(R.id.filter_input).setText("sensors/+/temp")
        a.findViewById<EditText>(R.id.pattern_input).setText("^\\d+")
        a.findViewById<Spinner>(R.id.qos_spinner).setSelection(2)
        a.findViewById<Button>(R.id.save_button).performClick()

        val shadow = shadowOf(a)
        assertEquals(Activity.RESULT_OK, shadow.resultCode)
        val result = shadow.resultIntent
        assertEquals(EventFilterConfig("sensors/+/temp", "^\\d+", 2), EventFilterConfig.fromBundle(result.getBundleExtra(TaskerApi.EXTRA_BUNDLE)))
        assertEquals("Message on sensors/+/temp", result.getStringExtra(TaskerApi.EXTRA_STRING_BLURB))
        val vars = result.getStringArrayExtra(TaskerApi.EXTRA_RELEVANT_VARIABLES)!!
        assertTrue(vars.all { it.startsWith("%mqtt_") })
        assertEquals(5, vars.size)
        assertEquals(listOf(Subscription("sensors/+/temp", 2)), AppGraph.settings.subscriptions())
        assertEquals("sensors/+/temp" to 2, gateway.subscriptions.last())
    }

    @Test
    fun eventConfigWithoutServiceStillStoresSubscription() {
        val a = launch<EventConfigActivity>(TaskerApi.ACTION_EDIT_EVENT, EventFilterConfig("a/#", "x", 7).toBundle())
        assertEquals("a/#", a.findViewById<EditText>(R.id.filter_input).text.toString())
        assertEquals("x", a.findViewById<EditText>(R.id.pattern_input).text.toString())
        assertEquals(2, a.findViewById<Spinner>(R.id.qos_spinner).selectedItemPosition)
        a.findViewById<Button>(R.id.save_button).performClick()
        assertEquals(listOf(Subscription("a/#", 2)), AppGraph.settings.subscriptions())
    }

    @Test
    fun eventConfigValidation() {
        val a = launch<EventConfigActivity>(TaskerApi.ACTION_EDIT_EVENT)
        val filterLayout = a.findViewById<TextInputLayout>(R.id.filter_layout)
        val patternLayout = a.findViewById<TextInputLayout>(R.id.pattern_layout)
        a.findViewById<EditText>(R.id.filter_input).setText("a/#/b")
        a.findViewById<Button>(R.id.save_button).performClick()
        assertNotNull(filterLayout.error)
        assertFalse(a.isFinishing)

        a.findViewById<EditText>(R.id.filter_input).setText("a/#")
        a.findViewById<EditText>(R.id.pattern_input).setText("[")
        a.findViewById<Button>(R.id.save_button).performClick()
        assertNull(filterLayout.error)
        assertNotNull(patternLayout.error)
        assertFalse(a.isFinishing)
        assertTrue(AppGraph.settings.subscriptions().isEmpty())
    }

    @Test
    fun eventConfigCancel() {
        val a = launch<EventConfigActivity>(TaskerApi.ACTION_EDIT_EVENT)
        a.findViewById<Button>(R.id.cancel_button).performClick()
        assertEquals(Activity.RESULT_CANCELED, shadowOf(a).resultCode)
        assertTrue(a.isFinishing)
    }

    @Test
    fun manifestExposesTaskerPlugins() {
        val pm = context.packageManager
        fun activities(action: String) = pm.queryIntentActivities(Intent(action).setPackage(context.packageName), 0)
            .map { it.activityInfo.name }
        fun receivers(action: String) = pm.queryBroadcastReceivers(Intent(action).setPackage(context.packageName), 0)
            .map { it.activityInfo.name }
        assertEquals(listOf(PublishActionActivity::class.java.name), activities(TaskerApi.ACTION_EDIT_SETTING))
        assertEquals(listOf(EventConfigActivity::class.java.name), activities(TaskerApi.ACTION_EDIT_EVENT))
        assertEquals(listOf(FirePublishReceiver::class.java.name), receivers(TaskerApi.ACTION_FIRE_SETTING))
        assertEquals(listOf(QueryEventReceiver::class.java.name), receivers(TaskerApi.ACTION_QUERY_CONDITION))
    }
}
