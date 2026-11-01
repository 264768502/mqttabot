package com.mqttabot

import android.app.Application

class MqttABotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}
