package com.mqttabot

import android.app.Application
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions

class MqttABotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        // Material You: take colors from the wallpaper on Android 12+, brand colors otherwise.
        val options = DynamicColorsOptions.Builder().setPrecondition { _, _ -> AppGraph.dynamicColor }.build()
        DynamicColors.applyToActivitiesIfAvailable(this, options)
    }
}
