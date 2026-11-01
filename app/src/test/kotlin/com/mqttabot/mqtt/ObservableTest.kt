package com.mqttabot.mqtt

import org.junit.Assert.assertEquals
import org.junit.Test

class ObservableTest {
    @Test
    fun observeDeliversCurrentAndUpdates() {
        val o = Observable(1)
        val seen = mutableListOf<Int>()
        val l: (Int) -> Unit = { seen += it }
        o.observe(l)
        o.set(2)
        o.remove(l)
        o.set(3)
        assertEquals(listOf(1, 2), seen)
        assertEquals(3, o.value)
    }

    @Test
    fun clearListeners() {
        val o = Observable("a")
        val seen = mutableListOf<String>()
        o.observe { seen += it }
        o.clearListeners()
        o.set("b")
        assertEquals(listOf("a"), seen)
    }
}
