package com.mqttabot.log

import android.util.Log
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.TimeZone
import java.util.concurrent.Executor

class LogFileTest {
    @get:Rule val tmp = TemporaryFolder()

    private val direct = Executor { it.run() }

    private fun log(
        maxFileBytes: Long = 100,
        maxFiles: Int = 3,
    ) = LogFile(tmp.root.resolve("logs"), direct, maxFileBytes, maxFiles)

    @Test
    fun appendsAndRotatesWithinTheLimit() {
        val log = log()
        assertEquals(emptyList<Any>(), log.files())
        assertEquals(0, log.sizeBytes())
        repeat(20) { log.append("line %02d ".format(it) + "x".repeat(30) + "\n") }
        val files = log.files()
        // Current file plus at most two rotated ones, never more than the limit (+ one line).
        assertEquals(listOf("mqttabot.2.log", "mqttabot.1.log", "mqttabot.log"), files.map { it.name })
        assertTrue(log.sizeBytes() <= log.limitBytes + 40)
        assertEquals(300, log.limitBytes)
        // Oldest lines were dropped; the newest survive, in order.
        val text = String(log.export("HEADER\n"))
        assertTrue(text.startsWith("HEADER\n"))
        assertFalse(text.contains("line 00"))
        assertTrue(text.indexOf("line 18") < text.indexOf("line 19"))
        assertTrue(text.trimEnd().endsWith("x"))
    }

    @Test
    fun clearDeletesEverything() {
        val log = log()
        repeat(10) { log.append("y".repeat(60) + "\n") }
        log.clear()
        assertEquals(emptyList<Any>(), log.files())
        assertEquals(tmp.root.resolve("logs"), log.dir)
    }

    @Test
    fun formatsLinesAndStackTraces() {
        val zone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            assertEquals("1970-01-01 00:00:01.234 I/Tag [main]: hello\n", LogFile.format(1234, Log.INFO, "Tag", "main", "hello", null))
            val withError = LogFile.format(0, Log.WARN, "T", "io", "failed", IllegalStateException("boom"))
            assertTrue(withError.startsWith("1970-01-01 00:00:00.000 W/T [io]: failed\njava.lang.IllegalStateException: boom\n\tat "))
            val long = LogFile.format(0, Log.DEBUG, "T", "t", "z".repeat(LogFile.MAX_MESSAGE_CHARS + 10), null)
            assertTrue(long.endsWith("z…\n"))
            assertEquals(
                listOf('V', 'D', 'E', 'A'),
                listOf(Log.VERBOSE, Log.DEBUG, Log.ERROR, Log.ASSERT).map {
                    LogFile.format(0, it, "T", "t", "m", null)[24]
                },
            )
        } finally {
            TimeZone.setDefault(zone)
        }
    }

    @Test
    fun defaultLimitIsTwoMegabytes() {
        assertEquals(2_000_000L, LogFile(tmp.root, direct).limitBytes)
    }
}
