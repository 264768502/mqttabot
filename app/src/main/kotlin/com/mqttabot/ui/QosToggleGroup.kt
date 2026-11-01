package com.mqttabot.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.widget.TooltipCompat
import androidx.core.os.BundleCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.mqttabot.R

/** Connected button group that picks an MQTT QoS level (0–2). */
class QosToggleGroup(
    context: Context,
    attrs: AttributeSet?,
) : MaterialButtonToggleGroup(context, attrs) {
    /** Selected level; values outside 0–2 are clamped. */
    var qos: Int
        get() = indexOfChild(findViewById(checkedButtonId))
        set(value) = check(getChildAt(value.coerceIn(0, 2)).id)

    // Labels are protocol digits; the localized meaning goes into the description and tooltip.
    @SuppressLint("SetTextI18n")
    private fun addLevel(
        level: Int,
        description: String,
    ) {
        val button = LayoutInflater.from(context).inflate(R.layout.qos_button, this, false) as MaterialButton
        button.id = View.generateViewId()
        button.text = level.toString()
        button.contentDescription = description
        // Generated ids change across recreation, so the group saves the selection itself.
        button.isSaveEnabled = false
        TooltipCompat.setTooltipText(button, description)
        addView(button)
    }

    init {
        isSingleSelection = true
        isSelectionRequired = true
        resources.getStringArray(R.array.qos_levels).forEachIndexed(::addLevel)
        qos = 0
    }

    override fun onSaveInstanceState(): Parcelable =
        Bundle().apply {
            putParcelable(KEY_SUPER, super.onSaveInstanceState())
            putInt(KEY_QOS, qos)
        }

    override fun onRestoreInstanceState(state: Parcelable?) {
        val bundle = state as Bundle
        super.onRestoreInstanceState(BundleCompat.getParcelable(bundle, KEY_SUPER, Parcelable::class.java))
        qos = bundle.getInt(KEY_QOS)
    }

    private companion object {
        const val KEY_SUPER = "super"
        const val KEY_QOS = "qos"
    }
}
