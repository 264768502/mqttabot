package com.mqttabot.ui

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mqttabot.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class LocalizationTest {
    private val app = ApplicationProvider.getApplicationContext<Context>()

    private fun localized(tag: String): Context =
        app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })

    private fun send(tag: String) = localized(tag).getString(R.string.action_send)

    private fun allScript(
        text: String,
        vararg scripts: Character.UnicodeScript,
    ) = text.codePoints().filter { Character.isLetter(it) }.allMatch { Character.UnicodeScript.of(it) in scripts }

    @Test
    fun picksSupportedLanguageForRegionalVariants() {
        assertEquals("Send", send("en-US"))
        assertEquals("Send", send("en-GB"))
        assertEquals("发送", send("zh-CN"))
        assertEquals("发送", send("zh-SG"))
        assertEquals("发送", send("zh-Hans"))
        assertEquals("傳送", send("zh-TW"))
        assertEquals("傳送", send("zh-HK"))
        assertEquals("傳送", send("zh-MO"))
        assertEquals("傳送", send("zh-Hant"))
        assertEquals("Enviar", send("es-ES"))
        assertEquals("Enviar", send("es-MX"))
        assertEquals("Senden", send("de-DE"))
        assertEquals("Senden", send("de-AT"))
        assertTrue(allScript(send("ko-KR"), Character.UnicodeScript.HANGUL))
        assertTrue(
            allScript(send("ja-JP"), Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA),
        )
    }

    @Test
    fun unsupportedLanguagesFallBackToEnglish() {
        for (tag in listOf("fr-FR", "ru-RU", "pt-BR", "ar-EG", "it-IT")) assertEquals(tag, "Send", send(tag))
    }

    @Test
    fun translationsKeepFormatPlaceholders() {
        val placeholder = Regex("""%\d+\$[sd]""")
        val english = localized("en")
        for (tag in listOf("zh-CN", "zh-TW", "es", "de", "ko", "ja")) {
            val ctx = localized(tag)
            for (field in R.string::class.java.fields) {
                val id = field.getInt(null)
                val expected =
                    placeholder
                        .findAll(english.getString(id))
                        .map { it.value }
                        .sorted()
                        .toList()
                val actual =
                    placeholder
                        .findAll(ctx.getString(id))
                        .map { it.value }
                        .sorted()
                        .toList()
                assertEquals("$tag ${field.name}", expected, actual)
            }
            val qos = ctx.resources.getStringArray(R.array.qos_levels)
            assertEquals(tag, 3, qos.size)
        }
    }
}
