package com.mqttabot.ui

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mqttabot.AppGraph
import com.mqttabot.R
import com.mqttabot.data.Subscription
import com.mqttabot.data.ThemeMode
import com.mqttabot.mqtt.BrokerConfig
import com.mqttabot.mqtt.ConnectionController
import com.mqttabot.mqtt.ConnectionState
import com.mqttabot.mqtt.ConnectionStatus
import com.mqttabot.mqtt.Observable
import com.mqttabot.mqtt.ReceivedMessage
import com.mqttabot.service.MqttService
import com.mqttabot.testutil.FakeGateway
import com.mqttabot.testutil.ManualTaskRunner
import com.mqttabot.testutil.onMain
import com.mqttabot.testutil.text
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.Executor

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val gateways = mutableListOf<FakeGateway>()
    private var nextGateway: () -> FakeGateway = { FakeGateway() }
    private val pending = mutableListOf<Runnable>()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        AppGraph.settings.saveBroker(BrokerConfig(host = "broker", clientId = "c"))
        AppGraph.gatewayFactory = { nextGateway().also { gateways += it } }
        AppGraph.ioExecutor = Executor { pending += it }
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch(): ActivityScenario<MainActivity> = ActivityScenario.launch(MainActivity::class.java).also { scenario = it }

    private fun activity(): MainActivity {
        var a: MainActivity? = null
        scenario!!.onActivity { a = it }
        return a!!
    }

    private fun runIo() {
        pending.toList().also { pending.clear() }.forEach { it.run() }
        compose.waitForIdle()
    }

    private fun string(id: Int) = app.getString(id)

    private fun connectedController(gateway: FakeGateway): ConnectionController =
        ConnectionController(
            { BrokerConfig(host = "broker") },
            { emptyList() },
            { gateway },
            ManualTaskRunner(),
            AppGraph.status,
            {},
        ).also { it.start() }

    @Test
    fun showsBrokerAndEveryStatus() {
        launch()
        compose.onMain(TAG_BROKER).assertTextEquals("tcp://broker:1883")
        compose.onMain(TAG_STATUS).assertTextEquals(string(R.string.status_disconnected))
        compose.onMain(TAG_SERVICE).assertTextEquals(string(R.string.action_start_service))
        compose.onNodeWithTag(TAG_STATUS_PROGRESS).assertDoesNotExist()
        compose.onNodeWithTag(TAG_STATUS_DETAIL).assertDoesNotExist()

        AppGraph.status.set(ConnectionStatus(ConnectionState.CONNECTING, "x"))
        compose.onMain(TAG_STATUS).assertTextEquals(string(R.string.state_connecting))
        compose.onMain(TAG_STATUS_PROGRESS).assertIsDisplayed()

        AppGraph.status.set(ConnectionStatus(ConnectionState.WAITING_RETRY, "boom"))
        compose.onMain(TAG_STATUS).assertTextEquals(string(R.string.state_reconnecting))
        compose.onMain(TAG_STATUS_DETAIL).assertTextEquals("boom")
        compose.onNodeWithTag(TAG_STATUS_PROGRESS).assertDoesNotExist()

        AppGraph.status.set(ConnectionStatus(ConnectionState.CONNECTED, "u"))
        compose.onMain(TAG_STATUS).assertTextEquals(string(R.string.state_connected))
    }

    @Test
    fun startServiceWithNotificationsEnabled() {
        launch()
        compose.onMain(TAG_SERVICE).performClick()
        assertNull(shadowOf(activity()).lastRequestedPermission)
        assertEquals(MqttService.ACTION_START, shadowOf(app).nextStartedService.action)
    }

    @Test
    fun stopServiceWhenRunning() {
        AppGraph.controller = connectedController(FakeGateway())
        launch()
        compose.onMain(TAG_SERVICE).assertTextEquals(string(R.string.action_stop_service)).performClick()
        assertEquals(MqttService::class.java.name, shadowOf(app).nextStoppedService.component!!.className)
    }

    @Suppress("DEPRECATION")
    private fun answerNotificationPermission(result: Int) {
        val request = shadowOf(activity()).lastRequestedPermission
        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), request.requestedPermissions.toList())
        activity().onRequestPermissionsResult(request.requestCode, request.requestedPermissions, intArrayOf(result))
        compose.waitForIdle()
    }

    @Test
    fun deniedNotificationPermissionIsExplained() {
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        launch()
        compose.onMain(TAG_SERVICE).performClick()
        // Asked only now, when the service is about to start.
        answerNotificationPermission(PackageManager.PERMISSION_DENIED)
        assertEquals(MqttService.ACTION_START, shadowOf(app).nextStartedService.action)
        compose.onNodeWithText(string(R.string.rationale_notifications_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.action_open_settings)).performClick()
        val settings = shadowOf(activity()).nextStartedActivity
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, settings.action)
        assertEquals(app.packageName, settings.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        compose.onNodeWithText(string(R.string.rationale_notifications_title)).assertDoesNotExist()
    }

    @Test
    fun grantedNotificationPermissionStartsQuietly() {
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        launch()
        compose.onMain(TAG_SERVICE).performClick()
        answerNotificationPermission(PackageManager.PERMISSION_GRANTED)
        assertEquals(MqttService.ACTION_START, shadowOf(app).nextStartedService.action)
        compose.onNodeWithText(string(R.string.rationale_notifications_title)).assertDoesNotExist()
    }

    @Test
    fun notificationsBlockedInSettingsAreExplained() {
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        launch()
        compose.onMain(TAG_SERVICE).performClick()
        assertNull(shadowOf(activity()).lastRequestedPermission)
        assertEquals(MqttService.ACTION_START, shadowOf(app).nextStartedService.action)
        compose.onNodeWithText(string(R.string.action_not_now)).performClick()
        compose.onNodeWithText(string(R.string.rationale_notifications_title)).assertDoesNotExist()
        assertNull(shadowOf(activity()).nextStartedActivity)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.N])
    fun notificationsOffBeforeAndroid13OpenAppSettings() {
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        launch()
        compose.onMain(TAG_SERVICE).performClick()
        assertNull(shadowOf(activity()).lastRequestedPermission)
        compose.onNodeWithText(string(R.string.action_open_settings)).performClick()
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, shadowOf(activity()).nextStartedActivity.action)
    }

    @Test
    fun optimizedBackgroundAsksForExemptionAndExplainsRefusal() {
        launch()
        compose.onMain(TAG_BACKGROUND).assertTextEquals(string(R.string.background_optimized))
        compose.onMain(TAG_ALLOW_BACKGROUND).performClick()
        val request = shadowOf(activity()).nextStartedActivityForResult
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, request.intent.action)
        assertEquals("package:${app.packageName}", request.intent.dataString)

        // The user declined in the system dialog.
        shadowOf(activity()).receiveResult(request.intent, Activity.RESULT_CANCELED, null)
        compose.onNodeWithText(string(R.string.rationale_background_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.action_open_settings)).performClick()
        val retry = shadowOf(activity()).nextStartedActivityForResult
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, retry.intent.action)

        // This time the user allowed it.
        shadowOf(app.getSystemService(PowerManager::class.java)).setIgnoringBatteryOptimizations(app.packageName, true)
        shadowOf(activity()).receiveResult(retry.intent, Activity.RESULT_OK, null)
        compose.onNodeWithText(string(R.string.rationale_background_title)).assertDoesNotExist()
        compose.onMain(TAG_BACKGROUND).assertTextEquals(string(R.string.background_unrestricted))
        compose.onNodeWithTag(TAG_ALLOW_BACKGROUND).assertDoesNotExist()
    }

    @Test
    fun restrictedBackgroundOpensAppSettings() {
        shadowOf(app.getSystemService(ActivityManager::class.java)).setBackgroundRestricted(true)
        launch()
        compose.onMain(TAG_BACKGROUND).assertTextEquals(string(R.string.background_restricted))
        compose.onMain(TAG_ALLOW_BACKGROUND).performClick()
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, shadowOf(activity()).nextStartedActivityForResult.intent.action)
    }

    @Test
    fun missingExemptionDialogFallsBackToAppSettings() {
        val settings = ComponentName("com.android.settings", "AppInfo")
        shadowOf(app.packageManager).addActivityIfNotPresent(settings)
        shadowOf(app.packageManager).addIntentFilterForActivity(
            settings,
            IntentFilter(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme("package")
            },
        )
        shadowOf(app).checkActivities(true)
        launch()
        compose.onMain(TAG_ALLOW_BACKGROUND).performClick()
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, shadowOf(activity()).nextStartedActivityForResult.intent.action)
    }

    @Test
    fun backgroundModeRefreshesOnResume() {
        launch()
        compose.onMain(TAG_BACKGROUND).assertTextEquals(string(R.string.background_optimized))
        scenario!!.moveToState(Lifecycle.State.STARTED)
        shadowOf(app.getSystemService(PowerManager::class.java)).setIgnoringBatteryOptimizations(app.packageName, true)
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        compose.onMain(TAG_BACKGROUND).assertTextEquals(string(R.string.background_unrestricted))
    }

    @Test
    fun publishSuccessAndFailure() {
        launch()
        compose.onMain(TAG_TOPIC).performTextReplacement(" a/b ")
        compose.onMain(TAG_PAYLOAD).performTextReplacement("hello")
        compose.onMain("$TAG_QOS-1").performClick()
        compose.onMain(TAG_RETAIN).performClick().assertIsOn()
        compose.onNodeWithTag(TAG_PUBLISH_RESULT).assertDoesNotExist()
        compose.onMain(TAG_SEND).performClick().assertIsNotEnabled()
        compose.onMain(TAG_PUBLISH_RESULT).assertTextEquals(string(R.string.publish_sending))
        runIo()
        compose.onMain(TAG_SEND).assertIsEnabled()
        compose.onMain(TAG_PUBLISH_RESULT).assertTextEquals(app.getString(R.string.publish_ok, "a/b"))
        assertEquals(listOf(FakeGateway.Published("a/b", "hello", 1, true)), gateways.single().published)

        nextGateway = { FakeGateway(connectError = IOException("refused")) }
        compose.onMain(TAG_SEND).performClick()
        runIo()
        assertTrue(compose.onMain(TAG_PUBLISH_RESULT).text().contains("refused"))
    }

    @Test
    fun publishValidatesTopic() {
        launch()
        compose.onMain(TAG_TOPIC).performTextReplacement("a/+")
        compose.onMain(TAG_SEND).performClick()
        compose.onNodeWithTag(TAG_PUBLISH_RESULT).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.error_topic)).assertExists()
        assertTrue(pending.isEmpty())
        compose.onMain(TAG_TOPIC).performTextReplacement("a")
        compose.onMain(TAG_SEND).performClick()
        compose.onNodeWithText(string(R.string.error_topic)).assertDoesNotExist()
        assertEquals(1, pending.size)
    }

    @Test
    fun addAndRemoveSubscriptions() {
        val gateway = FakeGateway()
        AppGraph.controller = connectedController(gateway)
        launch()
        compose.onMain(TAG_NO_SUBSCRIPTIONS).assertIsDisplayed()

        compose.onMain(TAG_FILTER).performTextReplacement("bad#")
        compose.onMain(TAG_ADD_SUBSCRIPTION).performClick()
        compose.onNodeWithText(string(R.string.error_filter)).assertExists()

        compose.onMain(TAG_FILTER).performTextReplacement("home/#")
        compose.onMain("$TAG_FILTER_QOS-2").performClick().assertIsOn()
        compose.onMain(TAG_ADD_SUBSCRIPTION).performClick()
        compose.onNodeWithText(string(R.string.error_filter)).assertDoesNotExist()
        assertEquals("", compose.onMain(TAG_FILTER).text())
        assertEquals(listOf(Subscription("home/#", 2)), AppGraph.settings.subscriptions())
        assertEquals("home/#" to 2, gateway.subscriptions.last())
        compose.onNodeWithTag(TAG_NO_SUBSCRIPTIONS).assertDoesNotExist()
        compose.onMain(TAG_SUBSCRIPTION).assertIsDisplayed()
        compose.onNodeWithText("home/#").assertExists()
        compose.onNodeWithText(app.getString(R.string.qos_value, 2)).assertExists()

        compose.onMain(TAG_REMOVE_SUBSCRIPTION).performClick()
        assertTrue(AppGraph.settings.subscriptions().isEmpty())
        assertEquals(listOf("home/#"), gateway.unsubscriptions)
        compose.onNodeWithTag(TAG_SUBSCRIPTION).assertDoesNotExist()
    }

    @Test
    fun subscriptionsWithoutRunningService() {
        AppGraph.settings.addSubscription(Subscription("x", 0))
        launch()
        compose.onMain(TAG_SUBSCRIPTION).assertIsDisplayed()
        compose.onMain(TAG_FILTER).performTextReplacement("y")
        compose.onMain(TAG_ADD_SUBSCRIPTION).performClick()
        compose.onAllNodesWithTag(TAG_REMOVE_SUBSCRIPTION)[0].performClick()
        assertEquals(listOf(Subscription("y", 0)), AppGraph.settings.subscriptions())
    }

    // Tall window so every lazy row is composed.
    @Test
    @Config(qualifiers = "h2000dp")
    fun messagesRenderAndClear() {
        launch()
        compose.onMain(TAG_NO_MESSAGES).assertIsDisplayed()
        AppGraph.messageLog.add(ReceivedMessage("t/1", "payload-1", 1, false, 0))
        AppGraph.messageLog.add(ReceivedMessage("t/2", "payload-2", 0, false, 0))
        compose.onNodeWithTag(TAG_NO_MESSAGES).assertDoesNotExist()
        val rows = compose.onAllNodesWithTag(TAG_MESSAGE, useUnmergedTree = true)
        assertEquals(2, rows.fetchSemanticsNodes().size)
        compose.onNodeWithText("payload-1").assertExists()
        compose.onNodeWithText("t/2").assertExists()
        compose.onMain(TAG_CLEAR_MESSAGES).performClick()
        compose.onMain(TAG_NO_MESSAGES).assertIsDisplayed()
        compose.onNodeWithTag(TAG_MESSAGE).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "h2000dp")
    fun listenersDetachOnStop() {
        launch()
        scenario!!.moveToState(Lifecycle.State.CREATED)
        AppGraph.messageLog.add(ReceivedMessage("t", "p", 0, false, 0))
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        // Re-attaching on start delivers the current log.
        compose.onNodeWithText("p").assertExists()
    }

    @Test
    fun opensBrokerSettings() {
        launch()
        compose.onNodeWithTag(TAG_SETTINGS).performClick()
        assertEquals(BrokerSettingsActivity::class.java.name, shadowOf(activity()).nextStartedActivity.component!!.className)
    }

    @Test
    fun choosesTheme() {
        launch()
        compose.onNodeWithTag(TAG_THEME).performClick()
        compose.onNodeWithTag("$TAG_THEME_OPTION-${ThemeMode.AUTO.name}").assertIsSelected()
        compose.onNodeWithTag("$TAG_THEME_OPTION-${ThemeMode.DARK.name}").performClick()
        assertEquals(ThemeMode.DARK, AppGraph.themeMode.value)
        assertEquals(ThemeMode.DARK, AppGraph.settings.themeMode)
        compose.onNodeWithText(string(R.string.theme_title)).assertDoesNotExist()

        compose.onNodeWithTag(TAG_THEME).performClick()
        compose.onNodeWithText(string(R.string.action_cancel)).performClick()
        compose.onNodeWithText(string(R.string.theme_title)).assertDoesNotExist()
        assertEquals(ThemeMode.DARK, AppGraph.themeMode.value)
    }

    @Test
    fun formStateSurvivesRecreation() {
        launch()
        compose.onMain(TAG_TOPIC).performTextReplacement("kept/topic")
        compose.onMain("$TAG_QOS-2").performClick()
        compose.onMain("$TAG_FILTER_QOS-1").performClick()
        scenario!!.recreate()
        assertEquals("kept/topic", compose.onMain(TAG_TOPIC).text())
        compose.onMain("$TAG_QOS-2").assertIsOn()
        compose.onMain("$TAG_FILTER_QOS-1").assertIsOn()
    }
}
