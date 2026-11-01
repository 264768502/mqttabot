package com.mqttabot

import android.annotation.SuppressLint
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.ServiceCompat
import com.mqttabot.data.MessageLog
import com.mqttabot.data.SettingsRepository
import com.mqttabot.data.ThemeMode
import com.mqttabot.mqtt.ConnectionController
import com.mqttabot.mqtt.ConnectionStatus
import com.mqttabot.mqtt.ExecutorTaskRunner
import com.mqttabot.mqtt.MqttGateway
import com.mqttabot.mqtt.MqttPublisher
import com.mqttabot.mqtt.Observable
import com.mqttabot.mqtt.PahoMqttGateway
import com.mqttabot.mqtt.ReceivedMessage
import com.mqttabot.mqtt.TaskRunner
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

    /** Current appearance; persisted by [setThemeMode]. */
    val themeMode = Observable(ThemeMode.AUTO)

    @Volatile var gatewayFactory: () -> MqttGateway = defaultGatewayFactory

    @Volatile var taskRunnerFactory: () -> TaskRunner = defaultTaskRunnerFactory

    @Volatile var ioExecutor: Executor = defaultIoExecutor

    @Volatile var foregroundStarter: (Service, Int, Notification) -> Unit = ::startForegroundSpecialUse

    /** Use wallpaper colors where available; screenshots turn this off to show the brand colors. */
    @Volatile var dynamicColor = true

    /** Wall-clock time for the automatic theme. */
    @Volatile var clock: () -> Long = System::currentTimeMillis

    /** Live connection owned by the running MqttService, if any. */
    @Volatile var controller: ConnectionController? = null

    fun init(context: Context) {
        settings = SettingsRepository(context.applicationContext)
        gatewayFactory = defaultGatewayFactory
        taskRunnerFactory = defaultTaskRunnerFactory
        ioExecutor = defaultIoExecutor
        foregroundStarter = ::startForegroundSpecialUse
        dynamicColor = true
        clock = System::currentTimeMillis
        themeMode.clearListeners()
        themeMode.set(settings.themeMode)
        controller = null
        status.clearListeners()
        status.set(ConnectionStatus.DISCONNECTED)
        messageLog.updates.clearListeners()
        messageLog.clear()
    }

    fun setThemeMode(mode: ThemeMode) {
        settings.themeMode = mode
        themeMode.set(mode)
    }

    fun publisher() = MqttPublisher({ settings.loadBroker() }, gatewayFactory, { controller?.activeGateway })

    fun newController(onMessage: (ReceivedMessage) -> Unit) =
        ConnectionController(
            configProvider = { settings.loadBroker() },
            subscriptionsProvider = { settings.subscriptions() },
            gatewayFactory = gatewayFactory,
            runner = taskRunnerFactory(),
            status = status,
            onMessage = onMessage,
        )

    // ServiceCompat drops the type on API levels that don't know it.
    @SuppressLint("InlinedApi")
    private fun startForegroundSpecialUse(
        service: Service,
        id: Int,
        notification: Notification,
    ) = ServiceCompat.startForeground(service, id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
}
