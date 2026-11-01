package com.mqttabot.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.material.textfield.TextInputLayout
import com.mqttabot.AppGraph
import com.mqttabot.R
import com.mqttabot.data.Subscription
import com.mqttabot.mqtt.ConnectionStatus
import com.mqttabot.mqtt.ReceivedMessage
import com.mqttabot.mqtt.Topics
import com.mqttabot.service.MqttService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Status, service control, manual publish, subscriptions and received messages. */
class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var brokerText: TextView
    private lateinit var serviceButton: Button
    private lateinit var topicLayout: TextInputLayout
    private lateinit var topic: EditText
    private lateinit var payload: EditText
    private lateinit var qos: Spinner
    private lateinit var retained: CheckBox
    private lateinit var sendButton: Button
    private lateinit var publishResult: TextView
    private lateinit var filterLayout: TextInputLayout
    private lateinit var filter: EditText
    private lateinit var filterQos: Spinner
    private lateinit var subscriptions: LinearLayout
    private lateinit var noSubscriptions: TextView
    private lateinit var messages: TextView

    private val statusListener: (ConnectionStatus) -> Unit = { runOnUiThread { renderStatus(it) } }
    private val logListener: (List<ReceivedMessage>) -> Unit = { runOnUiThread { renderMessages(it) } }
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.status_text)
        brokerText = findViewById(R.id.broker_text)
        serviceButton = findViewById(R.id.service_button)
        topicLayout = findViewById(R.id.topic_layout)
        topic = findViewById(R.id.topic_input)
        payload = findViewById(R.id.payload_input)
        qos = findViewById(R.id.qos_spinner)
        retained = findViewById(R.id.retain_check)
        sendButton = findViewById(R.id.send_button)
        publishResult = findViewById(R.id.publish_result)
        filterLayout = findViewById(R.id.filter_layout)
        filter = findViewById(R.id.filter_input)
        filterQos = findViewById(R.id.filter_qos_spinner)
        subscriptions = findViewById(R.id.subscriptions_container)
        noSubscriptions = findViewById(R.id.no_subscriptions)
        messages = findViewById(R.id.messages_text)
        qos.adapter = QosAdapter.create(this)
        filterQos.adapter = QosAdapter.create(this)

        findViewById<Button>(R.id.settings_button).setOnClickListener {
            startActivity(Intent(this, BrokerSettingsActivity::class.java))
        }
        serviceButton.setOnClickListener { toggleService() }
        sendButton.setOnClickListener { publish() }
        findViewById<Button>(R.id.add_subscription_button).setOnClickListener { addSubscription() }
        findViewById<Button>(R.id.clear_messages_button).setOnClickListener { AppGraph.messageLog.clear() }
    }

    override fun onStart() {
        super.onStart()
        brokerText.text = AppGraph.settings.loadBroker().serverUri
        renderSubscriptions()
        AppGraph.status.observe(statusListener)
        AppGraph.messageLog.updates.observe(logListener)
    }

    override fun onStop() {
        AppGraph.status.remove(statusListener)
        AppGraph.messageLog.updates.remove(logListener)
        super.onStop()
    }

    // Requesting POST_NOTIFICATIONS below API 33 is a harmless no-op.
    @SuppressLint("InlinedApi")
    private fun toggleService() {
        if (MqttService.isRunning) {
            MqttService.stop(this)
            return
        }
        // Without the permission (Android 13+) the service still runs, just with a hidden notification.
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
        MqttService.start(this)
    }

    private fun publish() {
        val topicText = topic.text.toString().trim()
        if (!Topics.isValidTopicName(topicText)) {
            topicLayout.error = getString(R.string.error_topic)
            return
        }
        topicLayout.error = null
        val payloadText = payload.text.toString()
        val qosLevel = qos.selectedItemPosition
        val retain = retained.isChecked
        sendButton.isEnabled = false
        publishResult.text = getString(R.string.publish_sending)
        AppGraph.ioExecutor.execute {
            val result =
                try {
                    AppGraph.publisher().publish(topicText, payloadText, qosLevel, retain)
                    getString(R.string.publish_ok, topicText)
                } catch (e: Exception) {
                    getString(R.string.publish_failed, e.toString())
                }
            runOnUiThread {
                sendButton.isEnabled = true
                publishResult.text = result
            }
        }
    }

    private fun addSubscription() {
        val filterText = filter.text.toString().trim()
        if (!Topics.isValidFilter(filterText)) {
            filterLayout.error = getString(R.string.error_filter)
            return
        }
        filterLayout.error = null
        val subscription = Subscription(filterText, filterQos.selectedItemPosition)
        AppGraph.settings.addSubscription(subscription)
        AppGraph.controller?.subscribe(subscription)
        filter.setText("")
        renderSubscriptions()
    }

    private fun renderStatus(status: ConnectionStatus) {
        statusText.text = status.describe(this)
        serviceButton.setText(if (MqttService.isRunning) R.string.action_stop_service else R.string.action_start_service)
    }

    private fun renderSubscriptions() {
        subscriptions.removeAllViews()
        val list = AppGraph.settings.subscriptions()
        noSubscriptions.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        for (s in list) {
            val row = layoutInflater.inflate(R.layout.item_subscription, subscriptions, false)
            row.findViewById<TextView>(R.id.subscription_text).text = getString(R.string.subscription_item, s.filter, s.qos)
            row.findViewById<View>(R.id.remove_button).setOnClickListener {
                AppGraph.settings.removeSubscription(s.filter)
                AppGraph.controller?.unsubscribe(s.filter)
                renderSubscriptions()
            }
            subscriptions.addView(row)
        }
    }

    private fun renderMessages(list: List<ReceivedMessage>) {
        messages.text =
            if (list.isEmpty()) {
                getString(R.string.no_messages)
            } else {
                list.joinToString("\n\n") {
                    getString(R.string.message_item, timeFormat.format(Date(it.timestamp)), it.topic, it.qos, it.payload)
                }
            }
    }

    companion object {
        const val REQUEST_NOTIFICATIONS = 1
    }
}
