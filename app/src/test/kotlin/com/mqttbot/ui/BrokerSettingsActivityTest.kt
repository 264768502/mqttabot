package com.mqttbot.ui

import android.app.Application
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.textfield.TextInputLayout
import com.mqttbot.AppGraph
import com.mqttbot.R
import com.mqttbot.mqtt.BrokerConfig
import com.mqttbot.mqtt.BrokerPresets
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
class BrokerSettingsActivityTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun launch() = Robolectric.buildActivity(BrokerSettingsActivity::class.java).setup().get()

    private fun BrokerSettingsActivity.text(id: Int) = findViewById<EditText>(id).text.toString()

    private fun BrokerSettingsActivity.set(id: Int, value: String) = findViewById<EditText>(id).setText(value)

    private fun BrokerSettingsActivity.save() = findViewById<Button>(R.id.save_button).performClick()

    @Test
    fun loadsCurrentSettingsAndMatchingPreset() {
        AppGraph.settings.saveBroker(BrokerConfig("broker.emqx.io", 8883, true, "u", "p", "cid", 15, false))
        AppGraph.settings.autoStartOnBoot = true
        val a = launch()
        assertEquals("broker.emqx.io", a.text(R.id.host_input))
        assertEquals("8883", a.text(R.id.port_input))
        assertTrue(a.findViewById<CheckBox>(R.id.tls_check).isChecked)
        assertEquals("u", a.text(R.id.username_input))
        assertEquals("p", a.text(R.id.password_input))
        assertEquals("cid", a.text(R.id.client_id_input))
        assertEquals("15", a.text(R.id.keep_alive_input))
        assertFalse(a.findViewById<CheckBox>(R.id.clean_session_check).isChecked)
        assertTrue(a.findViewById<CheckBox>(R.id.auto_start_check).isChecked)
        val expected = BrokerPresets.all.indexOfFirst { it.host == "broker.emqx.io" && it.tls } + 1
        assertEquals(expected, a.findViewById<Spinner>(R.id.preset_spinner).selectedItemPosition)
    }

    @Test
    fun choosingPresetsFillsEndpoint() {
        AppGraph.settings.saveBroker(BrokerConfig(host = "custom.example", username = "me", password = "pw"))
        val a = launch()
        assertEquals(0, a.findViewById<Spinner>(R.id.preset_spinner).selectedItemPosition)
        // Re-selecting the applied position is a no-op.
        a.onItemSelected(null, null, 0, 0)
        assertEquals("custom.example", a.text(R.id.host_input))

        val hive = BrokerPresets.all.indexOfFirst { it.host == "broker.hivemq.com" && it.tls }
        a.onItemSelected(null, null, hive + 1, 0)
        assertEquals("broker.hivemq.com", a.text(R.id.host_input))
        assertEquals("8883", a.text(R.id.port_input))
        assertTrue(a.findViewById<CheckBox>(R.id.tls_check).isChecked)
        // Presets without credentials keep the user's.
        assertEquals("me", a.text(R.id.username_input))

        val auth = BrokerPresets.all.indexOfFirst { it.username.isNotEmpty() }
        a.onItemSelected(null, null, auth + 1, 0)
        assertEquals("rw", a.text(R.id.username_input))
        assertEquals("readwrite", a.text(R.id.password_input))

        // Back to custom keeps the form as-is.
        a.onItemSelected(null, null, 0, 0)
        assertEquals("test.mosquitto.org", a.text(R.id.host_input))
        a.onNothingSelected(null)
    }

    @Test
    fun saveValidAndReconnect() {
        val gateways = mutableListOf<FakeGateway>()
        val controller = ConnectionController(
            { AppGraph.settings.loadBroker() }, { emptyList() }, { FakeGateway().also { gateways += it } },
            ManualTaskRunner(), Observable(ConnectionStatus.DISCONNECTED), {},
        ).also { it.start() }
        AppGraph.controller = controller
        val a = launch()
        a.set(R.id.host_input, " my.host ")
        a.set(R.id.port_input, "1884")
        a.findViewById<CheckBox>(R.id.tls_check).isChecked = false
        a.set(R.id.username_input, "user")
        a.set(R.id.password_input, "secret")
        a.set(R.id.client_id_input, " my-id ")
        a.set(R.id.keep_alive_input, "45")
        a.findViewById<CheckBox>(R.id.clean_session_check).isChecked = false
        a.findViewById<CheckBox>(R.id.auto_start_check).isChecked = true
        a.save()
        assertTrue(a.isFinishing)
        assertEquals(BrokerConfig("my.host", 1884, false, "user", "secret", "my-id", 45, false), AppGraph.settings.loadBroker())
        assertTrue(AppGraph.settings.autoStartOnBoot)
        assertEquals(2, gateways.size)
        assertEquals("my.host", gateways.last().connectedWith!!.host)
    }

    @Test
    fun saveWithoutRunningService() {
        val a = launch()
        a.set(R.id.host_input, "h2")
        a.save()
        assertTrue(a.isFinishing)
        assertEquals("h2", AppGraph.settings.loadBroker().host)
    }

    @Test
    fun validationErrors() {
        val a = launch()
        fun error(id: Int) = a.findViewById<TextInputLayout>(id).error

        a.set(R.id.host_input, "")
        a.save()
        assertNotNull(error(R.id.host_layout))

        a.set(R.id.host_input, "h")
        a.set(R.id.port_input, "")
        a.save()
        assertNull(error(R.id.host_layout))
        assertNotNull(error(R.id.port_layout))

        a.set(R.id.port_input, "1883")
        a.set(R.id.keep_alive_input, "")
        a.save()
        assertNotNull(error(R.id.keep_alive_layout))

        a.set(R.id.keep_alive_input, "60")
        a.set(R.id.username_input, "")
        a.set(R.id.password_input, "p")
        a.save()
        assertNotNull(error(R.id.username_layout))
        assertFalse(a.isFinishing)
        assertEquals(app.getString(R.string.error_username), error(R.id.username_layout).toString())
    }
}
