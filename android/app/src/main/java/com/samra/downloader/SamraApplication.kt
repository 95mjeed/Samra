package com.samra.downloader

import android.app.Application
import android.content.Context
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File

class SamraApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Record any uncaught crash to an on-device file for diagnosis. Best-effort.
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                File(getExternalFilesDir(null), "last_crash.txt")
                    .writeText(Log.getStackTraceString(e))
            } catch (_: Throwable) {}
            prev?.uncaughtException(t, e)
        }

        // Warm up the Python runtime OFF the main thread. On a device's FIRST launch
        // Chaquopy extracts the whole stdlib + wheels, which (done here synchronously)
        // would block the UI thread long enough to ANR — the app would look like it
        // "won't open". Downloads call ensureStarted(), which blocks only their own
        // background thread if this warm-up hasn't finished yet.
        kotlin.concurrent.thread(name = "py-warmup") {
            try { ensureStarted(this) } catch (_: Throwable) {}
        }
    }

    companion object {
        /** Start the Python runtime if it isn't already. Safe to call from any
         *  background thread; never call on the main thread (it can block for
         *  seconds on first run). */
        @Synchronized
        fun ensureStarted(ctx: Context) {
            if (!Python.isStarted()) Python.start(AndroidPlatform(ctx.applicationContext))
        }
    }
}
