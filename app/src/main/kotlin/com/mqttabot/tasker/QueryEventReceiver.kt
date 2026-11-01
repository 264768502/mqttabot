package com.mqttabot.tasker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mqttabot.log.AppLog

/** Answers Tasker's QUERY_CONDITION for the "MQTT message received" event. */
class QueryEventReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != TaskerApi.ACTION_QUERY_CONDITION) return
        val filter = EventFilterConfig.fromBundle(intent.getBundleExtra(TaskerApi.EXTRA_BUNDLE))
        if (filter == null) {
            AppLog.w(TAG, "Query without a valid event bundle")
            resultCode = TaskerApi.RESULT_CONDITION_UNKNOWN
            return
        }
        val message = MessageBundles.fromPassThrough(intent.getBundleExtra(TaskerApi.EXTRA_PASS_THROUGH_DATA))
        if (message == null) {
            AppLog.d(TAG, "Query for ${filter.topicFilter} without a message (not our request)")
            resultCode = TaskerApi.RESULT_CONDITION_UNSATISFIED
            return
        }
        if (!filter.matches(message)) {
            AppLog.d(TAG, "Event ${filter.topicFilter} / ${AppLog.preview(filter.payloadPattern)} does not match ${message.topic}")
            resultCode = TaskerApi.RESULT_CONDITION_UNSATISFIED
            return
        }
        AppLog.i(TAG, "Event ${filter.topicFilter} matches ${message.topic}: firing")
        getResultExtras(true).putBundle(TaskerApi.EXTRA_VARIABLES, MessageBundles.toVariables(message))
        resultCode = TaskerApi.RESULT_CONDITION_SATISFIED
    }

    private companion object {
        const val TAG = "TaskerQuery"
    }
}
