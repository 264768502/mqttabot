package com.mqttabot.screenshots

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mqttabot.AppGraph
import com.mqttabot.R
import com.mqttabot.data.Subscription
import com.mqttabot.data.ThemeMode
import com.mqttabot.mqtt.BrokerConfig
import com.mqttabot.mqtt.ReceivedMessage
import com.mqttabot.tasker.EventConfigActivity
import com.mqttabot.tasker.TAG_PATTERN
import com.mqttabot.tasker.TaskerApi
import com.mqttabot.testutil.FakeGateway
import com.mqttabot.testutil.ManualTaskRunner
import com.mqttabot.ui.BrokerSettingsActivity
import com.mqttabot.ui.MainActivity
import com.mqttabot.ui.TAG_FILTER
import com.mqttabot.ui.TAG_MAIN_LIST
import com.mqttabot.ui.TAG_PAYLOAD
import com.mqttabot.ui.TAG_QOS
import com.mqttabot.ui.TAG_TOPIC
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Calendar

/**
 * Renders store/README screenshots with Robolectric's native graphics. Runs only with
 * `./gradlew :app:testDebugUnitTest -Pscreenshots`; writes into [dir] under the repository root.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenshotTest(
    private val dir: String,
    private val theme: ThemeMode = ThemeMode.LIGHT,
) {
    @get:Rule val compose = createEmptyComposeRule()

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
        // Brand colors instead of Robolectric's default wallpaper palette.
        AppGraph.dynamicColor = false
        AppGraph.themeMode.set(theme)
        // A connected service, so status and the start/stop button agree.
        AppGraph.gatewayFactory = { FakeGateway() }
        AppGraph.taskRunnerFactory = { ManualTaskRunner() }
        AppGraph.controller = AppGraph.newController {}.also { it.start() }
        val base = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 1, 9, 41, 7) }.timeInMillis
        AppGraph.messageLog.add(ReceivedMessage("home/door/front", "open", 2, false, base))
        AppGraph.messageLog.add(ReceivedMessage("home/kitchen/temperature", "21.5", 1, false, base + 63_000))
        AppGraph.messageLog.add(ReceivedMessage("home/livingroom/temperature", "22.8", 1, true, base + 125_000))
    }

    private fun <A : Activity> ActivityScenario<A>.capture(name: String) {
        onActivity { activity ->
            // No text cursor in store screenshots.
            activity.window.decorView
                .findFocus()
                ?.clearFocus()
        }
        compose.waitForIdle()
        onActivity { activity ->
            val view = activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            write(bitmap, "$dir/$name.png")
        }
        close()
    }

    @Test
    fun main() {
        val s = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithTag(TAG_TOPIC).performTextReplacement("home/livingroom/light")
        compose.onNodeWithTag(TAG_PAYLOAD).performTextReplacement("ON")
        compose.onNodeWithTag("$TAG_QOS-1").performClick()
        s.capture("1_main")
    }

    /** Scrolled to the bottom by touch, so the top app bar collapses too. */
    @Test
    fun messages() {
        val s = ActivityScenario.launch(MainActivity::class.java)
        repeat(6) { compose.onNodeWithTag(TAG_MAIN_LIST).performTouchInput { swipeUp() } }
        s.capture("2_messages")
    }

    @Test
    fun brokerSettings() {
        ActivityScenario.launch(BrokerSettingsActivity::class.java).capture("3_broker_settings")
    }

    @Test
    fun taskerEvent() {
        val intent = Intent(TaskerApi.ACTION_EDIT_EVENT).setClass(app, EventConfigActivity::class.java)
        val s = ActivityScenario.launch<EventConfigActivity>(intent)
        compose.onNodeWithTag(TAG_FILTER).performTextReplacement("home/+/temperature")
        compose.onNodeWithTag(TAG_PATTERN).performTextReplacement("^\\d+(\\.\\d+)?$")
        s.capture("4_tasker_event")
    }

    protected fun write(
        bitmap: Bitmap,
        path: String,
    ) {
        val root = File(checkNotNull(System.getProperty("screenshot.root")) { "Run with -Pscreenshots" })
        val file = File(root, path)
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS-w400dp-h880dp-xhdpi")
class EnglishScreenshotTest : ScreenshotTest("$STORE/en-US/images/phoneScreenshots") {
    /** 512x512 store icon rendered from the adaptive launcher icon. */
    @Test
    fun icon() {
        val drawable =
            checkNotNull(ApplicationProvider.getApplicationContext<android.app.Application>().getDrawable(R.drawable.ic_launcher))
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, 512, 512)
        drawable.draw(Canvas(bitmap))
        write(bitmap, "$STORE/en-US/images/icon.png")
    }
}

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "zh-rCN-w400dp-h880dp-xhdpi")
class ChineseScreenshotTest : ScreenshotTest("$STORE/zh-CN/images/phoneScreenshots")

/** Dark theme, for the README only (not a store locale). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS-w400dp-h880dp-xhdpi")
class DarkScreenshotTest : ScreenshotTest("docs/screenshots/dark", ThemeMode.DARK)

private const val STORE = "fastlane/metadata/android"
