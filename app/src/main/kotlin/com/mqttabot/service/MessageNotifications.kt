package com.mqttabot.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mqttabot.R
import com.mqttabot.mqtt.ReceivedMessage
import com.mqttabot.ui.MainActivity

/** Posts a notification per received message; one per topic, replaced by its latest message. */
class MessageNotifications(
    private val context: Context,
) {
    init {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat
                .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.message_channel_name))
                .build(),
        )
    }

    fun show(message: ReceivedMessage) {
        val open =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val text = message.payload.take(MAX_TEXT)
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_mqtt)
                .setContentTitle(message.topic)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setWhen(message.timestamp)
                .setShowWhen(true)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        context.getSystemService(NotificationManager::class.java).notify(message.topic, NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "mqtt_messages"
        const val NOTIFICATION_ID = 2

        // Notifications show only a few lines; long payloads stay in the app's message list.
        private const val MAX_TEXT = 1000
    }
}
