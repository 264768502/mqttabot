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
            MqttService.start(this)
            if (!granted) explanation = Explanation.NOTIFICATIONS
        }

    private val backgroundSettings =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            background = backgroundMode()
            if (background != BackgroundMode.UNRESTRICTED) explanation = Explanation.BACKGROUND
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setThemedContent {
            MainScreen(
                background = background,
                onOpenSettings = { startActivity(Intent(this, BrokerSettingsActivity::class.java)) },
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
    }

    private fun toggleService() {
        when {
            MqttService.isRunning -> {
                MqttService.stop(this)
            }

            NotificationManagerCompat.from(this).areNotificationsEnabled() -> {
                MqttService.start(this)
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED -> {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            // Granted (or not needed) but switched off in system settings.
            else -> {
                MqttService.start(this)
                explanation = Explanation.NOTIFICATIONS
            }
        }
    }

    private fun allowBackground() {
        try {
            backgroundSettings.launch(unrestrictedBackgroundIntent(background))
        } catch (e: ActivityNotFoundException) {
            // Some devices hide the one-tap dialog; the app's settings page always exists.
            backgroundSettings.launch(appDetailsIntent())
        }
    }
}
