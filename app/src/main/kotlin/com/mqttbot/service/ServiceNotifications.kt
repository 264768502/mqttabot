package com.mqttbot.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mqttbot.R
import com.mqttbot.mqtt.ConnectionStatus
import com.mqttbot.ui.MainActivity
import com.mqttbot.ui.describe

/** Builds the ongoing notification that keeps [MqttService] in the foreground. */
class ServiceNotifications(private val context: Context) {
    init {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.notification_channel_name))
                .setShowBadge(false)
                .build(),
        )
    }

    fun build(status: ConnectionStatus): Notification {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            context, 1, MqttService.intent(context, MqttService.ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mqtt)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(status.describe(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.action_stop), stop)
            .build()
    }

    fun update(status: ConnectionStatus) {
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build(status))
    }

    companion object {
        const val CHANNEL_ID = "mqtt_service"
        const val NOTIFICATION_ID = 1
    }
}
