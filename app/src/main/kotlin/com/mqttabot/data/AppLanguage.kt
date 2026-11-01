package com.mqttabot.data

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.icu.util.ULocale
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import com.mqttabot.AppGraph
import java.util.Locale

/**
 * In-app language. Android 13+ keeps it as the system per-app language; older versions store it
 * in [SettingsRepository] and apply it through [wrap].
 */
object AppLanguage {
    /** Follow the system language. */
    const val AUTO = ""

    /** Translated languages by tag, each named in its own language. */
    val choices =
        linkedMapOf(
            "en" to "English",
            "zh-Hans" to "简体中文",
            "zh-Hant" to "繁體中文",
            "de" to "Deutsch",
            "es" to "Español",
            "ja" to "日本語",
            "ko" to "한국어",
        )

    /** The chosen [choices] tag, or [AUTO]. */
    fun current(context: Context): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            match(localeManager(context).applicationLocales[0])
        } else {
            AppGraph.settings.language
        }

    /** Switches [activity] (and the app) to [tag]. */
    fun set(
        activity: Activity,
        tag: String,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The system recreates the activities.
            localeManager(activity).applicationLocales = LocaleList.forLanguageTags(tag)
        } else {
            AppGraph.settings.language = tag
            activity.recreate()
        }
    }

    /** Before Android 13, [base] in the chosen language; call from attachBaseContext. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = AppGraph.settings.language
        if (tag == AUTO) return base
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return base.createConfigurationContext(config)
    }

    // System settings may pick a regional variant such as zh-TW; match it by language and script.
    private fun match(locale: Locale?): String {
        locale ?: return AUTO
        val wanted = script(locale)
        return choices.keys.firstOrNull { script(Locale.forLanguageTag(it)) == wanted } ?: AUTO
    }

    private fun script(locale: Locale) = ULocale.addLikelySubtags(ULocale.forLocale(locale)).let { "${it.language}-${it.script}" }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun localeManager(context: Context) = context.getSystemService(LocaleManager::class.java)
}
