package io.github.qlaus.calnotes.diagnostics

import android.content.Context
import android.os.Build
import io.github.qlaus.calnotes.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

/**
 * Saves uncaught exceptions to a local file so they can be shown on the next launch.
 * Needed because the test device has no PC/logcat access. Only stack traces are stored —
 * never tokens or note content.
 */
object CrashReporter {
    private const val FILE_NAME = "last_crash.txt"
    private const val NON_FATAL_FILE = "last_error.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(appContext, FILE_NAME, error, "thread=${thread.name}") }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Records an exception that was caught (the app kept running). */
    fun recordNonFatal(context: Context, error: Throwable) {
        runCatching { write(context.applicationContext, NON_FATAL_FILE, error, "non-fatal") }
    }

    fun readLastCrash(context: Context): String? = file(context, FILE_NAME).takeIf { it.exists() }?.readText()

    fun clearLastCrash(context: Context) {
        file(context, FILE_NAME).delete()
    }

    private fun write(context: Context, name: String, error: Throwable, extra: String) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val header = buildString {
            appendLine("time=${Instant.now()}")
            appendLine("app=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL} sdk=${Build.VERSION.SDK_INT}")
            appendLine(extra)
        }
        // Keep it short enough to paste into a chat.
        file(context, name).writeText(header + trace.take(12_000))
    }

    private fun file(context: Context, name: String) = File(context.filesDir, name)
}
