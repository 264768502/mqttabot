package com.mqttabot.mqtt

data class ReceivedMessage(
    val topic: String,
    val payload: String,
    val qos: Int,
    val retained: Boolean,
    val timestamp: Long,
)
