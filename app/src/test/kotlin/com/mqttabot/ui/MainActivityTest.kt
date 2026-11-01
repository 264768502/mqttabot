package com.mqttabot.ui

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputLayout
import com.mqttabot.AppGraph
import com.mqttabot.R
import com.mqttabot.data.Subscription
import com.mqttabot.mqtt.BrokerConfig
import com.mqttabot.mqtt.ConnectionController
import com.mqttabot.mqtt.ConnectionState
import com.mqttabot.mqtt.ConnectionStatus
import com.mqttabot.mqtt.Observable
import com.mqttabot.mqtt.ReceivedMessage
import com.mqttabot.service.MqttService
import com.mqttabot.testutil.FakeGateway
import com.mqttabot.testutil.ManualTaskRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import java.io.IOException
import java.util.concurrent.Executor

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val gateways = mutableListOf<FakeGateway>()
    private var nextGateway: () -> FakeGateway = { FakeGateway() }
    private val pending = mutableListOf<Runnable>()

    @Before
    fun setUp() {
        AppGraph.settings.saveBroker(BrokerConfig(host = "broker", clientId = "c"))
        AppGraph.gatewayFactory = { nextGateway().also { gateways += it } }
        AppGraph.ioExecutor = Executor { pending += it }
    }

    private fun launch(): ActivityController<MainActivity> = Robolectric.buildActivity(MainActivity::class.java).setup()

    private fun runIo() {
        pending.toList().also { pending.clear() }.forEach { it.run() }
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    private fun connectedController(gateway: FakeGateway): ConnectionController =
        ConnectionController(
            { BrokerConfig(host = "broker") },
            { emptyList() },
            { gateway },
            ManualTaskRunner(),
            Observable(ConnectionStatus.DISCONNECTED),
            {},
        ).also { it.start() }

    @Test
    fun showsBrokerAndStatus() {
        val a = launch().get()
        val status = a.findViewById<TextView>(R.id.status_text)
        val detail = a.findViewById<TextView>(R.id.status_detail)
        val icon = a.findViewById<View>(R.id.status_icon)
        val progress = a.findViewById<View>(R.id.status_progress)
        assertEquals("tcp://broker:1883", a.findViewById<TextView>(R.id.broker_text).text.toString())
        assertEquals(app.getString(R.string.status_disconnected), status.text.toString())
        assertEquals(app.getString(R.string.action_start_service), a.findViewById<Button>(R.id.service_button).text.toString())
        assertEquals(View.VISIBLE, icon.visibility)
        assertEquals(View.GONE, progress.visibility)
        assertEquals(View.GONE, detail.visibility)

        AppGraph.status.set(ConnectionStatus(ConnectionState.CONNECTING, "x"))
        assertEquals(app.getString(R.string.state_connecting), status.text.toString())
        assertEquals(View.INVISIBLE, icon.visibility)
        assertEquals(View.VISIBLE, progress.visibility)

        AppGraph.status.set(ConnectionStatus(ConnectionState.WAITING_RETRY, "boom"))
        assertEquals(app.getString(R.string.state_reconnecting), status.text.toString())
        assertEquals(View.VISIBLE, detail.visibility)
        assertEquals("boom", detail.text.toString())
        assertEquals(View.VISIBLE, icon.visibility)
    }

    @Test
    fun startServiceRequestsNotificationPermissionWhenNeeded() {
        val nm = app.getSystemService(NotificationManager::class.java)
        shadowOf(nm).setNotificationsEnabled(false)
        val a = launch().get()
        a.findViewById<Button>(R.id.service_button).performClick()
        val request = shadowOf(a).lastRequestedPermission
        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), request.requestedPermissions.toList())
        assertEquals(MqttService.ACTION_START, shadowOf(app).nextStartedService.action)
    }

    @Test
    fun startServiceWithNotificationsEnabled() {
        val a = launch().get()
        a.findViewById<Button>(R.id.service_button).performClick()
        assertNull(shadowOf(a).lastRequestedPermission)
        assertEquals(MqttService.ACTION_START, shadowOf(app).nextStartedService.action)
    }

    @Test
    fun stopServiceWhenRunning() {
        AppGraph.controller = connectedController(FakeGateway())
        val a = launch().get()
        assertEquals(app.getString(R.string.action_stop_service), a.findViewById<Button>(R.id.service_button).text.toString())
        a.findViewById<Button>(R.id.service_button).performClick()
        assertEquals(MqttService::class.java.name, shadowOf(app).nextStoppedService.component!!.className)
    }

    @Test
    fun publishSuccessAndFailure() {
        val a = launch().get()
        a.findViewById<EditText>(R.id.topic_input).setText(" a/b ")
        a.findViewById<EditText>(R.id.payload_input).setText("hello")
        a.findViewById<QosToggleGroup>(R.id.qos_group).qos = 1
        a.findViewById<CompoundButton>(R.id.retain_check).isChecked = true
        val send = a.findViewById<Button>(R.id.send_button)
        val result = a.findViewById<TextView>(R.id.publish_result)
        assertEquals(View.GONE, result.visibility)
        send.performClick()
        assertEquals(false, send.isEnabled)
        assertEquals(View.VISIBLE, result.visibility)
        assertEquals(app.getString(R.string.publish_sending), result.text.toString())
        runIo()
        assertTrue(send.isEnabled)
        assertEquals(app.getString(R.string.publish_ok, "a/b"), result.text.toString())
        assertEquals(listOf(FakeGateway.Published("a/b", "hello", 1, true)), gateways.single().published)

        nextGateway = { FakeGateway(connectError = IOException("refused")) }
        send.performClick()
        runIo()
        assertTrue(result.text.toString().contains("refused"))
    }

    @Test
    fun publishValidatesTopic() {
        val a = launch().get()
        val layout = a.findViewById<TextInputLayout>(R.id.topic_layout)
        a.findViewById<EditText>(R.id.topic_input).setText("a/+")
        a.findViewById<Button>(R.id.send_button).performClick()
        assertNotNull(layout.error)
        assertTrue(pending.isEmpty())
        a.findViewById<EditText>(R.id.topic_input).setText("a")
        a.findViewById<Button>(R.id.send_button).performClick()
        assertNull(layout.error)
    }

    @Test
    fun addAndRemoveSubscriptions() {
        val gateway = FakeGateway()
        AppGraph.controller = connectedController(gateway)
        val a = launch().get()
        val container = a.findViewById<LinearLayout>(R.id.subscriptions_container)
        val empty = a.findViewById<TextView>(R.id.no_subscriptions)
        assertEquals(View.VISIBLE, empty.visibility)

        val filterLayout = a.findViewById<TextInputLayout>(R.id.filter_layout)
        a.findViewById<EditText>(R.id.filter_input).setText("bad#")
        a.findViewById<Button>(R.id.add_subscription_button).performClick()
        assertNotNull(filterLayout.error)

        a.findViewById<EditText>(R.id.filter_input).setText("home/#")
        a.findViewById<QosToggleGroup>(R.id.filter_qos_group).qos = 2
        a.findViewById<Button>(R.id.add_subscription_button).performClick()
        assertNull(filterLayout.error)
        assertEquals("", a.findViewById<EditText>(R.id.filter_input).text.toString())
        assertEquals(listOf(Subscription("home/#", 2)), AppGraph.settings.subscriptions())
        assertEquals("home/#" to 2, gateway.subscriptions.last())
        assertEquals(1, container.childCount)
        assertEquals(View.GONE, empty.visibility)
        val row = container.getChildAt(0)
        assertEquals("home/#", row.findViewById<TextView>(R.id.subscription_text).text.toString())
        assertEquals(app.getString(R.string.qos_value, 2), row.findViewById<TextView>(R.id.subscription_qos).text.toString())

        container.getChildAt(0).findViewById<Button>(R.id.remove_button).performClick()
        assertTrue(AppGraph.settings.subscriptions().isEmpty())
        assertEquals(listOf("home/#"), gateway.unsubscriptions)
        assertEquals(0, container.childCount)
    }

    @Test
    fun subscriptionsWithoutRunningService() {
        AppGraph.settings.addSubscription(Subscription("x", 0))
        val a = launch().get()
        val container = a.findViewById<LinearLayout>(R.id.subscriptions_container)
        assertEquals(1, container.childCount)
        a.findViewById<EditText>(R.id.filter_input).setText("y")
        a.findViewById<Button>(R.id.add_subscription_button).performClick()
        container.getChildAt(0).findViewById<Button>(R.id.remove_button).performClick()
        assertEquals(listOf(Subscription("y", 0)), AppGraph.settings.subscriptions())
    }

    @Test
    fun messagesRenderAndClear() {
        val a = launch().get()
        val list = a.findViewById<LinearLayout>(R.id.messages_container)
        val empty = a.findViewById<TextView>(R.id.no_messages)

        fun text(
            row: Int,
            id: Int,
        ) = list
            .getChildAt(row)
            .findViewById<TextView>(id)
            .text
            .toString()
        assertEquals(View.VISIBLE, empty.visibility)
        assertEquals(0, list.childCount)
        AppGraph.messageLog.add(ReceivedMessage("t/1", "payload-1", 1, false, 0))
        AppGraph.messageLog.add(ReceivedMessage("t/2", "payload-2", 0, false, 0))
        assertEquals(View.GONE, empty.visibility)
        assertEquals(2, list.childCount)
        assertEquals("t/2", text(0, R.id.message_topic))
        assertEquals("t/1", text(1, R.id.message_topic))
        assertEquals("payload-1", text(1, R.id.message_payload))
        assertTrue(text(1, R.id.message_meta).endsWith("QoS 1"))
        a.findViewById<Button>(R.id.clear_messages_button).performClick()
        assertEquals(View.VISIBLE, empty.visibility)
        assertEquals(0, list.childCount)
    }

    @Test
    fun listenersDetachOnStop() {
        val controller = launch()
        val a = controller.get()
        controller.pause().stop()
        AppGraph.messageLog.add(ReceivedMessage("t", "p", 0, false, 0))
        assertEquals(0, a.findViewById<LinearLayout>(R.id.messages_container).childCount)
    }

    @Test
    fun opensBrokerSettings() {
        val a = launch().get()
        a.findViewById<MaterialToolbar>(R.id.toolbar).menu.performIdentifierAction(R.id.action_broker_settings, 0)
        assertEquals(BrokerSettingsActivity::class.java.name, shadowOf(a).nextStartedActivity.component!!.className)
    }

    @Test
    fun qosSelectionSurvivesRecreation() {
        val controller = launch()
        controller.get().findViewById<QosToggleGroup>(R.id.qos_group).qos = 2
        controller.get().findViewById<QosToggleGroup>(R.id.filter_qos_group).qos = 9
        controller.recreate()
        val a = controller.get()
        assertEquals(2, a.findViewById<QosToggleGroup>(R.id.qos_group).qos)
        assertEquals(2, a.findViewById<QosToggleGroup>(R.id.filter_qos_group).qos)
    }
}
