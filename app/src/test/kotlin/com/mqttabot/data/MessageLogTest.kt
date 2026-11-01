package com.mqttabot.data

import com.mqttabot.mqtt.ReceivedMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageLogTest {
    private fun msg(i: Int) = ReceivedMessage("t$i", "p", 0, false, i.toLong())

    @Test
    fun newestFirstAndBounded() {
        val log = MessageLog(capacity = 2)
        val seen = mutableListOf<List<ReceivedMessage>>()
        log.updates.observe { seen += it }
        log.add(msg(1))
        log.add(msg(2))
        log.add(msg(3))
        assertEquals(listOf(msg(3), msg(2)), log.updates.value)
        log.clear()
        assertEquals(emptyList<ReceivedMessage>(), log.updates.value)
        assertEquals(5, seen.size)
    }

    @Test
    fun defaultCapacity() {
        val log = MessageLog()
        repeat(MessageLog.DEFAULT_CAPACITY + 5) { log.add(msg(it)) }
        assertEquals(MessageLog.DEFAULT_CAPACITY, log.updates.value.size)
    }
}
