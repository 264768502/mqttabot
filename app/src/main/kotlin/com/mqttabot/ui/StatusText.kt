package com.mqttabot.ui

import android.content.Context
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.mqttabot.R
import com.mqttabot.mqtt.ConnectionState
import com.mqttabot.mqtt.ConnectionStatus
import com.google.android.material.R as M

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
    @param:AttrRes val container: Int,
    @param:AttrRes val onContainer: Int,
    /** Show the loading indicator instead of the icon. */
    val busy: Boolean = false,
    /** Show [ConnectionStatus.detail] (the last error) below the headline. */
    val showDetail: Boolean = false,
)

val ConnectionState.style: StatusStyle
    get() =
        when (this) {
            ConnectionState.DISCONNECTED -> {
                StatusStyle(
                    R.string.status_disconnected,
                    R.drawable.ic_cloud_off,
                    M.attr.colorSurfaceContainerHighest,
                    M.attr.colorOnSurfaceVariant,
                )
            }

            ConnectionState.CONNECTING -> {
                StatusStyle(
                    R.string.state_connecting,
                    R.drawable.ic_cloud_sync,
                    M.attr.colorSecondaryContainer,
                    M.attr.colorOnSecondaryContainer,
                    busy = true,
                )
            }

            ConnectionState.CONNECTED -> {
                StatusStyle(
                    R.string.state_connected,
                    R.drawable.ic_cloud_done,
                    M.attr.colorPrimaryContainer,
                    M.attr.colorOnPrimaryContainer,
                )
            }

            ConnectionState.WAITING_RETRY -> {
                StatusStyle(
                    R.string.state_reconnecting,
                    R.drawable.ic_sync_problem,
                    M.attr.colorErrorContainer,
                    M.attr.colorOnErrorContainer,
                    showDetail = true,
                )
            }
        }
