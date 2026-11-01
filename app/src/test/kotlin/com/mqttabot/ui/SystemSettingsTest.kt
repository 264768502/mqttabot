package com.mqttabot.ui

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class SystemSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val packageUri = "package:${context.packageName}"

    @Test
    fun readsBackgroundMode() {
        assertEquals(BackgroundMode.OPTIMIZED, context.backgroundMode())
        shadowOf(context.getSystemService(PowerManager::class.java)).setIgnoringBatteryOptimizations(context.packageName, true)
        assertEquals(BackgroundMode.UNRESTRICTED, context.backgroundMode())
        // A user restriction wins over the battery exemption.
        shadowOf(context.getSystemService(ActivityManager::class.java)).setBackgroundRestricted(true)
        assertEquals(BackgroundMode.RESTRICTED, context.backgroundMode())
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.O])
    fun noBackgroundRestrictionBeforeAndroid9() {
        assertEquals(BackgroundMode.OPTIMIZED, context.backgroundMode())
    }

    @Test
    fun intentsForEachMode() {
        val optimized = context.unrestrictedBackgroundIntent(BackgroundMode.OPTIMIZED)
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, optimized.action)
        assertEquals(packageUri, optimized.dataString)
        for (mode in listOf(BackgroundMode.RESTRICTED, BackgroundMode.UNRESTRICTED)) {
            val details = context.unrestrictedBackgroundIntent(mode)
            assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, details.action)
            assertEquals(packageUri, details.dataString)
        }
    }

    @Test
    fun notificationSettings() {
        val intent = context.notificationSettingsIntent()
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(context.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.N])
    fun notificationSettingsBeforeAndroid8() {
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, context.notificationSettingsIntent().action)
    }
}
