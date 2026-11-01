package com.mqttabot.tasker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputLayout
import com.mqttabot.AppGraph
import com.mqttabot.R
import com.mqttabot.data.Subscription
import com.mqttabot.mqtt.Topics
import com.mqttabot.ui.QosAdapter

/** Tasker edit screen for the "MQTT message received" event. */
class EventConfigActivity : AppCompatActivity() {
    private lateinit var filterLayout: TextInputLayout
    private lateinit var filter: EditText
    private lateinit var patternLayout: TextInputLayout
    private lateinit var pattern: EditText
    private lateinit var qos: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_config)
        filterLayout = findViewById(R.id.filter_layout)
        filter = findViewById(R.id.filter_input)
        patternLayout = findViewById(R.id.pattern_layout)
        pattern = findViewById(R.id.pattern_input)
        qos = findViewById(R.id.qos_spinner)
        qos.adapter = QosAdapter.create(this)
        qos.setSelection(1)

        EventFilterConfig.fromBundle(intent.getBundleExtra(TaskerApi.EXTRA_BUNDLE))?.let {
            filter.setText(it.topicFilter)
            pattern.setText(it.payloadPattern)
            qos.setSelection(it.qos.coerceIn(0, 2))
        }
        findViewById<Button>(R.id.save_button).setOnClickListener { save() }
        findViewById<Button>(R.id.cancel_button).setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun save() {
        val filterText = filter.text.toString().trim()
        val patternText = pattern.text.toString()
        filterLayout.error = null
        patternLayout.error = null
        if (!Topics.isValidFilter(filterText)) {
            filterLayout.error = getString(R.string.error_filter)
            return
        }
        if (!EventFilterConfig.isValidPattern(patternText)) {
            patternLayout.error = getString(R.string.error_pattern)
            return
        }
        val config = EventFilterConfig(filterText, patternText, qos.selectedItemPosition)
        // The event only fires for messages we receive, so make sure we subscribe.
        val subscription = Subscription(config.topicFilter, config.qos)
        AppGraph.settings.addSubscription(subscription)
        AppGraph.controller?.subscribe(subscription)

        val result =
            Intent()
                .putExtra(TaskerApi.EXTRA_BUNDLE, config.toBundle())
                .putExtra(TaskerApi.EXTRA_STRING_BLURB, getString(R.string.tasker_event_blurb, config.topicFilter))
                .putExtra(TaskerApi.EXTRA_RELEVANT_VARIABLES, resources.getStringArray(R.array.tasker_event_variables))
        setResult(RESULT_OK, result)
        finish()
    }
}
