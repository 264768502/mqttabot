package com.mqttabot.data

data class Subscription(
    val filter: String,
    val qos: Int,
)
