package com.mqttabot.log

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import java.util.concurrent.Executor

@RunWith(AndroidJUnit4::class)
class AppLogTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var file: LogFile

    @Before
    fun setUp() {
        file = LogFile(tmp.root, Executor { it.run() })
        AppLog.file = file
        AppLog.clock = { 0L }
    }

    @After
    fun tearDown() {
        AppLog.file = null
        AppLog.clock = System::currentTimeMillis
    }

    @Test
    fun writesEveryLevelToFileAndLogcat() {
        AppLog.d("T", "debug")
        AppLog.i("T", "info")
        AppLog.w("T", "warn")
        AppLog.w("T", "warn with error", RuntimeException("w"))
        AppLog.e("T", "error")
        AppLog.e("T", "error with error", RuntimeException("e"))
        val text = file.current.readText()
        for (s in listOf("D/T", "I/T", "W/T", "E/T", "debug", "warn with error", "java.lang.RuntimeException: e")) {
            assertTrue(s, text.contains(s))
        }
        assertEquals(6, ShadowLog.getLogsForTag("T").size)
        assertTrue(
            ShadowLog
                .getLogsForTag("T")
                .last()
                .msg
                .contains("RuntimeException: e"),
        )
    }

    @Test
    fun nothingOnDiskWhileOff() {
        AppLog.file = null
        AppLog.i("T", "only logcat")
        assertTrue(file.files().isEmpty())
    }

    @Test
    fun previewKeepsPayloadsShortAndOnOneLine() {
        assertEquals("\"a\\nb\"", AppLog.preview("a\nb"))
        assertEquals("\"abc…\" (5 chars)", AppLog.preview("abcde", max = 3))
        assertEquals("\"${"x".repeat(64)}\"", AppLog.preview("x".repeat(64)))
    }

    @Test
    fun crashHandlerLogsThenDelegatesAndInstallsOnce() {
        val seen = mutableListOf<Throwable>()
        val previous = Thread.UncaughtExceptionHandler { _, e -> seen += e }
        val handler = CrashHandler(previous)
        val error = IllegalStateException("crash")
        handler.uncaughtException(Thread.currentThread(), error)
        assertEquals(listOf<Throwable>(error), seen)
        assertTrue(file.current.readText().contains("E/Crash"))
        // Without a previous handler or a log file it still just logs.
        AppLog.file = null
        CrashHandler(null).uncaughtException(Thread.currentThread(), error)

        val original = Thread.getDefaultUncaughtExceptionHandler()
        try {
            Thread.setDefaultUncaughtExceptionHandler(previous)
            AppLog.installCrashHandler()
            val installed = Thread.getDefaultUncaughtExceptionHandler()
            assertTrue(installed is CrashHandler)
            AppLog.installCrashHandler()
            assertSame(installed, Thread.getDefaultUncaughtExceptionHandler())
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original)
        }
    }
}
