package com.mqttbot.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.mqttbot.AppGraph
import com.mqttbot.mqtt.ConnectionController
import com.mqttbot.mqtt.ConnectionStatus
import com.mqttbot.mqtt.ReceivedMessage
import com.mqttbot.tasker.TaskerEventDispatcher

/** Foreground service holding the long-lived MQTT subscription connection. */
class MqttService : Service() {
    private var controller: ConnectionController? = null
    private lateinit var notifications: ServiceNotifications
    private val statusListener: (ConnectionStatus) -> Unit = { notifications.update(it) }

    override fun onCreate() {
        super.onCreate()
        notifications = ServiceNotifications(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            AppGraph.foregroundStarter(this, ServiceNotifications.NOTIFICATION_ID, notifications.build(AppGraph.status.value))
        } catch (e: RuntimeException) {
            // E.g. ForegroundServiceStartNotAllowedException when restarted from the background.
            Log.w(TAG, "Cannot enter foreground", e)
            stopSelf()
            return START_NOT_STICKY
        }
        if (controller == null) {
            val c = AppGraph.newController(::onMessage)
            controller = c
            AppGraph.controller = c
            AppGraph.status.observe(statusListener)
            c.start()
        }
        return START_STICKY
    }

    private fun onMessage(message: ReceivedMessage) {
        AppGraph.messageLog.add(message)
        TaskerEventDispatcher.dispatch(this, message)
    }

    override fun onDestroy() {
        AppGraph.status.remove(statusListener)
        controller?.let {
            it.stop()
            if (AppGraph.controller === it) AppGraph.controller = null
        }
        controller = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MqttService"
        const val ACTION_START = "com.mqttbot.action.START"
        const val ACTION_STOP = "com.mqttbot.action.STOP"

        val isRunning: Boolean
            get() = AppGraph.controller != null

        fun intent(context: Context, action: String): Intent =
            Intent(context, MqttService::class.java).setAction(action)

        fun start(context: Context) = ContextCompat.startForegroundService(context, intent(context, ACTION_START))

        fun stop(context: Context) {
            context.stopService(Intent(context, MqttService::class.java))
        }
    }
}
