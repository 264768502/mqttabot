package com.mqttabot.log

import android.util.Log

/**
 * Diagnostic log. Always goes to logcat; also to [file] while the user has diagnostic logging
 * turned on. Never log secrets (passwords); keep payloads to a [preview].
 */
object AppLog {
    /** Destination on disk, or null while logging to disk is off. */
    @Volatile var file: LogFile? = null

    @Volatile var clock: () -> Long = System::currentTimeMillis

    fun d(
        tag: String,
        message: String,
    ) = log(Log.DEBUG, tag, message, null)

    fun i(
        tag: String,
        message: String,
    ) = log(Log.INFO, tag, message, null)

    fun w(
        tag: String,
        message: String,
        error: Throwable? = null,
    ) = log(Log.WARN, tag, message, error)

    fun e(
        tag: String,
        message: String,
        error: Throwable? = null,
    ) = log(Log.ERROR, tag, message, error)

    private fun log(
        priority: Int,
        tag: String,
        message: String,
        error: Throwable?,
    ) {
        Log.println(priority, tag, if (error == null) message else "$message\n${Log.getStackTraceString(error)}")
        file?.append(LogFile.format(clock(), priority, tag, Thread.currentThread().name, message, error))
    }

    /** Payload excerpt for the log: at most [max] characters, on one line. */
    fun preview(
        text: String,
        max: Int = 64,
    ): String {
        val oneLine = text.replace("\n", "\\n")
        return if (oneLine.length <= max) "\"$oneLine\"" else "\"${oneLine.take(max)}…\" (${text.length} chars)"
    }

    /** Wraps the default uncaught-exception handler with [CrashHandler], once. */
    fun installCrashHandler() {
        val current = Thread.getDefaultUncaughtExceptionHandler()
        if (current !is CrashHandler) Thread.setDefaultUncaughtExceptionHandler(CrashHandler(current))
    }
}

/**
 * Logs uncaught exceptions, waiting for the write so the line survives the crash, then passes them
 * on to [previous], which normally ends the process.
 */
class CrashHandler(
    private val previous: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(
        thread: Thread,
        error: Throwable,
    ) {
        AppLog.e("Crash", "Uncaught exception in thread ${thread.name}", error)
        AppLog.file?.afterPendingWrites {}
        previous?.uncaughtException(thread, error)
    }
}
