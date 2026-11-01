package com.mqttbot

import android.app.Notification
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.ServiceCompat
import com.mqttbot.data.MessageLog
import com.mqttbot.data.SettingsRepository
import com.mqttbot.mqtt.ConnectionController
import com.mqttbot.mqtt.ConnectionStatus
import com.mqttbot.mqtt.ExecutorTaskRunner
import com.mqttbot.mqtt.MqttGateway
import com.mqttbot.mqtt.MqttPublisher
import com.mqttbot.mqtt.Observable
import com.mqttbot.mqtt.PahoMqttGateway
import com.mqttbot.mqtt.ReceivedMessage
import com.mqttbot.mqtt.TaskRunner
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** Process-wide dependencies; fields are swappable for tests. */
object AppGraph {
    private val defaultIoExecutor: Executor = Executors.newCachedThreadPool()
    private val defaultGatewayFactory: () -> MqttGateway = { PahoMqttGateway() }
    private val defaultTaskRunnerFactory: () -> TaskRunner = { ExecutorTaskRunner() }

    lateinit var settings: SettingsRepository
        private set
    val status = Observable(ConnectionStatus.DISCONNECTED)
    val messageLog = MessageLog()

    @Volatile var gatewayFactory: () -> MqttGateway = defaultGatewayFactory

    @Volatile var taskRunnerFactory: () -> TaskRunner = defaultTaskRunnerFactory

    @Volatile var ioExecutor: Executor = defaultIoExecutor

    @Volatile var foregroundStarter: (Service, Int, Notification) -> Unit = ::startForegroundSpecialUse

    /** Live connection owned by the running MqttService, if any. */
    @Volatile var controller: ConnectionController? = null

    fun init(context: Context) {
        settings = SettingsRepository(context.applicationContext)
        gatewayFactory = defaultGatewayFactory
        taskRunnerFactory = defaultTaskRunnerFactory
        ioExecutor = defaultIoExecutor
        foregroundStarter = ::startForegroundSpecialUse
        controller = null
        status.clearListeners()
        status.set(ConnectionStatus.DISCONNECTED)
        messageLog.updates.clearListeners()
        messageLog.clear()
    }

    fun publisher() = MqttPublisher({ settings.loadBroker() }, gatewayFactory, { controller?.activeGateway })

    fun newController(onMessage: (ReceivedMessage) -> Unit) = ConnectionController(
        configProvider = { settings.loadBroker() },
        subscriptionsProvider = { settings.subscriptions() },
        gatewayFactory = gatewayFactory,
        runner = taskRunnerFactory(),
        status = status,
        onMessage = onMessage,
    )

    // ServiceCompat drops the type on API levels that don't know it.
    @SuppressLint("InlinedApi")
    private fun startForegroundSpecialUse(service: Service, id: Int, notification: Notification) =
        ServiceCompat.startForeground(service, id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
}
