package com.mqttabot.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
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
    private lateinit var statusIcon: ImageView
    private lateinit var statusProgress: View
    private lateinit var statusText: TextView
    private lateinit var statusDetail: TextView
    private lateinit var brokerText: TextView
    private lateinit var serviceButton: MaterialButton
    private lateinit var topicLayout: TextInputLayout
    private lateinit var topic: EditText
    private lateinit var payload: EditText
    private lateinit var qos: QosToggleGroup
    private lateinit var retained: CompoundButton
    private lateinit var sendButton: Button
    private lateinit var publishResult: TextView
    private lateinit var filterLayout: TextInputLayout
    private lateinit var filter: EditText
    private lateinit var filterQos: QosToggleGroup
    private lateinit var subscriptions: LinearLayout
    private lateinit var noSubscriptions: TextView
    private lateinit var messages: LinearLayout
    private lateinit var noMessages: TextView

    private val statusListener: (ConnectionStatus) -> Unit = { runOnUiThread { renderStatus(it) } }
    private val logListener: (List<ReceivedMessage>) -> Unit = { runOnUiThread { renderMessages(it) } }
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setEdgeToEdgeContentView(R.layout.activity_main)
        statusIcon = findViewById(R.id.status_icon)
        statusProgress = findViewById(R.id.status_progress)
        statusText = findViewById(R.id.status_text)
        statusDetail = findViewById(R.id.status_detail)
        brokerText = findViewById(R.id.broker_text)
        serviceButton = findViewById(R.id.service_button)
        topicLayout = findViewById(R.id.topic_layout)
        topic = findViewById(R.id.topic_input)
        payload = findViewById(R.id.payload_input)
        qos = findViewById(R.id.qos_group)
        retained = findViewById(R.id.retain_check)
        sendButton = findViewById(R.id.send_button)
        publishResult = findViewById(R.id.publish_result)
        filterLayout = findViewById(R.id.filter_layout)
        filter = findViewById(R.id.filter_input)
        filterQos = findViewById(R.id.filter_qos_group)
        subscriptions = findViewById(R.id.subscriptions_container)
        noSubscriptions = findViewById(R.id.no_subscriptions)
        messages = findViewById(R.id.messages_container)
        noMessages = findViewById(R.id.no_messages)

        // The only menu item is broker settings.
        findViewById<MaterialToolbar>(R.id.toolbar).setOnMenuItemClickListener {
            startActivity(Intent(this, BrokerSettingsActivity::class.java))
            true
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
        val qosLevel = qos.qos
        val retain = retained.isChecked
        sendButton.isEnabled = false
        publishResult.isVisible = true
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
        val subscription = Subscription(filterText, filterQos.qos)
        AppGraph.settings.addSubscription(subscription)
        AppGraph.controller?.subscribe(subscription)
        filter.setText("")
        renderSubscriptions()
    }

    private fun renderStatus(status: ConnectionStatus) {
        val style = status.state.style
        statusText.setText(style.headline)
        statusIcon.setImageResource(style.icon)
        statusIcon.backgroundTintList = ColorStateList.valueOf(MaterialColors.getColor(statusIcon, style.container))
        statusIcon.imageTintList = ColorStateList.valueOf(MaterialColors.getColor(statusIcon, style.onContainer))
        statusIcon.isInvisible = style.busy
        statusProgress.isVisible = style.busy
        statusDetail.isVisible = style.showDetail
        statusDetail.text = status.detail
        if (MqttService.isRunning) {
            serviceButton.setText(R.string.action_stop_service)
            serviceButton.setIconResource(R.drawable.ic_stop)
        } else {
            serviceButton.setText(R.string.action_start_service)
            serviceButton.setIconResource(R.drawable.ic_play_arrow)
        }
    }

    private fun renderSubscriptions() {
        subscriptions.removeAllViews()
        val list = AppGraph.settings.subscriptions()
        noSubscriptions.isVisible = list.isEmpty()
        for (s in list) {
            val row = layoutInflater.inflate(R.layout.item_subscription, subscriptions, false)
            row.findViewById<TextView>(R.id.subscription_text).text = s.filter
            row.findViewById<TextView>(R.id.subscription_qos).text = getString(R.string.qos_value, s.qos)
            row.findViewById<View>(R.id.remove_button).setOnClickListener {
                AppGraph.settings.removeSubscription(s.filter)
                AppGraph.controller?.unsubscribe(s.filter)
                renderSubscriptions()
            }
            subscriptions.addView(row)
        }
    }

    // Rows are reused across updates; only the difference in count is inflated or removed.
    private fun renderMessages(list: List<ReceivedMessage>) {
        noMessages.isVisible = list.isEmpty()
        while (messages.childCount > list.size) messages.removeViewAt(messages.childCount - 1)
        while (messages.childCount < list.size) messages.addView(layoutInflater.inflate(R.layout.item_message, messages, false))
        list.forEachIndexed { i, m ->
            val row = messages.getChildAt(i)
            row.findViewById<TextView>(R.id.message_topic).text = m.topic
            row.findViewById<TextView>(R.id.message_meta).text =
                getString(R.string.message_meta, timeFormat.format(Date(m.timestamp)), m.qos)
            row.findViewById<TextView>(R.id.message_payload).text = m.payload
        }
    }

    companion object {
        const val REQUEST_NOTIFICATIONS = 1
    }
}
