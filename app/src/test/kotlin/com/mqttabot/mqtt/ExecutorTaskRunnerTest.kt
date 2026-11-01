package com.mqttabot.mqtt

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ExecutorTaskRunnerTest {
    @Test
    fun executesAndSchedules() {
        val runner = ExecutorTaskRunner()
        val latch = CountDownLatch(2)
        runner.execute { latch.countDown() }
        runner.schedule(10) { latch.countDown() }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        val cancelled = runner.schedule(60_000) { }
        assertTrue(cancelled.cancel(false))
        runner.shutdown()
        // Tasks submitted after shutdown are silently discarded.
        runner.execute { throw AssertionError("must not run") }
    }
}
