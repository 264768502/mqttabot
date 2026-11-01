package com.mqttbot.tasker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputLayout
import com.mqttbot.R
import com.mqttbot.mqtt.Topics
import com.mqttbot.ui.QosAdapter

/** Tasker edit screen for the "MQTT publish" action. */
class PublishActionActivity : AppCompatActivity() {
    private lateinit var topicLayout: TextInputLayout
    private lateinit var topic: EditText
    private lateinit var payload: EditText
    private lateinit var qos: Spinner
    private lateinit var retained: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_publish_action)
        topicLayout = findViewById(R.id.topic_layout)
        topic = findViewById(R.id.topic_input)
        payload = findViewById(R.id.payload_input)
        qos = findViewById(R.id.qos_spinner)
        retained = findViewById(R.id.retain_check)
        qos.adapter = QosAdapter.create(this)

        PublishActionConfig.fromBundle(intent.getBundleExtra(TaskerApi.EXTRA_BUNDLE))?.let {
            topic.setText(it.topic)
            payload.setText(it.payload)
            qos.setSelection(it.qos.coerceIn(0, 2))
            retained.isChecked = it.retained
        }
        findViewById<Button>(R.id.save_button).setOnClickListener { save() }
        findViewById<Button>(R.id.cancel_button).setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun save() {
        val topicText = topic.text.toString().trim()
        if (!Topics.isValidTopicName(topicText)) {
            topicLayout.error = getString(R.string.error_topic)
            return
        }
        val config = PublishActionConfig(topicText, payload.text.toString(), qos.selectedItemPosition, retained.isChecked)
        val result = Intent()
            .putExtra(TaskerApi.EXTRA_BUNDLE, config.toBundle())
            .putExtra(TaskerApi.EXTRA_STRING_BLURB, getString(R.string.tasker_publish_blurb, config.topic, config.qos))
        setResult(RESULT_OK, result)
        finish()
    }
}
