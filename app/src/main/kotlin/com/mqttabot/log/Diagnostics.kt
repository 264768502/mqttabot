package com.mqttabot.log

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.pm.PackageInfoCompat
import com.mqttabot.AppGraph
import com.mqttabot.mqtt.BrokerConfig
import com.mqttabot.service.MqttService
import com.mqttabot.ui.backgroundMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Environment and settings snapshot, so a log can be understood on its own. */
object Diagnostics {
    const val LOG_DIR = "logs"

    /** App-specific external storage (reachable over USB, no permission), else internal storage. */
    fun logDirectory(context: Context): File = context.getExternalFilesDir(LOG_DIR) ?: File(context.filesDir, LOG_DIR)

    /**
     * [file] as users can find it: under `/sdcard` (the shared-storage alias that file managers and
     * adb accept) when it is on shared storage, otherwise its absolute path.
     */
    @SuppressLint("SdCardPath") // Display only; the file is accessed by its real path.
    @Suppress("DEPRECATION") // Only used to shorten the displayed path.
    fun displayPath(
        file: File,
        sharedStorage: File = Environment.getExternalStorageDirectory(),
    ): String {
        // Both absolute, so they share the root and relativeTo cannot fail.
        val relative = file.absoluteFile.relativeTo(sharedStorage.absoluteFile).path
        return if (relative.startsWith("..")) file.absolutePath else "/sdcard/$relative"
    }

    @Suppress("DEPRECATION") // The flags overload needs API 33.
    fun appVersion(context: Context): String {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return "${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})"
    }

    /** Broker settings without the password. */
    fun describe(config: BrokerConfig): String =
        "${config.serverUri}, clientId=${config.clientId}, keepAlive=${config.keepAliveSeconds}s, " +
            "cleanSession=${config.cleanSession}, username=${if (config.username.isEmpty()) "none" else "set"}, " +
            "password=${if (config.password.isEmpty()) "none" else "set"}"

    fun report(context: Context): String {
        val settings = AppGraph.settings
        val subscriptions = settings.subscriptions()
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(AppLog.clock()))
        return buildString {
            appendLine("=== MqttABot diagnostic report, $time ===")
            appendLine("App: ${appVersion(context)}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Locale: ${Locale.getDefault().toLanguageTag()}, time zone: ${TimeZone.getDefault().id}")
            appendLine("Broker: ${describe(settings.loadBroker())}")
            appendLine("Subscriptions (${subscriptions.size}): ${subscriptions.joinToString { "${it.filter} (QoS ${it.qos})" }}")
            appendLine(
                "Start on boot: ${settings.autoStartOnBoot}, notify on message: ${settings.notifyOnMessage}, theme: ${AppGraph.themeMode.value}, dynamic color: ${AppGraph.dynamicColor}",
            )
            appendLine("Service running: ${MqttService.isRunning}, status: ${AppGraph.status.value}")
            appendLine(
                "Background: ${context.backgroundMode()}, notifications enabled: " +
                    NotificationManagerCompat.from(context).areNotificationsEnabled(),
            )
            appendLine("=== log ===")
        }
    }
}
