package com.mqttabot.log

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mqttabot.AppGraph
import com.mqttabot.data.Subscription
import com.mqttabot.mqtt.BrokerConfig
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DiagnosticsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun reportDescribesSettingsWithoutSecrets() {
        AppGraph.settings.saveBroker(
            BrokerConfig(host = "h", port = 8883, tls = true, username = "alice", password = "s3cret", clientId = "cid"),
        )
        AppGraph.settings.addSubscription(Subscription("a/#", 1))
        val report = Diagnostics.report(context)
        for (s in listOf(
            "MqttABot diagnostic report",
            "App: ",
            "Android: ",
            "ssl://h:8883",
            "clientId=cid",
            "username=set",
            "password=set",
            "a/# (QoS 1)",
            "Service running: false",
            "Background: OPTIMIZED",
        )) {
            assertTrue(s, report.contains(s))
        }
        assertFalse(report.contains("s3cret"))
        assertFalse(report.contains("alice"))
        assertTrue(Diagnostics.describe(BrokerConfig(host = "h")).contains("username=none, password=none"))
    }

    @Test
    fun displaysPathsUnderSdcard() {
        val storage = File("/storage/emulated/0")
        assertEquals(
            "/sdcard/Android/data/x/files/logs/a.log",
            Diagnostics.displayPath(File(storage, "Android/data/x/files/logs/a.log"), storage),
        )
        assertEquals("/data/user/0/x/files/logs/a.log", Diagnostics.displayPath(File("/data/user/0/x/files/logs/a.log"), storage))
        assertEquals("/other/a.log", Diagnostics.displayPath(File("/other/a.log"), File("relative-root")))
    }

    @Test
    fun logsLiveInAppSpecificStorage() {
        assertEquals(context.getExternalFilesDir("logs"), Diagnostics.logDirectory(context))
        val internal = File("/data/files")
        val noExternal =
            mockk<Context> {
                every { getExternalFilesDir(any()) } returns null
                every { filesDir } returns internal
            }
        assertEquals(File(internal, "logs"), Diagnostics.logDirectory(noExternal))
    }
}
