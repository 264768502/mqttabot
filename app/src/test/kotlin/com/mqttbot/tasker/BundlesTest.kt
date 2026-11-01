package com.mqttbot.tasker

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mqttbot.mqtt.ReceivedMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BundlesTest {
    @Test
    fun publishConfigRoundTrip() {
        val c = PublishActionConfig("t/%x", "%payload", 2, true)
        val b = c.toBundle()
        assertEquals(c, PublishActionConfig.fromBundle(b))
        assertEquals("${PublishActionConfig.KEY_TOPIC} ${PublishActionConfig.KEY_PAYLOAD}", b.getString(TaskerApi.KEY_VARIABLE_REPLACE_KEYS))
        assertEquals(1, b.getInt(PublishActionConfig.KEY_VERSION))
    }

    @Test
    fun publishConfigDefaultsAndMissing() {
        assertNull(PublishActionConfig.fromBundle(null))
        assertNull(PublishActionConfig.fromBundle(Bundle()))
        val minimal = Bundle().apply { putString(PublishActionConfig.KEY_TOPIC, "t") }
        assertEquals(PublishActionConfig("t", "", 0, false), PublishActionConfig.fromBundle(minimal))
    }

    @Test
    fun eventConfigRoundTripAndDefaults() {
        val c = EventFilterConfig("a/+", "on|off", 1)
        assertEquals(c, EventFilterConfig.fromBundle(c.toBundle()))
        assertNull(EventFilterConfig.fromBundle(null))
        assertNull(EventFilterConfig.fromBundle(Bundle()))
        val minimal = Bundle().apply { putString(EventFilterConfig.KEY_TOPIC_FILTER, "#") }
        assertEquals(EventFilterConfig("#", "", 0), EventFilterConfig.fromBundle(minimal))
    }

    @Test
    fun eventMatching() {
        val m = ReceivedMessage("home/room/temp", "23.5", 0, false, 0)
        assertTrue(EventFilterConfig("home/#", "", 0).matches(m))
        assertFalse(EventFilterConfig("office/#", "", 0).matches(m))
        assertTrue(EventFilterConfig("home/+/temp", "^2\\d", 0).matches(m))
        assertFalse(EventFilterConfig("home/+/temp", "^3", 0).matches(m))
        // A pattern that slipped past validation never matches.
        assertFalse(EventFilterConfig("#", "(", 0).matches(m))
    }

    @Test
    fun patternValidation() {
        assertTrue(EventFilterConfig.isValidPattern(""))
        assertTrue(EventFilterConfig.isValidPattern("a.*b"))
        assertFalse(EventFilterConfig.isValidPattern("[a"))
    }

    @Test
    fun messagePassThroughAndVariables() {
        val m = ReceivedMessage("t", "p", 1, true, 99)
        assertEquals(m, MessageBundles.fromPassThrough(MessageBundles.toPassThrough(m)))
        assertNull(MessageBundles.fromPassThrough(null))
        assertNull(MessageBundles.fromPassThrough(Bundle()))
        val v = MessageBundles.toVariables(m)
        assertEquals("t", v.getString(TaskerApi.VAR_TOPIC))
        assertEquals("p", v.getString(TaskerApi.VAR_PAYLOAD))
        assertEquals("1", v.getString(TaskerApi.VAR_QOS))
        assertEquals("true", v.getString(TaskerApi.VAR_RETAINED))
        assertEquals("99", v.getString(TaskerApi.VAR_RECEIVED_AT))
    }

    @Test
    fun partialPassThroughUsesDefaults() {
        val b = Bundle().apply { putString("com.mqttbot.message.TOPIC", "t") }
        assertEquals(ReceivedMessage("t", "", 0, false, 0), MessageBundles.fromPassThrough(b))
    }
}
