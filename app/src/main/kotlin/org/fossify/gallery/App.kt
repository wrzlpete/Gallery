package org.fossify.gallery

import android.os.Build
import com.github.ajalt.reprint.core.Reprint
import com.squareup.picasso.Downloader
import com.squareup.picasso.Picasso
import okhttp3.Request
import okhttp3.Response
import org.fossify.commons.FossifyApp
import org.fossify.gallery.extensions.logCrash
import java.io.PrintWriter
import java.io.StringWriter

class App : FossifyApp() {

    override val isAppLockFeatureAvailable = true

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        Reprint.initialize(this)
        Picasso.setSingletonInstance(Picasso.Builder(this).downloader(object : Downloader {
            override fun load(request: Request) = Response.Builder().build()

            override fun shutdown() {}
        }).build())
    }

    /**
     * Installs a process-wide UncaughtExceptionHandler that persists the crash stack trace
     * (and the thread/process info) to crash.log in the app's external files dir, then
     * delegates to the previous handler so the default crash dialog/Process.killProcess
     * behavior is preserved.
     *
     * This exists to diagnose an intermittent crash reported when returning from a fullscreen
     * image to a filtered "Show all" list. The crash is rare and hard to reproduce, so
     * capturing the stack trace on-device lets the user share it. The logger is best-effort
     * and never throws.
     */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val info = buildString {
                    appendLine("=== CRASH ===")
                    appendLine("Thread: ${thread.name}")
                    appendLine("Process: ${android.os.Process.myPid()}, API ${Build.VERSION.SDK_INT}")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Stacktrace:")
                    append(sw.toString())
                    appendLine()
                }
                logCrash(info)
            } catch (ignored: Throwable) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
