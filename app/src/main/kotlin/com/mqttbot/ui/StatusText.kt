package com.mqttbot.ui

import android.content.Context
import com.mqttbot.R
import com.mqttbot.mqtt.ConnectionState
import com.mqttbot.mqtt.ConnectionStatus

fun ConnectionStatus.describe(context: Context): String = when (state) {
    ConnectionState.DISCONNECTED -> context.getString(R.string.status_disconnected)
    ConnectionState.CONNECTING -> context.getString(R.string.status_connecting, detail)
    ConnectionState.CONNECTED -> context.getString(R.string.status_connected, detail)
    ConnectionState.WAITING_RETRY -> context.getString(R.string.status_waiting_retry, detail)
}
