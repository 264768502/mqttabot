package com.mqttabot.ui

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mqttabot.AppGraph
import com.mqttabot.R
import com.mqttabot.mqtt.BrokerConfig
import com.mqttabot.mqtt.BrokerPresets
import com.mqttabot.mqtt.ConnectionController
import com.mqttabot.mqtt.ConnectionStatus
import com.mqttabot.mqtt.Observable
import com.mqttabot.testutil.FakeGateway
import com.mqttabot.testutil.ManualTaskRunner
import com.mqttabot.testutil.text
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrokerSettingsActivityTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private var scenario: ActivityScenario<BrokerSettingsActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch() {
        scenario = ActivityScenario.launchActivityForResult(BrokerSettingsActivity::class.java)
    }

    // Blocks until the activity finishes.
    private fun assertFinished() {
        scenario!!.result
    }

    private fun assertNotFinishing() = scenario!!.onActivity { assertFalse(it.isFinishing) }

    private fun node(tag: String): SemanticsNodeInteraction = compose.onNodeWithTag(tag).performScrollTo()

    private fun set(
        tag: String,
        value: String,
    ) = node(tag).performTextReplacement(value)

    private fun save() = node(TAG_SAVE).performClick()

    private fun choosePreset(index: Int) {
        node(TAG_PRESET).performClick()
        // The popup may lie outside Robolectric's small window, so click semantically.
        compose.onNodeWithTag("$TAG_PRESET_OPTION-$index").performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun assertError(message: Int) = compose.onNodeWithText(app.getString(message)).assertExists()

    @Test
    fun loadsCurrentSettingsAndMatchingPreset() {
        AppGraph.settings.saveBroker(BrokerConfig("broker.emqx.io", 8883, true, "u", "p", "cid", 15, false))
        AppGraph.settings.autoStartOnBoot = true
        AppGraph.settings.notifyOnMessage = true
        launch()
        assertEquals("broker.emqx.io", node(TAG_HOST).text())
        assertEquals("8883", node(TAG_PORT).text())
        node(TAG_TLS).assertIsOn()
        assertEquals("u", node(TAG_USERNAME).text())
        assertEquals("cid", node(TAG_CLIENT_ID).text())
        assertEquals("15", node(TAG_KEEP_ALIVE).text())
        node(TAG_CLEAN_SESSION).assertIsOff()
        node(TAG_AUTO_START).assertIsOn()
        node(TAG_NOTIFY_MESSAGES).assertIsOn()
        val expected = BrokerPresets.all.first { it.host == "broker.emqx.io" && it.tls }
        assertEquals(expected.name, node(TAG_PRESET).text())
    }

    @Test
    fun passwordIsMaskedUntilRevealed() {
        AppGraph.settings.saveBroker(BrokerConfig(host = "h", username = "u", password = "p"))
        launch()
        assertEquals("•", node(TAG_PASSWORD).text())
        node(TAG_PASSWORD_TOGGLE).assert(hasContentDescription(app.getString(R.string.action_show_password))).performClick()
        assertEquals("p", node(TAG_PASSWORD).text())
        node(TAG_PASSWORD_TOGGLE).assert(hasContentDescription(app.getString(R.string.action_hide_password))).performClick()
        assertEquals("•", node(TAG_PASSWORD).text())
    }

    @Test
    fun choosingPresetsFillsEndpoint() {
        AppGraph.settings.saveBroker(BrokerConfig(host = "custom.example", username = "me", password = "pw"))
        launch()
        assertEquals(app.getString(R.string.preset_custom), node(TAG_PRESET).text())

        val hive = BrokerPresets.all.indexOfFirst { it.host == "broker.hivemq.com" && it.tls }
        choosePreset(hive + 1)
        assertEquals(BrokerPresets.all[hive].name, node(TAG_PRESET).text())
        assertEquals("broker.hivemq.com", node(TAG_HOST).text())
        assertEquals("8883", node(TAG_PORT).text())
        node(TAG_TLS).assertIsOn()
        // Presets without credentials keep the user's.
        assertEquals("me", node(TAG_USERNAME).text())

        val auth = BrokerPresets.all.indexOfFirst { it.username.isNotEmpty() }
        choosePreset(auth + 1)
        assertEquals("rw", node(TAG_USERNAME).text())

        // Back to custom keeps the form as-is.
        choosePreset(0)
        assertEquals(app.getString(R.string.preset_custom), node(TAG_PRESET).text())
        assertEquals("test.mosquitto.org", node(TAG_HOST).text())
        node(TAG_PASSWORD_TOGGLE).performClick()
        assertEquals("readwrite", node(TAG_PASSWORD).text())
    }

    @Test
    fun saveValidAndReconnect() {
        val gateways = mutableListOf<FakeGateway>()
        AppGraph.controller =
            ConnectionController(
                { AppGraph.settings.loadBroker() },
                { emptyList() },
                { FakeGateway().also { gateways += it } },
                ManualTaskRunner(),
                Observable(ConnectionStatus.DISCONNECTED),
                {},
            ).also { it.start() }
        launch()
        set(TAG_HOST, " my.host ")
        set(TAG_PORT, "1884")
        node(TAG_TLS).performClick().assertIsOn().performClick()
        set(TAG_USERNAME, "user")
        set(TAG_PASSWORD, "secret")
        set(TAG_CLIENT_ID, " my-id ")
        set(TAG_KEEP_ALIVE, "45")
        node(TAG_CLEAN_SESSION).performClick()
        node(TAG_AUTO_START).performClick()
        node(TAG_NOTIFY_MESSAGES).assertIsOff().performClick()
        save()
        assertFinished()
        assertEquals(BrokerConfig("my.host", 1884, false, "user", "secret", "my-id", 45, false), AppGraph.settings.loadBroker())
        assertTrue(AppGraph.settings.autoStartOnBoot)
        assertTrue(AppGraph.settings.notifyOnMessage)
        assertEquals(2, gateways.size)
        assertEquals("my.host", gateways.last().connectedWith!!.host)
    }

    @Test
    fun saveWithoutRunningService() {
        launch()
        set(TAG_HOST, "h2")
        save()
        assertFinished()
        assertEquals("h2", AppGraph.settings.loadBroker().host)
    }

    @Test
    fun validationErrors() {
        launch()
        set(TAG_HOST, "")
        save()
        assertError(R.string.error_host)

        set(TAG_HOST, "h")
        set(TAG_PORT, "")
        save()
        compose.onNodeWithText(app.getString(R.string.error_host)).assertDoesNotExist()
        assertError(R.string.error_port)

        set(TAG_PORT, "1883")
        set(TAG_KEEP_ALIVE, "")
        save()
        assertError(R.string.error_keep_alive)

        set(TAG_KEEP_ALIVE, "60")
        set(TAG_USERNAME, "")
        set(TAG_PASSWORD, "p")
        save()
        assertError(R.string.error_username)
        assertNotFinishing()
    }

    @Test
    fun navigateUpDiscardsChanges() {
        AppGraph.settings.saveBroker(BrokerConfig(host = "kept"))
        launch()
        set(TAG_HOST, "discarded")
        compose.onNodeWithTag(TAG_BACK).performClick()
        assertFinished()
        assertEquals("kept", AppGraph.settings.loadBroker().host)
    }
}
