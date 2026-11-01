package com.mqttbot.testutil

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.mqttbot.tasker.FirePublishReceiver
import com.mqttbot.tasker.QueryEventReceiver
import com.mqttbot.tasker.TaskerApi
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.CopyOnWriteArrayList

/** Mimics the host side of the Tasker / Locale plugin protocol. */
class FakeTasker(private val context: Context) : AutoCloseable {
    /** Pass-through data of every REQUEST_QUERY broadcast received from the plugin. */
    val requests = CopyOnWriteArrayList<Bundle>()

    private val requestReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            if (intent.getStringExtra(TaskerApi.EXTRA_ACTIVITY) != null) {
                requests += intent.getBundleExtra(TaskerApi.EXTRA_PASS_THROUGH_DATA)!!
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            context, requestReceiver, IntentFilter(TaskerApi.ACTION_REQUEST_QUERY), ContextCompat.RECEIVER_EXPORTED,
        )
    }

    /** Replaces %variables in the keys the plugin listed for replacement. */
    fun substitute(stored: Bundle, variables: Map<String, String>): Bundle {
        val out = Bundle(stored)
        val keys = stored.getString(TaskerApi.KEY_VARIABLE_REPLACE_KEYS).orEmpty().split(' ').filter { it.isNotEmpty() }
        for (key in keys) {
            var value = stored.getString(key) ?: continue
            for ((name, v) in variables) value = value.replace(name, v)
            out.putString(key, value)
        }
        return out
    }

    fun fireAction(bundle: Bundle) {
        context.sendBroadcast(
            Intent(TaskerApi.ACTION_FIRE_SETTING)
                .setComponent(ComponentName(context, FirePublishReceiver::class.java))
                .putExtra(TaskerApi.EXTRA_BUNDLE, bundle),
        )
        // Deliver the broadcast; the receiver then publishes on a background thread.
        shadowOf(Looper.getMainLooper()).idle()
    }

    data class QueryResult(val resultCode: Int, val variables: Bundle?)

    /** Sends QUERY_CONDITION for a stored event config, as Tasker does after REQUEST_QUERY. */
    fun queryEvent(eventConfig: Bundle, passThrough: Bundle): QueryResult {
        var result: QueryResult? = null
        val resultReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                result = QueryResult(resultCode, getResultExtras(false)?.getBundle(TaskerApi.EXTRA_VARIABLES))
            }
        }
        val intent = Intent(TaskerApi.ACTION_QUERY_CONDITION)
            .setComponent(ComponentName(context, QueryEventReceiver::class.java))
            .putExtra(TaskerApi.EXTRA_BUNDLE, eventConfig)
            .putExtra(TaskerApi.EXTRA_PASS_THROUGH_DATA, passThrough)
        context.sendOrderedBroadcast(intent, null, resultReceiver, null, Activity.RESULT_CANCELED, null, null)
        shadowOf(Looper.getMainLooper()).idle()
        return checkNotNull(result) { "No query result" }
    }

    override fun close() = context.unregisterReceiver(requestReceiver)
}
