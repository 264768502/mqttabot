package com.mqttabot.mqtt

import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Serial task queue with delayed scheduling. */
interface TaskRunner {
    fun execute(task: Runnable)

    fun schedule(
        delayMillis: Long,
        task: Runnable,
    ): Future<*>

    fun shutdown()
}

class ExecutorTaskRunner(
    // Late callbacks (e.g. a Paho reconnect racing stop()) are dropped after shutdown.
    private val executor: ScheduledExecutorService = ScheduledThreadPoolExecutor(1, ThreadPoolExecutor.DiscardPolicy()),
) : TaskRunner {
    override fun execute(task: Runnable) = executor.execute(task)

    override fun schedule(
        delayMillis: Long,
        task: Runnable,
    ): Future<*> = executor.schedule(task, delayMillis, TimeUnit.MILLISECONDS)

    override fun shutdown() = executor.shutdown()
}
