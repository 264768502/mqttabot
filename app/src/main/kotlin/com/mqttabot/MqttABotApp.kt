package com.mqttabot

import android.app.Application
import com.mqttabot.log.AppLog
import com.mqttabot.log.Diagnostics

class MqttABotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        AppLog.installCrashHandler()
        AppLog.i(TAG, "Process started: MqttABot ${Diagnostics.appVersion(this)}")
    }

    private companion object {
        const val TAG = "App"
    }
}
