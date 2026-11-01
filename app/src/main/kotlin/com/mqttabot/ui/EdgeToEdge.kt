package com.mqttabot.ui

import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.mqttabot.R

/**
 * Sets [layout] edge to edge: the app bar handles the status bar, and the scrolling `content`
 * view is padded so it clears the navigation bar, display cutouts and the keyboard.
 */
fun AppCompatActivity.setEdgeToEdgeContentView(
    @LayoutRes layout: Int,
) {
    enableEdgeToEdge()
    setContentView(layout)
    val content = findViewById<View>(R.id.content)
    val bottom = content.paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
        val types = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime()
        val bars = insets.getInsets(types)
        view.updatePadding(left = bars.left, right = bars.right, bottom = bottom + bars.bottom)
        insets
    }
}
