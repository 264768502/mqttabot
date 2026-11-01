package com.mqttabot.tasker

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
import com.mqttabot.AppGraph
import com.mqttabot.R
import com.mqttabot.data.Subscription
import com.mqttabot.mqtt.Topics
import com.mqttabot.ui.FormField
import com.mqttabot.ui.QosSelector
import com.mqttabot.ui.TAG_FILTER
import com.mqttabot.ui.TAG_QOS
import com.mqttabot.ui.theme.setThemedContent

const val TAG_PATTERN = "pattern"

/** Tasker edit screen for the "MQTT message received" event. */
class EventConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initial = EventFilterConfig.fromBundle(intent.getBundleExtra(TaskerApi.EXTRA_BUNDLE))
        setThemedContent {
            var filter by rememberSaveable { mutableStateOf(initial?.topicFilter.orEmpty()) }
            var pattern by rememberSaveable { mutableStateOf(initial?.payloadPattern.orEmpty()) }
            var qos by rememberSaveable { mutableIntStateOf(initial?.qos?.coerceIn(0, 2) ?: 1) }
            var filterError by rememberSaveable { mutableStateOf(false) }
            var patternError by rememberSaveable { mutableStateOf(false) }
            TaskerEditScaffold(
                title = stringResource(R.string.tasker_event_label),
                hint = stringResource(R.string.tasker_event_hint),
                onCancel = ::cancel,
                onSave = {
                    val filterText = filter.trim()
                    filterError = !Topics.isValidFilter(filterText)
                    patternError = !filterError && !EventFilterConfig.isValidPattern(pattern)
                    if (!filterError && !patternError) save(EventFilterConfig(filterText, pattern, qos))
                },
            ) {
                FormField(
                    filter,
                    { filter = it },
                    stringResource(R.string.hint_filter),
                    TAG_FILTER,
                    modifier = Modifier.padding(top = 8.dp),
                    error = if (filterError) stringResource(R.string.error_filter) else null,
                )
                FormField(
                    pattern,
                    { pattern = it },
                    stringResource(R.string.hint_pattern),
                    TAG_PATTERN,
                    error = if (patternError) stringResource(R.string.error_pattern) else null,
                )
                QosSelector(qos, { qos = it }, TAG_QOS, Modifier.padding(top = 8.dp))
            }
        }
    }

    private fun cancel() {
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun save(config: EventFilterConfig) {
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
