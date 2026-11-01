package com.mqttabot.ui.theme

import android.graphics.Color
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.mqttabot.AppGraph
import com.mqttabot.data.ThemeMode
import com.mqttabot.ui.observeAsState
import kotlinx.coroutines.delay

/**
 * Edge-to-edge Compose content in the app theme. System bar icons follow the app's light/dark
 * choice rather than the system's.
 */
fun ComponentActivity.setThemedContent(content: @Composable () -> Unit) {
    setContent {
        val dark = rememberDarkTheme()
        DisposableEffect(dark) {
            val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
            enableEdgeToEdge(style, style)
            onDispose {}
        }
        MqttABotTheme(dark, content)
    }
}

/** Whether to draw dark: the user's [ThemeMode], with AUTO following the time of day. */
@Composable
fun rememberDarkTheme(): Boolean {
    val mode by AppGraph.themeMode.observeAsState()
    if (mode == ThemeMode.AUTO) return rememberNight()
    return mode == ThemeMode.DARK
}

@Composable
private fun rememberNight(): Boolean {
    var now by remember { mutableLongStateOf(AppGraph.clock()) }
    // Re-evaluate at each 07:00 / 19:00 boundary while on screen.
    LaunchedEffect(Unit) {
        while (true) {
            delay(ThemeMode.millisUntilSwitch(now))
            now = AppGraph.clock()
        }
    }
    return ThemeMode.isNight(now)
}

/** Material 3 Expressive theme: wallpaper colors on Android 12+, brand colors otherwise. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MqttABotTheme(
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors =
        when {
            AppGraph.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            dark -> {
                BrandDark
            }

            else -> {
                BrandLight
            }
        }
    MaterialExpressiveTheme(colorScheme = colors, motionScheme = MotionScheme.expressive(), content = content)
}
