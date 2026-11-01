package com.mqttbot

import android.app.Application

class MqttBotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}
