package com.mqttabot.tasker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputLayout
import com.mqttabot.R
import com.mqttabot.mqtt.Topics
import com.mqttabot.ui.QosToggleGroup
import com.mqttabot.ui.setEdgeToEdgeContentView

/** Tasker edit screen for the "MQTT publish" action. */
class PublishActionActivity : AppCompatActivity() {
    private lateinit var topicLayout: TextInputLayout
    private lateinit var topic: EditText
    private lateinit var payload: EditText
    private lateinit var qos: QosToggleGroup
    private lateinit var retained: CompoundButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setEdgeToEdgeContentView(R.layout.activity_publish_action)
        topicLayout = findViewById(R.id.topic_layout)
        topic = findViewById(R.id.topic_input)
        payload = findViewById(R.id.payload_input)
        qos = findViewById(R.id.qos_group)
        retained = findViewById(R.id.retain_check)

        PublishActionConfig.fromBundle(intent.getBundleExtra(TaskerApi.EXTRA_BUNDLE))?.let {
            topic.setText(it.topic)
            payload.setText(it.payload)
            qos.qos = it.qos
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
        val config = PublishActionConfig(topicText, payload.text.toString(), qos.qos, retained.isChecked)
        val result =
            Intent()
                .putExtra(TaskerApi.EXTRA_BUNDLE, config.toBundle())
                .putExtra(TaskerApi.EXTRA_STRING_BLURB, getString(R.string.tasker_publish_blurb, config.topic, config.qos))
        setResult(RESULT_OK, result)
        finish()
    }
}
