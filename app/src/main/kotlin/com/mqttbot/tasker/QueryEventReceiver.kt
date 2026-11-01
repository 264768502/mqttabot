package com.mqttbot.tasker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Answers Tasker's QUERY_CONDITION for the "MQTT message received" event. */
class QueryEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TaskerApi.ACTION_QUERY_CONDITION) return
        val filter = EventFilterConfig.fromBundle(intent.getBundleExtra(TaskerApi.EXTRA_BUNDLE))
        if (filter == null) {
            resultCode = TaskerApi.RESULT_CONDITION_UNKNOWN
            return
        }
        val message = MessageBundles.fromPassThrough(intent.getBundleExtra(TaskerApi.EXTRA_PASS_THROUGH_DATA))
        if (message == null || !filter.matches(message)) {
            resultCode = TaskerApi.RESULT_CONDITION_UNSATISFIED
            return
        }
        getResultExtras(true).putBundle(TaskerApi.EXTRA_VARIABLES, MessageBundles.toVariables(message))
        resultCode = TaskerApi.RESULT_CONDITION_SATISFIED
    }
}
