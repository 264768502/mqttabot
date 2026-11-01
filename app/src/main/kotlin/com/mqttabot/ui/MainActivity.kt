package com.mqttabot.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mqttabot.log.AppLog
import com.mqttabot.service.MqttService
import com.mqttabot.ui.theme.setThemedContent

/**
 * Main screen host. Permissions and settings are asked for only when needed: notifications when
 * the service starts, unrestricted background activity when the user taps the card's button. If
 * the user declines, a dialog explains why the app needs it.
 */
class MainActivity : ComponentActivity() {
    private var background by mutableStateOf(BackgroundMode.OPTIMIZED)
    private var explanation by mutableStateOf<Explanation?>(null)

    // The service runs either way; without the permission its notification is hidden.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            AppLog.i(TAG, "Notification permission ${if (granted) "granted" else "denied"}; starting the service")
            MqttService.start(this)
            if (!granted) explanation = Explanation.NOTIFICATIONS
        }

    private val backgroundSettings =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            background = backgroundMode()
            AppLog.i(TAG, "Back from background settings: $background")
            if (background != BackgroundMode.UNRESTRICTED) explanation = Explanation.BACKGROUND
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.i(TAG, "Opened")
        setThemedContent {
            MainScreen(
                background = background,
                onOpenSettings = { startActivity(Intent(this, BrokerSettingsActivity::class.java)) },
                onOpenDiagnostics = { startActivity(Intent(this, DiagnosticsActivity::class.java)) },
                onToggleService = ::toggleService,
                onAllowBackground = ::allowBackground,
            )
            explanation?.let { shown ->
                ExplanationDialog(
                    shown,
                    onOpenSettings = {
                        explanation = null
                        when (shown) {
                            Explanation.NOTIFICATIONS -> startActivity(notificationSettingsIntent())
                            Explanation.BACKGROUND -> allowBackground()
                        }
                    },
                    onDismiss = { explanation = null },
                )
            }
        }
    }

    // Users may change battery usage in system settings at any time.
    override fun onResume() {
        super.onResume()
        background = backgroundMode()
        AppLog.d(TAG, "Resumed; background activity: $background")
    }

    private fun toggleService() {
        when {
            MqttService.isRunning -> {
                AppLog.i(TAG, "Stop service tapped")
                MqttService.stop(this)
            }

            NotificationManagerCompat.from(this).areNotificationsEnabled() -> {
                AppLog.i(TAG, "Start service tapped")
                MqttService.start(this)
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED -> {
                AppLog.i(TAG, "Start service tapped; asking for the notification permission")
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            // Granted (or not needed) but switched off in system settings.
            else -> {
                AppLog.i(TAG, "Start service tapped; notifications are off in settings")
                MqttService.start(this)
                explanation = Explanation.NOTIFICATIONS
            }
        }
    }

    private fun allowBackground() {
        AppLog.i(TAG, "Asking for unrestricted background activity (now $background)")
        try {
            backgroundSettings.launch(unrestrictedBackgroundIntent(background))
        } catch (e: ActivityNotFoundException) {
            AppLog.w(TAG, "No battery optimization dialog; opening app settings", e)
            // Some devices hide the one-tap dialog; the app's settings page always exists.
            backgroundSettings.launch(appDetailsIntent())
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
