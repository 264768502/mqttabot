package com.mqttabot.screenshots

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.widget.EditText
import android.widget.Spinner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mqttabot.AppGraph
import com.mqttabot.R
import com.mqttabot.data.Subscription
import com.mqttabot.mqtt.BrokerConfig
import com.mqttabot.mqtt.ReceivedMessage
import com.mqttabot.tasker.EventConfigActivity
import com.mqttabot.tasker.TaskerApi
import com.mqttabot.testutil.FakeGateway
import com.mqttabot.testutil.ManualTaskRunner
import com.mqttabot.ui.BrokerSettingsActivity
import com.mqttabot.ui.MainActivity
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Calendar

/**
 * Renders README screenshots with Robolectric's native graphics. Runs only with
 * `./gradlew :app:testDebugUnitTest -Pscreenshots`; writes PNGs to docs/screenshots/.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenshotTest(
    private val suffix: String,
) {
    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before
    fun sampleData() {
        AppGraph.settings.saveBroker(
            BrokerConfig(
                host = "broker.hivemq.com",
                port = 8883,
                tls = true,
                username = "alice",
                password = "secret",
                clientId = "mqttabot-7f3a",
            ),
        )
        AppGraph.settings.autoStartOnBoot = true
        AppGraph.settings.addSubscription(Subscription("home/+/temperature", 1))
        AppGraph.settings.addSubscription(Subscription("home/door/#", 2))
        // A connected service, so status and the start/stop button agree.
        AppGraph.gatewayFactory = { FakeGateway() }
        AppGraph.taskRunnerFactory = { ManualTaskRunner() }
        AppGraph.controller = AppGraph.newController {}.also { it.start() }
        val base = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 1, 9, 41, 7) }.timeInMillis
        AppGraph.messageLog.add(ReceivedMessage("home/door/front", "open", 2, false, base))
        AppGraph.messageLog.add(ReceivedMessage("home/kitchen/temperature", "21.5", 1, false, base + 63_000))
        AppGraph.messageLog.add(ReceivedMessage("home/livingroom/temperature", "22.8", 1, true, base + 125_000))
    }

    @Test
    @Config(qualifiers = "+h1080dp")
    fun main() {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        a.findViewById<EditText>(R.id.topic_input).setText("home/livingroom/light")
        a.findViewById<EditText>(R.id.payload_input).setText("ON")
        a.findViewById<Spinner>(R.id.qos_spinner).setSelection(1)
        capture(a, "main")
    }

    @Test
    fun brokerSettings() {
        capture(Robolectric.buildActivity(BrokerSettingsActivity::class.java).setup().get(), "broker-settings")
    }

    @Test
    fun taskerEvent() {
        val intent = Intent(TaskerApi.ACTION_EDIT_EVENT).setClass(app, EventConfigActivity::class.java)
        val a = Robolectric.buildActivity(EventConfigActivity::class.java, intent).setup().get()
        a.findViewById<EditText>(R.id.filter_input).setText("home/+/temperature")
        a.findViewById<EditText>(R.id.pattern_input).setText("^\\d+(\\.\\d+)?$")
        capture(a, "tasker-event")
    }

    private fun capture(
        activity: Activity,
        name: String,
    ) {
        shadowOf(Looper.getMainLooper()).idle()
        val view = activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File(checkNotNull(System.getProperty("screenshot.dir")) { "Run with -Pscreenshots" })
        dir.mkdirs()
        File(dir, "$name$suffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS-w400dp-h880dp-xhdpi")
class EnglishScreenshotTest : ScreenshotTest("")

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "zh-rCN-w400dp-h880dp-xhdpi")
class ChineseScreenshotTest : ScreenshotTest("-zh")
