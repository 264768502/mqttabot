package com.mqttbot.tasker

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mqttbot.AppGraph
import com.mqttbot.mqtt.BrokerConfig
import com.mqttbot.mqtt.ConnectionController
import com.mqttbot.mqtt.Observable
import com.mqttbot.mqtt.ConnectionStatus
import com.mqttbot.mqtt.ReceivedMessage
import com.mqttbot.testutil.FakeGateway
import com.mqttbot.testutil.ManualTaskRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class TaskerReceiversTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val gateways = mutableListOf<FakeGateway>()
    private var nextGateway: () -> FakeGateway = { FakeGateway() }

    @Before
    fun setUp() {
        AppGraph.ioExecutor = java.util.concurrent.Executor { it.run() }
        AppGraph.gatewayFactory = { nextGateway().also { gateways += it } }
        AppGraph.settings.saveBroker(BrokerConfig(host = "h", clientId = "c"))
    }

    private fun query(config: Bundle?, data: Bundle?, action: String = TaskerApi.ACTION_QUERY_CONDITION): Pair<Int, Bundle?> {
        val intent = Intent(action).setComponent(ComponentName(context, QueryEventReceiver::class.java))
        config?.let { intent.putExtra(TaskerApi.EXTRA_BUNDLE, it) }
        data?.let { intent.putExtra(TaskerApi.EXTRA_PASS_THROUGH_DATA, it) }
        var code = -1
        var extras: Bundle? = null
        val result = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                code = resultCode
                extras = getResultExtras(false)
            }
        }
        context.sendOrderedBroadcast(intent, null, result, null, Activity.RESULT_CANCELED, null, null)
        shadowOf(Looper.getMainLooper()).idle()
        return code to extras
    }

    private val message = ReceivedMessage("home/door", "open", 1, false, 5)

    @Test
    fun querySatisfiedSetsVariables() {
        val (code, extras) = query(EventFilterConfig("home/+", "", 1).toBundle(), MessageBundles.toPassThrough(message))
        assertEquals(TaskerApi.RESULT_CONDITION_SATISFIED, code)
        val vars = extras!!.getBundle(TaskerApi.EXTRA_VARIABLES)!!
        assertEquals("home/door", vars.getString(TaskerApi.VAR_TOPIC))
        assertEquals("open", vars.getString(TaskerApi.VAR_PAYLOAD))
    }

    @Test
    fun queryUnsatisfiedAndUnknown() {
        assertEquals(
            TaskerApi.RESULT_CONDITION_UNSATISFIED,
            query(EventFilterConfig("other", "", 0).toBundle(), MessageBundles.toPassThrough(message)).first,
        )
        assertEquals(TaskerApi.RESULT_CONDITION_UNSATISFIED, query(EventFilterConfig("#", "", 0).toBundle(), null).first)
        assertEquals(TaskerApi.RESULT_CONDITION_UNKNOWN, query(null, MessageBundles.toPassThrough(message)).first)
    }

    @Test
    fun queryIgnoresOtherActions() {
        assertEquals(Activity.RESULT_CANCELED, query(EventFilterConfig("#", "", 0).toBundle(), null, "other").first)
    }

    private fun fire(bundle: Bundle?, action: String = TaskerApi.ACTION_FIRE_SETTING) {
        val intent = Intent(action).setComponent(ComponentName(context, FirePublishReceiver::class.java))
        bundle?.let { intent.putExtra(TaskerApi.EXTRA_BUNDLE, it) }
        context.sendBroadcast(intent)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun firePublishesWithOneShotConnection() {
        fire(PublishActionConfig("a/b", "on", 1, true).toBundle())
        assertEquals(listOf(FakeGateway.Published("a/b", "on", 1, true)), gateways.single().published)
        assertEquals("h", gateways.single().connectedWith!!.host)
    }

    @Test
    fun fireUsesSharedConnectionWhenServiceConnected() {
        val shared = FakeGateway()
        val c = ConnectionController(
            { BrokerConfig(host = "h") }, { emptyList() }, { shared }, ManualTaskRunner(), Observable(ConnectionStatus.DISCONNECTED), {},
        )
        c.start()
        AppGraph.controller = c
        fire(PublishActionConfig("a", "x", 0, false).toBundle())
        assertEquals(1, shared.published.size)
        assertTrue(gateways.isEmpty())
    }

    @Test
    fun fireFailureIsSwallowed() {
        nextGateway = { FakeGateway(connectError = IOException("down")) }
        fire(PublishActionConfig("a", "x", 0, false).toBundle())
        assertEquals(1, gateways.single().disconnects)
    }

    @Test
    fun fireIgnoresInvalidIntents() {
        fire(null)
        fire(PublishActionConfig("a", "x", 0, false).toBundle(), action = "other")
        assertTrue(gateways.isEmpty())
    }

    @Test
    fun dispatcherBroadcastsRequestQuery() {
        TaskerEventDispatcher.dispatch(context, message)
        TaskerEventDispatcher.dispatch(context, message)
        val sent = shadowOf(context as android.app.Application).broadcastIntents.filter { it.action == TaskerApi.ACTION_REQUEST_QUERY }
        assertEquals(2, sent.size)
        val first = sent[0]
        assertEquals(EventConfigActivity::class.java.name, first.getStringExtra(TaskerApi.EXTRA_ACTIVITY))
        val data = first.getBundleExtra(TaskerApi.EXTRA_PASS_THROUGH_DATA)!!
        assertEquals(message, MessageBundles.fromPassThrough(data))
        val id1 = data.getInt(TaskerApi.KEY_PASS_THROUGH_MESSAGE_ID)
        val id2 = sent[1].getBundleExtra(TaskerApi.EXTRA_PASS_THROUGH_DATA)!!.getInt(TaskerApi.KEY_PASS_THROUGH_MESSAGE_ID)
        assertTrue(id1 > 0 && id2 > 0 && id1 != id2)
        assertNull(first.`package`)
    }
}
