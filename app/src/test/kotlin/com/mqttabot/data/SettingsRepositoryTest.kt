package com.mqttabot.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mqttabot.mqtt.BrokerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)

    @Test
    fun defaultsGenerateAndPersistClientId() {
        var n = 0
        val repo = SettingsRepository(context) { "id-${++n}" }
        val config = repo.loadBroker()
        assertEquals(BrokerConfig(clientId = "id-1"), config)
        assertEquals("id-1", repo.loadBroker().clientId)
        assertEquals(1, n)
    }

    @Test
    fun defaultIdGenerator() {
        assertTrue(SettingsRepository(context).loadBroker().clientId.startsWith("mqttabot-"))
    }

    @Test
    fun saveAndLoadBroker() {
        val repo = SettingsRepository(context)
        val config = BrokerConfig("h", 8883, true, "u", "p", "cid", 30, false)
        repo.saveBroker(config)
        assertEquals(config, SettingsRepository(context).loadBroker())
    }

    @Test
    fun blankClientIdIsRegenerated() {
        val repo = SettingsRepository(context) { "gen" }
        repo.saveBroker(BrokerConfig(clientId = " "))
        assertEquals("gen", repo.loadBroker().clientId)
    }

    @Test
    fun autoStartFlag() {
        val repo = SettingsRepository(context)
        assertFalse(repo.autoStartOnBoot)
        repo.autoStartOnBoot = true
        assertTrue(repo.autoStartOnBoot)
    }

    @Test
    fun notifyOnMessageFlag() {
        val repo = SettingsRepository(context)
        assertFalse(repo.notifyOnMessage)
        repo.notifyOnMessage = true
        assertTrue(SettingsRepository(context).notifyOnMessage)
    }

    @Test
    fun diagnosticLoggingFlag() {
        val repo = SettingsRepository(context)
        assertFalse(repo.diagnosticLogging)
        repo.diagnosticLogging = true
        assertTrue(SettingsRepository(context).diagnosticLogging)
    }

    @Test
    fun themeModeDefaultsToAutoAndPersists() {
        val repo = SettingsRepository(context)
        assertEquals(ThemeMode.AUTO, repo.themeMode)
        repo.themeMode = ThemeMode.DARK
        assertEquals(ThemeMode.DARK, SettingsRepository(context).themeMode)
        context
            .getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("theme_mode", "SEPIA")
            .commit()
        assertEquals(ThemeMode.AUTO, repo.themeMode)
    }

    @Test
    fun languageDefaultsToAutoAndPersists() {
        val repo = SettingsRepository(context)
        assertEquals(AppLanguage.AUTO, repo.language)
        repo.language = "de"
        assertEquals("de", SettingsRepository(context).language)
        prefs.edit().putString("language", "fr").commit()
        assertEquals(AppLanguage.AUTO, repo.language)
    }

    @Test
    fun subscriptionsAddUpdateRemove() {
        val repo = SettingsRepository(context)
        assertEquals(emptyList<Subscription>(), repo.subscriptions())
        repo.addSubscription(Subscription("a", 0))
        repo.addSubscription(Subscription("b", 1))
        repo.addSubscription(Subscription("a", 2))
        assertEquals(listOf(Subscription("b", 1), Subscription("a", 2)), repo.subscriptions())
        repo.removeSubscription("b")
        assertEquals(listOf(Subscription("a", 2)), repo.subscriptions())
    }

    @Test
    fun corruptSubscriptionsAreIgnored() {
        prefs.edit().putString(SettingsRepository.KEY_SUBSCRIPTIONS, "{not json").apply()
        assertEquals(emptyList<Subscription>(), SettingsRepository(context).subscriptions())
    }
}
