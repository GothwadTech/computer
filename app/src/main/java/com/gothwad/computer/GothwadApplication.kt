package com.gothwad.computer

import android.app.Application
import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Custom Application class for Gothwad Computer PC Emulator.
 */
class GothwadApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(com.gothwad.computer.data.DpiHelper.wrapContext(base))
    }

    override fun onCreate() {
        super.onCreate()
        if (com.gothwad.computer.data.DpiHelper.isCustomDpiEnabled(this)) {
            com.gothwad.computer.data.DpiHelper.applyToResources(
                resources,
                com.gothwad.computer.data.DpiHelper.getCustomDpiValue(this)
            )
        }
        val currentProc = com.gothwad.computer.data.ProcessHelper.currentProcessName()
        Log.i(TAG, "GothwadApplication initialized in process: $currentProc")

        // Only initialize main process handlers from the main process
        if (currentProc.isEmpty() || currentProc == packageName) {
            setupCrashLogging()
        }
    }

    private fun setupCrashLogging() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e(TAG, "Uncaught exception on thread ${thread.name}", throwable)
                logCrashToFile(throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Error logging crash", e)
            } finally {
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun logCrashToFile(throwable: Throwable) {
        runCatching {
            val crashFile = File(filesDir, CRASH_LOG_FILE)
            // Keep crash file bounded: if it exceeds 64KB, truncate it
            if (crashFile.exists() && crashFile.length() > 64 * 1024) {
                crashFile.delete()
            }

            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))

            crashFile.appendText("[$timestamp] EXCEPTION:\n$sw\n--------------------\n")
        }
    }

    companion object {
        private const val TAG = "GothwadApplication"
        private const val CRASH_LOG_FILE = "gothwad_crash.log"

        /** In-memory flag: true once device lock is satisfied for the current process lifetime */
        @Volatile
        var hasUnlockedDeviceThisProcess: Boolean = false
    }
}
