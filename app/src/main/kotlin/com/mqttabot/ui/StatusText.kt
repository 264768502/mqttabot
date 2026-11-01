package com.mqttabot.ui

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
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

/** How the main screen presents a connection state. */
class StatusStyle(
    @param:StringRes val headline: Int,
    @param:DrawableRes val icon: Int,
    /** Show the loading indicator instead of the icon. */
    val busy: Boolean = false,
    /** Show [ConnectionStatus.detail] (the last error) below the headline. */
    val showDetail: Boolean = false,
)

val ConnectionState.style: StatusStyle
    get() =
        when (this) {
            ConnectionState.DISCONNECTED -> StatusStyle(R.string.status_disconnected, R.drawable.ic_cloud_off)
            ConnectionState.CONNECTING -> StatusStyle(R.string.state_connecting, R.drawable.ic_cloud_sync, busy = true)
            ConnectionState.CONNECTED -> StatusStyle(R.string.state_connected, R.drawable.ic_cloud_done)
            ConnectionState.WAITING_RETRY -> StatusStyle(R.string.state_reconnecting, R.drawable.ic_sync_problem, showDetail = true)
        }

/** Container and content color of the status icon. */
@Composable
@ReadOnlyComposable
fun ConnectionState.colors(): Pair<Color, Color> =
    with(MaterialTheme.colorScheme) {
        when (this@colors) {
            ConnectionState.DISCONNECTED -> surfaceContainerHighest to onSurfaceVariant
            ConnectionState.CONNECTING -> secondaryContainer to onSecondaryContainer
            ConnectionState.CONNECTED -> primaryContainer to onPrimaryContainer
            ConnectionState.WAITING_RETRY -> errorContainer to onErrorContainer
        }
    }
