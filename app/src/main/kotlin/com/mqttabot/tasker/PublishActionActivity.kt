package com.mqttabot.tasker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mqttabot.R
import com.mqttabot.data.AppLanguage
import com.mqttabot.log.AppLog
import com.mqttabot.mqtt.Topics
import com.mqttabot.ui.FormField
import com.mqttabot.ui.QosSelector
import com.mqttabot.ui.SwitchRow
import com.mqttabot.ui.TAG_PAYLOAD
import com.mqttabot.ui.TAG_QOS
import com.mqttabot.ui.TAG_RETAIN
import com.mqttabot.ui.TAG_TOPIC
import com.mqttabot.ui.theme.setThemedContent

/** Tasker edit screen for the "MQTT publish" action. */
class PublishActionActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLanguage.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initial = PublishActionConfig.fromBundle(intent.getBundleExtra(TaskerApi.EXTRA_BUNDLE))
        AppLog.i(TAG, "Opened: ${initial ?: "new"}")
        setThemedContent {
            var topic by rememberSaveable { mutableStateOf(initial?.topic.orEmpty()) }
            var payload by rememberSaveable { mutableStateOf(initial?.payload.orEmpty()) }
            var qos by rememberSaveable { mutableIntStateOf(initial?.qos?.coerceIn(0, 2) ?: 0) }
            var retained by rememberSaveable { mutableStateOf(initial?.retained ?: false) }
            var topicError by rememberSaveable { mutableStateOf(false) }
            TaskerEditScaffold(
                title = stringResource(R.string.tasker_publish_label),
                hint = stringResource(R.string.tasker_publish_hint),
                onCancel = ::cancel,
                onSave = {
                    val topicText = topic.trim()
                    topicError = !Topics.isValidTopicName(topicText)
                    if (!topicError) save(PublishActionConfig(topicText, payload, qos, retained))
                },
            ) {
                FormField(
                    topic,
                    { topic = it },
                    stringResource(R.string.hint_topic),
                    TAG_TOPIC,
                    modifier = Modifier.padding(top = 8.dp),
                    error = if (topicError) stringResource(R.string.error_topic) else null,
                )
                FormField(payload, { payload = it }, stringResource(R.string.hint_payload), TAG_PAYLOAD, singleLine = false)
                QosSelector(qos, { qos = it }, TAG_QOS, Modifier.padding(top = 8.dp))
                SwitchRow(stringResource(R.string.label_retain), retained, { retained = it }, TAG_RETAIN)
            }
        }
    }

    private fun cancel() {
        AppLog.i(TAG, "Cancelled")
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun save(config: PublishActionConfig) {
        val result =
            Intent()
                .putExtra(TaskerApi.EXTRA_BUNDLE, config.toBundle())
                .putExtra(TaskerApi.EXTRA_STRING_BLURB, getString(R.string.tasker_publish_blurb, config.topic, config.qos))
        AppLog.i(TAG, "Saved: $config")
        setResult(RESULT_OK, result)
        finish()
    }

    private companion object {
        const val TAG = "TaskerPublishEdit"
    }
}
