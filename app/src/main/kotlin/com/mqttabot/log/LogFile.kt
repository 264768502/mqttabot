package com.mqttabot.log

import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.FutureTask

/**
 * Size-bounded log on disk: `mqttabot.log` plus up to [maxFiles] - 1 rotated files
 * (`mqttabot.1.log` is the newest of those). Writes run on [writer], in order.
 */
class LogFile(
    val dir: File,
    private val writer: Executor,
    private val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    private val maxFiles: Int = DEFAULT_MAX_FILES,
) {
    val current: File get() = File(dir, "$BASE_NAME.log")

    /** Upper bound of all files together. */
    val limitBytes: Long get() = maxFileBytes * maxFiles

    fun append(text: String) = writer.execute { write(text) }

    /** Runs [block] after all pending writes, on the writer thread, and returns its result. */
    fun <T> afterPendingWrites(block: () -> T): T {
        val task = FutureTask(block)
        writer.execute(task)
        return task.get()
    }

    /** Existing files, oldest first. */
    fun files(): List<File> = ((maxFiles - 1 downTo 1).map(::rotated) + current).filter(File::exists)

    fun sizeBytes(): Long = afterPendingWrites { files().sumOf(File::length) }

    /** [header] followed by every file, oldest first; at most [limitBytes] plus the header. */
    fun export(header: String): ByteArray =
        afterPendingWrites {
            val out = ByteArrayOutputStream()
            out.write(header.toByteArray())
            for (f in files()) out.write(f.readBytes())
            out.toByteArray()
        }

    fun clear() = afterPendingWrites { files().forEach(File::delete) }

    private fun write(text: String) {
        dir.mkdirs()
        current.appendText(text)
        if (current.length() > maxFileBytes) rotate()
    }

    private fun rotate() {
        rotated(maxFiles - 1).delete()
        for (i in maxFiles - 2 downTo 1) rotated(i).renameTo(rotated(i + 1))
        current.renameTo(rotated(1))
    }

    private fun rotated(index: Int) = File(dir, "$BASE_NAME.$index.log")

    companion object {
        const val BASE_NAME = "mqttabot"
        const val DEFAULT_MAX_FILE_BYTES = 500_000L
        const val DEFAULT_MAX_FILES = 4

        /** Longest message kept per line; longer ones are cut. */
        const val MAX_MESSAGE_CHARS = 4_000

        private val LEVELS = mapOf(2 to 'V', 3 to 'D', 4 to 'I', 5 to 'W', 6 to 'E', 7 to 'A')

        /** One log line (plus the stack trace of [error]), e.g. `2026-11-01 09:41:07.123 I/Tag [main]: text`. */
        fun format(
            millis: Long,
            priority: Int,
            tag: String,
            thread: String,
            message: String,
            error: Throwable?,
        ): String {
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(millis))
            val text = if (message.length > MAX_MESSAGE_CHARS) message.take(MAX_MESSAGE_CHARS) + "…" else message
            val line = "$time ${LEVELS.getValue(priority)}/$tag [$thread]: $text\n"
            return if (error == null) line else line + error.stackTraceToString()
        }
    }
}
