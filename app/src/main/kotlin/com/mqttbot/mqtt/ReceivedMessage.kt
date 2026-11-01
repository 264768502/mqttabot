package com.mqttbot.mqtt

data class ReceivedMessage(
    val topic: String,
    val payload: String,
    val qos: Int,
    val retained: Boolean,
    val timestamp: Long,
)
