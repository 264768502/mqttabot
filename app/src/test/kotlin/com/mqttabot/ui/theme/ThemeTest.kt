package com.mqttabot.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mqttabot.AppGraph
import com.mqttabot.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
class ThemeTest {
    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val utc = TimeZone.getTimeZone("UTC")

    private fun at(
        hour: Int,
        minute: Int = 0,
        zone: TimeZone = utc,
    ): Long =
        Calendar
            .getInstance(zone)
            .apply {
                clear()
                set(2026, Calendar.NOVEMBER, 1, hour, minute)
            }.timeInMillis

    @Test
    fun nightIsSevenPmToSevenAm() {
        assertTrue(ThemeMode.isNight(at(0), utc))
        assertTrue(ThemeMode.isNight(at(6, 59), utc))
        assertFalse(ThemeMode.isNight(at(7), utc))
        assertFalse(ThemeMode.isNight(at(18, 59), utc))
        assertTrue(ThemeMode.isNight(at(19), utc))
        assertTrue(ThemeMode.isNight(at(23, 59), utc))
        // Default zone overload.
        assertFalse(ThemeMode.isNight(at(12, zone = TimeZone.getDefault())))
    }

    @Test
    fun nextSwitchIsTheNextBoundary() {
        val hour = 3_600_000L
        assertEquals(7 * hour, ThemeMode.millisUntilSwitch(at(0), utc))
        assertEquals(12 * hour, ThemeMode.millisUntilSwitch(at(7), utc))
        assertEquals(30 * 60_000L, ThemeMode.millisUntilSwitch(at(18, 30), utc))
        assertEquals(12 * hour, ThemeMode.millisUntilSwitch(at(19), utc))
        // After 19:00 the next switch is tomorrow morning.
        assertEquals(8 * hour, ThemeMode.millisUntilSwitch(at(23), utc))
        assertTrue(ThemeMode.millisUntilSwitch(System.currentTimeMillis()) > 0)
    }

    private fun showDark() =
        compose.setContent {
            Text(if (rememberDarkTheme()) "dark" else "light")
        }

    @Test
    fun forcedModesIgnoreTheClock() {
        AppGraph.clock = { at(12, zone = TimeZone.getDefault()) }
        AppGraph.themeMode.set(ThemeMode.DARK)
        showDark()
        compose.onNodeWithText("dark").assertExists()
        AppGraph.themeMode.set(ThemeMode.LIGHT)
        compose.onNodeWithText("light").assertExists()
    }

    @Test
    fun autoFollowsTheTimeOfDayAndSwitchesLive() {
        val zone = TimeZone.getDefault()
        var now = at(18, 59, zone)
        AppGraph.clock = { now }
        AppGraph.themeMode.set(ThemeMode.AUTO)
        compose.mainClock.autoAdvance = false
        showDark()
        compose.onNodeWithText("light").assertExists()
        now = at(19, 0, zone)
        compose.mainClock.advanceTimeBy(61_000)
        compose.onNodeWithText("dark").assertExists()
    }

    private fun primary(dark: Boolean): Color {
        var color = Color.Unspecified
        compose.setContent { MqttABotTheme(dark) { color = MaterialTheme.colorScheme.primary } }
        compose.waitForIdle()
        return color
    }

    @Test
    fun brandColorsWithoutDynamicColor() {
        AppGraph.dynamicColor = false
        assertEquals(BrandLight.primary, primary(dark = false))
    }

    @Test
    fun brandDarkColorsWithoutDynamicColor() {
        AppGraph.dynamicColor = false
        assertEquals(BrandDark.primary, primary(dark = true))
    }

    @Test
    fun wallpaperColorsOnAndroid12() {
        assertEquals(dynamicLightColorScheme(context).primary, primary(dark = false))
    }

    @Test
    fun wallpaperDarkColorsOnAndroid12() {
        assertEquals(dynamicDarkColorScheme(context).primary, primary(dark = true))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun brandColorsBeforeAndroid12() {
        assertEquals(BrandLight.primary, primary(dark = false))
    }
}
