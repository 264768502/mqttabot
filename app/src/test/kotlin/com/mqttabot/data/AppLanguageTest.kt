package com.mqttabot.data

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mqttabot.AppGraph
import com.mqttabot.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class AppLanguageTest {
    private val app = ApplicationProvider.getApplicationContext<Context>()

    private fun localized(tag: String): Context =
        app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })

    private fun systemLocales() = app.getSystemService(LocaleManager::class.java)

    @Test
    fun everyChoiceIsTranslated() {
        val english = localized("en").getString(R.string.language_title)
        for (tag in AppLanguage.choices.keys - "en") assertNotEquals(tag, english, localized(tag).getString(R.string.language_title))
    }

    @Test
    fun usesSystemPerAppLanguage() {
        assertEquals(AppLanguage.AUTO, AppLanguage.current(app))
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        AppLanguage.set(activity, "zh-Hant")
        assertEquals(LocaleList.forLanguageTags("zh-Hant"), systemLocales().applicationLocales)
        assertEquals("zh-Hant", AppLanguage.current(app))
        // Not stored in the app's settings, and contexts are left to the system.
        assertEquals(AppLanguage.AUTO, AppGraph.settings.language)
        assertSame(app, AppLanguage.wrap(app))

        AppLanguage.set(activity, AppLanguage.AUTO)
        assertTrue(systemLocales().applicationLocales.isEmpty)
        assertEquals(AppLanguage.AUTO, AppLanguage.current(app))
    }

    @Test
    fun matchesRegionalVariantsChosenInSystemSettings() {
        for ((system, expected) in listOf("zh-TW" to "zh-Hant", "zh-CN" to "zh-Hans", "en-GB" to "en", "ko-KR" to "ko", "fr-FR" to "")) {
            systemLocales().applicationLocales = LocaleList.forLanguageTags(system)
            assertEquals(system, expected, AppLanguage.current(app))
        }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S_V2])
    fun storesAndAppliesLanguageBeforeAndroid13() {
        assertSame(app, AppLanguage.wrap(app))
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val before = controller.get()
        AppLanguage.set(before, "de")
        assertEquals("de", AppGraph.settings.language)
        assertEquals("de", AppLanguage.current(app))
        shadowOf(Looper.getMainLooper()).idle()
        assertNotSame(before, controller.get())
        assertEquals(localized("de").getString(R.string.action_send), AppLanguage.wrap(app).getString(R.string.action_send))
    }
}
