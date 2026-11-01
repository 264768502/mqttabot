package com.mqttabot.data

import android.content.Context
import androidx.core.content.edit
import com.mqttabot.mqtt.BrokerConfig
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

/** Persists broker settings and subscriptions in private SharedPreferences. */
class SettingsRepository(
    context: Context,
    private val idGenerator: () -> String = { "mqttabot-" + UUID.randomUUID().toString().substring(0, 8) },
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadBroker(): BrokerConfig {
        var clientId = str(KEY_CLIENT_ID, "")
        if (clientId.isBlank()) {
            clientId = idGenerator()
            prefs.edit { putString(KEY_CLIENT_ID, clientId) }
        }
        return BrokerConfig(
            host = str(KEY_HOST, BrokerConfig.DEFAULT_HOST),
            port = prefs.getInt(KEY_PORT, BrokerConfig.DEFAULT_PORT),
            tls = prefs.getBoolean(KEY_TLS, false),
            username = str(KEY_USERNAME, ""),
            password = str(KEY_PASSWORD, ""),
            clientId = clientId,
            keepAliveSeconds = prefs.getInt(KEY_KEEP_ALIVE, BrokerConfig.DEFAULT_KEEP_ALIVE),
            cleanSession = prefs.getBoolean(KEY_CLEAN_SESSION, true),
        )
    }

    fun saveBroker(config: BrokerConfig) {
        prefs.edit {
            putString(KEY_HOST, config.host)
            putInt(KEY_PORT, config.port)
            putBoolean(KEY_TLS, config.tls)
            putString(KEY_USERNAME, config.username)
            putString(KEY_PASSWORD, config.password)
            putString(KEY_CLIENT_ID, config.clientId)
            putInt(KEY_KEEP_ALIVE, config.keepAliveSeconds)
            putBoolean(KEY_CLEAN_SESSION, config.cleanSession)
        }
    }

    var autoStartOnBoot: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START, false)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_START, value) }

    var diagnosticLogging: Boolean
        get() = prefs.getBoolean(KEY_DIAGNOSTIC_LOGGING, false)
        set(value) = prefs.edit { putBoolean(KEY_DIAGNOSTIC_LOGGING, value) }

    var themeMode: ThemeMode
        get() = ThemeMode.entries.firstOrNull { it.name == str(KEY_THEME, "") } ?: ThemeMode.AUTO
        set(value) = prefs.edit { putString(KEY_THEME, value.name) }

    fun subscriptions(): List<Subscription> =
        try {
            val array = JSONArray(str(KEY_SUBSCRIPTIONS, "[]"))
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                Subscription(o.getString(JSON_FILTER), o.getInt(JSON_QOS))
            }
        } catch (e: JSONException) {
            emptyList()
        }

    /** Adds or updates (by filter) a subscription. */
    @Synchronized
    fun addSubscription(subscription: Subscription) {
        saveSubscriptions(subscriptions().filter { it.filter != subscription.filter } + subscription)
    }

    @Synchronized
    fun removeSubscription(filter: String) {
        saveSubscriptions(subscriptions().filter { it.filter != filter })
    }

    private fun saveSubscriptions(list: List<Subscription>) {
        val array = JSONArray()
        for (s in list) array.put(JSONObject().put(JSON_FILTER, s.filter).put(JSON_QOS, s.qos))
        prefs.edit { putString(KEY_SUBSCRIPTIONS, array.toString()) }
    }

    private fun str(
        key: String,
        default: String,
    ): String = prefs.getString(key, null) ?: default

    companion object {
        const val PREFS_NAME = "mqttabot_settings"
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_TLS = "tls"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_CLIENT_ID = "client_id"
        const val KEY_KEEP_ALIVE = "keep_alive"
        const val KEY_CLEAN_SESSION = "clean_session"
        const val KEY_AUTO_START = "auto_start_on_boot"
        const val KEY_THEME = "theme_mode"
        const val KEY_DIAGNOSTIC_LOGGING = "diagnostic_logging"
        const val KEY_SUBSCRIPTIONS = "subscriptions"
        private const val JSON_FILTER = "filter"
        private const val JSON_QOS = "qos"
    }
}
