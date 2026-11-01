package com.mqttbot.mqtt

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicsTest {
    @Test
    fun topicNames() {
        assertTrue(Topics.isValidTopicName("a/b/c"))
        assertTrue(Topics.isValidTopicName("/"))
        assertTrue(Topics.isValidTopicName("home/%room"))
        assertFalse(Topics.isValidTopicName(""))
        assertFalse(Topics.isValidTopicName("a/+"))
        assertFalse(Topics.isValidTopicName("a/#"))
        assertFalse(Topics.isValidTopicName("a\u0000b"))
        assertFalse(Topics.isValidTopicName("x".repeat(65536)))
    }

    @Test
    fun filters() {
        assertTrue(Topics.isValidFilter("a/b"))
        assertTrue(Topics.isValidFilter("#"))
        assertTrue(Topics.isValidFilter("a/+/c/#"))
        assertTrue(Topics.isValidFilter("+"))
        assertFalse(Topics.isValidFilter(""))
        assertFalse(Topics.isValidFilter("a/#/c"))
        assertFalse(Topics.isValidFilter("a/b#"))
        assertFalse(Topics.isValidFilter("a/b+"))
    }

    @Test
    fun matching() {
        assertTrue(Topics.matches("a/b", "a/b"))
        assertFalse(Topics.matches("a/b", "a/c"))
        assertTrue(Topics.matches("a/+", "a/x"))
        assertFalse(Topics.matches("a/+", "a/x/y"))
        assertFalse(Topics.matches("a/+/c", "a/x"))
        assertTrue(Topics.matches("a/#", "a"))
        assertTrue(Topics.matches("a/#", "a/x/y"))
        assertTrue(Topics.matches("#", "anything/here"))
        assertFalse(Topics.matches("a/b/c", "a/b"))
    }

    @Test
    fun wildcardsDoNotMatchSystemTopics() {
        assertFalse(Topics.matches("#", "\$SYS/x"))
        assertFalse(Topics.matches("+/x", "\$SYS/x"))
        assertTrue(Topics.matches("\$SYS/#", "\$SYS/x"))
        assertTrue(Topics.matches("a/#", "a/\$x"))
    }
}
