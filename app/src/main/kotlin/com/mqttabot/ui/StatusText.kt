package com.mqttabot.ui

import android.content.Context
import com.mqttabot.R
import com.mqttabot.mqtt.ConnectionState
import com.mqttabot.mqtt.ConnectionStatus

fun ConnectionStatus.describe(context: Context): String =
    when (state) {
        ConnectionState.DISCONNECTED -> context.getString(R.string.status_disconnected)
        ConnectionState.CONNECTING -> context.getString(R.string.status_connecting, detail)
        ConnectionState.CONNECTED -> context.getString(R.string.status_connected, detail)
        ConnectionState.WAITING_RETRY -> context.getString(R.string.status_waiting_retry, detail)
    }
