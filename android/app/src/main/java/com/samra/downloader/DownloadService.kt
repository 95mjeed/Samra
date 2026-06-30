package com.samra.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.chaquo.python.Python
import kotlin.concurrent.thread

/**
 * Foreground service that runs the Python download queue. Because it's a
 * foreground service with an ongoing notification, downloads keep running when
 * the user leaves the app or locks the screen.
 */
class DownloadService : Service() {

    @Volatile private var cancelRequested = false
    @Volatile private var working = false
    private var lastNotify = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val mgr = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Audiobook download progress" }
            mgr.createNotificationChannel(ch)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            cancelRequested = true
            DownloadController.addLog("Cancelling after the current book…")
            return START_NOT_STICKY
        }

        if (working) {
            DownloadController.addLog("A download is already running.")
            return START_NOT_STICKY
        }

        val data = intent ?: run { stopSelf(); return START_NOT_STICKY }
        val urls = data.getStringArrayListExtra(EXTRA_URLS) ?: arrayListOf()
        if (urls.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val username = data.getStringExtra(EXTRA_USERNAME)
        val password = data.getStringExtra(EXTRA_PASSWORD)
        val cookie = data.getStringExtra(EXTRA_COOKIE)
        val output = data.getStringExtra(EXTRA_OUTPUT) ?: filesDir.absolutePath
        val format = data.getStringExtra(EXTRA_FORMAT) ?: ""
        val combine = data.getBooleanExtra(EXTRA_COMBINE, false)
        val library = data.getStringExtra(EXTRA_LIBRARY)
        val ffmpeg = data.getStringExtra(EXTRA_FFMPEG)
        val creds = data.getStringExtra(EXTRA_CREDS) ?: "{}"

        cancelRequested = false
        working = true
        DownloadController.setRunning(true)
        DownloadController.setProgress(0f)
        DownloadController.setCurrentBook("")

        startForeground(notify("Starting…", null, ongoing = true, indeterminate = true))

        thread(name = "samra-service") {
            runQueue(urls, username, password, cookie, output, format, combine, library, ffmpeg, creds)
        }
        return START_NOT_STICKY
    }

    private fun runQueue(
        urls: List<String>,
        username: String?, password: String?, cookie: String?,
        output: String, format: String, combine: Boolean,
        library: String?, ffmpeg: String?, creds: String
    ) {
        DownloadController.addLog("Saving to: $output")
        DownloadController.setQueue(urls.map {
            DownloadController.QItem(it, shortName(it), "queued", 0f)
        })
        SamraApplication.ensureStarted(applicationContext)   // no-op if already warm
        val py = Python.getInstance()
        val module = py.getModule("samra")

        var active = -1
        val listener = object : SamraListener {
            override fun onLog(line: String) = DownloadController.addLog(line)
            override fun onProgress(fraction: Double) {
                val f = fraction.toFloat().coerceIn(0f, 1f)
                DownloadController.setProgress(f)
                if (active >= 0) DownloadController.updateItem(active) { it.copy(pct = f) }
                maybeNotify(f)
            }
            override fun onBook(title: String, index: Int, total: Int) {
                val label = if (total > 1) "$title  ($index/$total)" else title
                DownloadController.setCurrentBook(label)
                if (active >= 0) DownloadController.updateItem(active) { it.copy(title = title) }
                pushNotify(label, 0f, indeterminate = false)
            }
        }

        // Keep the engine's error log off public storage (app-private filesDir).
        try { module.callAttr("set_log_dir", filesDir.absolutePath) } catch (_: Throwable) {}
        var ok = 0
        for ((i, url) in urls.withIndex()) {
            if (cancelRequested) {
                DownloadController.addLog("Cancelled — stopped before remaining books.")
                break
            }
            active = i
            DownloadController.updateItem(i) { it.copy(status = "downloading", pct = 0f) }
            DownloadController.addLog("──────────")
            DownloadController.addLog("[${i + 1}/${urls.size}] $url")
            DownloadController.setProgress(0f)
            pushNotify("[${i + 1}/${urls.size}] working…", 0f, indeterminate = true)
            try {
                val result = module.callAttr(
                    "run_download",
                    url,
                    if (username.isNullOrBlank()) null else username,
                    if (password.isNullOrBlank()) null else password,
                    if (cookie.isNullOrBlank()) null else cookie,
                    output,
                    format,
                    combine,
                    if (library.isNullOrBlank()) null else library,
                    if (ffmpeg.isNullOrBlank()) null else ffmpeg,
                    creds,
                    listener
                ).toString()
                val good = result.contains("\"ok\": true")
                if (good) ok++
                DownloadController.updateItem(i) { it.copy(status = if (good) "done" else "failed", pct = if (good) 1f else it.pct) }
            } catch (e: Throwable) {
                DownloadController.addLog("Error: " + (e.message ?: e.toString()))
                DownloadController.updateItem(i) { it.copy(status = "failed") }
            }
        }

        val folder = output.substringAfterLast("/0/").ifBlank { output }
        val summary = if (ok > 0) "✓ $ok/${urls.size} saved → $folder"
        else "✗ Download failed (${urls.size}). Check the console / your login."
        DownloadController.addLog("══════════")
        DownloadController.addLog(summary)
        DownloadController.emitEvent(summary)
        DownloadController.setRunning(false)
        DownloadController.setProgress(0f)
        DownloadController.setCurrentBook("")
        working = false

        // Leave a final, dismissible notification with the summary.
        val mgr = getSystemService(NotificationManager::class.java)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        mgr.notify(NOTIF_ID, notify(summary, null, ongoing = false, indeterminate = false))
        stopSelf()
    }

    private fun shortName(url: String): String =
        url.trimEnd('/').substringAfterLast('/').substringBefore('?').ifBlank { url }.take(48)

    // --- notifications ----------------------------------------------------

    private fun maybeNotify(progress: Float) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotify < 500) return
        lastNotify = now
        pushNotify(DownloadController.currentBook.value.ifBlank { "Downloading…" },
            progress, indeterminate = false)
    }

    private fun pushNotify(text: String, progress: Float, indeterminate: Boolean) {
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.notify(NOTIF_ID, notify(text, progress, ongoing = true, indeterminate = indeterminate))
    }

    private fun notify(
        text: String,
        progress: Float?,
        ongoing: Boolean,
        indeterminate: Boolean
    ): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val cancelIntent = PendingIntent.getService(
            this, 1,
            Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Samra")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(openIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
        if (ongoing) {
            b.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent)
            if (progress != null || indeterminate) {
                b.setProgress(100, ((progress ?: 0f) * 100).toInt(), indeterminate)
            }
        } else {
            b.setSmallIcon(android.R.drawable.stat_sys_download_done)
        }
        return b.build()
    }

    private fun startForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this, NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val NOTIF_ID = 1001
        const val ACTION_CANCEL = "com.samra.downloader.CANCEL"
        const val EXTRA_URLS = "urls"
        const val EXTRA_USERNAME = "username"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_COOKIE = "cookie"
        const val EXTRA_OUTPUT = "output"
        const val EXTRA_FORMAT = "format"
        const val EXTRA_COMBINE = "combine"
        const val EXTRA_LIBRARY = "library"
        const val EXTRA_FFMPEG = "ffmpeg"
        const val EXTRA_CREDS = "creds"

        fun start(context: Context, urls: ArrayList<String>, username: String,
                  password: String, cookie: String?, output: String,
                  format: String, combine: Boolean, library: String, ffmpeg: String,
                  creds: String = "{}") {
            val i = Intent(context, DownloadService::class.java).apply {
                putStringArrayListExtra(EXTRA_URLS, urls)
                putExtra(EXTRA_USERNAME, username)
                putExtra(EXTRA_PASSWORD, password)
                putExtra(EXTRA_COOKIE, cookie)
                putExtra(EXTRA_OUTPUT, output)
                putExtra(EXTRA_FORMAT, format)
                putExtra(EXTRA_COMBINE, combine)
                putExtra(EXTRA_LIBRARY, library)
                putExtra(EXTRA_FFMPEG, ffmpeg)
                putExtra(EXTRA_CREDS, creds)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, i)
        }

        fun cancel(context: Context) {
            context.startService(
                Intent(context, DownloadService::class.java).setAction(ACTION_CANCEL)
            )
        }
    }
}
