package com.mqttbot.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputLayout
import com.mqttbot.AppGraph
import com.mqttbot.R
import com.mqttbot.mqtt.BrokerConfig
import com.mqttbot.mqtt.BrokerPresets
import com.mqttbot.mqtt.ConfigError

/** Broker endpoint, credentials and connection options. */
class BrokerSettingsActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener {
    private lateinit var preset: Spinner
    private lateinit var host: EditText
    private lateinit var port: EditText
    private lateinit var tls: CheckBox
    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var clientId: EditText
    private lateinit var keepAlive: EditText
    private lateinit var cleanSession: CheckBox
    private lateinit var autoStart: CheckBox
    private lateinit var hostLayout: TextInputLayout
    private lateinit var portLayout: TextInputLayout
    private lateinit var usernameLayout: TextInputLayout
    private lateinit var keepAliveLayout: TextInputLayout

    // Spinner position whose values are currently in the form; 0 = custom.
    private var appliedPreset = 0

    // Ports and timeouts must stay ASCII digits so they parse back.
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_broker_settings)
        preset = findViewById(R.id.preset_spinner)
        host = findViewById(R.id.host_input)
        port = findViewById(R.id.port_input)
        tls = findViewById(R.id.tls_check)
        username = findViewById(R.id.username_input)
        password = findViewById(R.id.password_input)
        clientId = findViewById(R.id.client_id_input)
        keepAlive = findViewById(R.id.keep_alive_input)
        cleanSession = findViewById(R.id.clean_session_check)
        autoStart = findViewById(R.id.auto_start_check)
        hostLayout = findViewById(R.id.host_layout)
        portLayout = findViewById(R.id.port_layout)
        usernameLayout = findViewById(R.id.username_layout)
        keepAliveLayout = findViewById(R.id.keep_alive_layout)

        val names = listOf(getString(R.string.preset_custom)) + BrokerPresets.all.map { it.name }
        preset.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val config = AppGraph.settings.loadBroker()
        host.setText(config.host)
        port.setText(config.port.toString())
        tls.isChecked = config.tls
        username.setText(config.username)
        password.setText(config.password)
        clientId.setText(config.clientId)
        keepAlive.setText(config.keepAliveSeconds.toString())
        cleanSession.isChecked = config.cleanSession
        autoStart.isChecked = AppGraph.settings.autoStartOnBoot
        appliedPreset = BrokerPresets.indexOf(config) + 1
        preset.setSelection(appliedPreset)
        preset.onItemSelectedListener = this
        findViewById<Button>(R.id.save_button).setOnClickListener { save() }
    }

    @SuppressLint("SetTextI18n")
    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        if (position == appliedPreset) return
        appliedPreset = position
        if (position == 0) return
        val p = BrokerPresets.all[position - 1]
        host.setText(p.host)
        port.setText(p.port.toString())
        tls.isChecked = p.tls
        if (p.username.isNotEmpty()) {
            username.setText(p.username)
            password.setText(p.password)
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) = Unit

    private fun save() {
        val config = BrokerConfig(
            host = host.text.toString().trim(),
            port = port.text.toString().toIntOrNull() ?: 0,
            tls = tls.isChecked,
            username = username.text.toString(),
            password = password.text.toString(),
            clientId = clientId.text.toString().trim(),
            keepAliveSeconds = keepAlive.text.toString().toIntOrNull() ?: -1,
            cleanSession = cleanSession.isChecked,
        )
        for (layout in listOf(hostLayout, portLayout, usernameLayout, keepAliveLayout)) layout.error = null
        when (config.validate()) {
            ConfigError.HOST -> hostLayout.error = getString(R.string.error_host)
            ConfigError.PORT -> portLayout.error = getString(R.string.error_port)
            ConfigError.KEEP_ALIVE -> keepAliveLayout.error = getString(R.string.error_keep_alive)
            ConfigError.USERNAME -> usernameLayout.error = getString(R.string.error_username)
            null -> {
                AppGraph.settings.saveBroker(config)
                AppGraph.settings.autoStartOnBoot = autoStart.isChecked
                AppGraph.controller?.reconnect()
                finish()
            }
        }
    }
}
