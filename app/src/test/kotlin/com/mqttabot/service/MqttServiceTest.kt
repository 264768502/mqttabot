package com.mqttabot.service

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mqttabot.AppGraph
import com.mqttabot.R
import com.mqttabot.mqtt.BrokerConfig
import com.mqttabot.mqtt.ConnectionState
import com.mqttabot.mqtt.ReceivedMessage
import com.mqttabot.tasker.TaskerApi
import com.mqttabot.testutil.FakeGateway
import com.mqttabot.testutil.ManualTaskRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController

@RunWith(AndroidJUnit4::class)
class MqttServiceTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val gateways = mutableListOf<FakeGateway>()
    private val runner = ManualTaskRunner()

    @Before
    fun setUp() {
        AppGraph.settings.saveBroker(BrokerConfig(host = "broker", clientId = "c"))
        AppGraph.gatewayFactory = { FakeGateway().also { gateways += it } }
        AppGraph.taskRunnerFactory = { runner }
    }

    private fun startService(action: String? = MqttService.ACTION_START): ServiceController<MqttService> {
        val intent = action?.let { MqttService.intent(app, it) }
        return Robolectric.buildService(MqttService::class.java, intent).create().startCommand(0, 1)
    }

    @Test
    fun startRunsInForegroundAndConnects() {
        val controller = startService()
        val service = controller.get()
        val shadow = shadowOf(service)
        assertTrue(shadow.isForegroundStopped.not())
        val notification = shadow.lastForegroundNotification
        assertNotNull(notification)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(ServiceNotifications.NOTIFICATION_ID, shadow.lastForegroundNotificationId)
        assertTrue(MqttService.isRunning)
        assertEquals(ConnectionState.CONNECTED, AppGraph.status.value.state)
        assertTrue(gateways.single().isConnected)

        // Status changes refresh the ongoing notification.
        val nm = app.getSystemService(NotificationManager::class.java)
        val text =
            shadowOf(nm)
                .getNotification(ServiceNotifications.NOTIFICATION_ID)
                .extras
                .getString(Notification.EXTRA_TEXT)
        assertEquals(app.getString(R.string.status_connected, "tcp://broker:1883"), text)
    }

    @Test
    fun startsWithForegroundServiceType() {
        var type = -1
        val default = AppGraph.foregroundStarter
        AppGraph.foregroundStarter = { s, id, n ->
            default(s, id, n)
            type = s.foregroundServiceType
        }
        startService()
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE, type)
    }

    @Test
    fun repeatedStartKeepsSingleConnection() {
        val controller = startService()
        controller.startCommand(0, 2)
        assertEquals(1, gateways.size)
    }

    @Test
    fun nullIntentRestartBehavesLikeStart() {
        // START_STICKY restarts deliver a null intent.
        val service = Robolectric.buildService(MqttService::class.java).create().get()
        assertEquals(android.app.Service.START_STICKY, service.onStartCommand(null, 0, 1))
        assertTrue(MqttService.isRunning)
    }

    @Test
    fun stopActionStopsSelf() {
        val controller = startService(action = MqttService.ACTION_STOP)
        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
        assertFalse(MqttService.isRunning)
    }

    @Test
    fun foregroundFailureStopsService() {
        AppGraph.foregroundStarter = { _, _, _ -> throw IllegalStateException("not allowed") }
        val controller = startService()
        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
        assertFalse(MqttService.isRunning)
        assertTrue(gateways.isEmpty())
    }

    @Test
    fun messagesGoToLogAndTasker() {
        startService()
        val msg = ReceivedMessage("a", "b", 0, false, 1)
        gateways.single().listener!!.onMessage(msg)
        assertEquals(listOf(msg), AppGraph.messageLog.updates.value)
        assertTrue(shadowOf(app).broadcastIntents.any { it.action == TaskerApi.ACTION_REQUEST_QUERY })
    }

    @Test
    fun messageNotificationsOffByDefault() {
        startService()
        gateways.single().listener!!.onMessage(ReceivedMessage("a", "b", 0, false, 1))
        val nm = shadowOf(app.getSystemService(NotificationManager::class.java))
        assertNull(nm.getNotification("a", MessageNotifications.NOTIFICATION_ID))
    }

    @Test
    fun messageNotificationsPerTopicWhenEnabled() {
        AppGraph.settings.notifyOnMessage = true
        startService()
        val listener = gateways.single().listener!!
        listener.onMessage(ReceivedMessage("a/b", "first", 0, false, 1))
        listener.onMessage(ReceivedMessage("a/b", "second", 0, false, 2))
        listener.onMessage(ReceivedMessage("c", "other", 0, false, 3))
        val nm = shadowOf(app.getSystemService(NotificationManager::class.java))
        val latest = nm.getNotification("a/b", MessageNotifications.NOTIFICATION_ID)
        assertEquals("a/b", latest.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("second", latest.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(2L, latest.`when`)
        assertNotNull(latest.contentIntent)
        assertTrue(latest.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertEquals("other", nm.getNotification("c", MessageNotifications.NOTIFICATION_ID).extras.getString(Notification.EXTRA_TEXT))
        // The ongoing service notification plus one per topic.
        assertEquals(3, nm.size())
        val channel = app.getSystemService(NotificationManager::class.java).getNotificationChannel(MessageNotifications.CHANNEL_ID)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
    }

    @Test
    fun longPayloadIsTruncatedInNotification() {
        AppGraph.settings.notifyOnMessage = true
        startService()
        gateways.single().listener!!.onMessage(ReceivedMessage("t", "x".repeat(5000), 0, false, 1))
        val n = shadowOf(app.getSystemService(NotificationManager::class.java)).getNotification("t", MessageNotifications.NOTIFICATION_ID)
        assertEquals(1000, n.extras.getCharSequence(Notification.EXTRA_TEXT)!!.length)
    }

    @Test
    fun destroyDisconnectsAndClearsController() {
        val controller = startService()
        controller.destroy()
        assertFalse(MqttService.isRunning)
        assertEquals(1, gateways.single().disconnects)
        assertEquals(ConnectionState.DISCONNECTED, AppGraph.status.value.state)
        assertTrue(runner.isShutdown)
    }

    @Test
    fun destroyKeepsControllerOwnedBySomeoneElse() {
        val controller = startService()
        val other = AppGraph.newController {}
        AppGraph.controller = other
        controller.destroy()
        assertSame(other, AppGraph.controller)
    }

    @Test
    fun destroyWithoutStart() {
        Robolectric.buildService(MqttService::class.java).create().destroy()
        assertNull(AppGraph.controller)
    }

    @Test
    fun bindIsUnsupported() {
        assertNull(
            Robolectric
                .buildService(MqttService::class.java)
                .create()
                .get()
                .onBind(Intent()),
        )
    }

    @Test
    fun companionStartAndStop() {
        MqttService.start(app)
        val started = shadowOf(app).nextStartedService
        assertEquals(MqttService::class.java.name, started.component!!.className)
        assertEquals(MqttService.ACTION_START, started.action)
        MqttService.stop(app)
        assertEquals(MqttService::class.java.name, shadowOf(app).nextStoppedService.component!!.className)
    }

    @Test
    fun bootReceiverHonoursSetting() {
        val receiver = BootReceiver()
        receiver.onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNull(shadowOf(app).nextStartedService)
        AppGraph.settings.autoStartOnBoot = true
        receiver.onReceive(app, Intent("other"))
        assertNull(shadowOf(app).nextStartedService)
        receiver.onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(MqttService.ACTION_START, shadowOf(app).nextStartedService.action)
    }

    @Test
    fun notificationContent() {
        val n = ServiceNotifications(app as Context)
        val notification = n.build(com.mqttabot.mqtt.ConnectionStatus.DISCONNECTED)
        assertEquals(app.getString(R.string.notification_title), notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(1, notification.actions.size)
        assertNotNull(notification.contentIntent)
        val channel = app.getSystemService(NotificationManager::class.java).getNotificationChannel(ServiceNotifications.CHANNEL_ID)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
    }
}
