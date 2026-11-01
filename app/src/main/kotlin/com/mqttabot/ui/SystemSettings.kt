package com.mqttabot.ui

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.StringRes
import com.mqttabot.R

/** The app's "battery usage" / background behavior as set in system settings. */
enum class BackgroundMode(
    @param:StringRes val label: Int,
) {
    /** Exempt from battery optimization: the service may run freely. */
    UNRESTRICTED(R.string.background_unrestricted),

    /** Default: Android may defer or cut background work. */
    OPTIMIZED(R.string.background_optimized),

    /** The user restricted background activity (Android 9+). */
    RESTRICTED(R.string.background_restricted),
}

fun Context.backgroundMode(): BackgroundMode {
    val power = getSystemService(PowerManager::class.java)
    val activity = getSystemService(ActivityManager::class.java)
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && activity.isBackgroundRestricted -> BackgroundMode.RESTRICTED
        power.isIgnoringBatteryOptimizations(packageName) -> BackgroundMode.UNRESTRICTED
        else -> BackgroundMode.OPTIMIZED
    }
}

/**
 * Where the user can allow unrestricted background activity. When optimized, the system dialog
 * grants the exemption in one tap. A persistent MQTT subscription that triggers automations is an
 * accepted use of this dialog. "Restricted" can only be lifted on the app's settings page.
 */
@SuppressLint("BatteryLife")
fun Context.unrestrictedBackgroundIntent(mode: BackgroundMode): Intent =
    if (mode == BackgroundMode.OPTIMIZED) {
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri())
    } else {
        appDetailsIntent()
    }

fun Context.appDetailsIntent(): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri())

/** The app's notification settings (Android 8+), otherwise its settings page. */
fun Context.notificationSettingsIntent(): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    } else {
        appDetailsIntent()
    }

private fun Context.packageUri(): Uri = Uri.fromParts("package", packageName, null)
