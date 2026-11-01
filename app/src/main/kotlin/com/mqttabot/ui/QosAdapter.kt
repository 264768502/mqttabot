package com.mqttabot.ui

import android.content.Context
import android.widget.ArrayAdapter
import com.mqttabot.R

object QosAdapter {
    fun create(context: Context): ArrayAdapter<CharSequence> =
        ArrayAdapter.createFromResource(context, R.array.qos_levels, android.R.layout.simple_spinner_item).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
}
