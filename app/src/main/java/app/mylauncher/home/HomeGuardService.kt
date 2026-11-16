package app.mylauncher.home

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import app.mylauncher.R

/**
 * Keeps the Amazon home out of the way: Fire OS doesn't let a third-party launcher take the Home
 * button, so every time the Amazon home comes to the front (Home, Back, remote app buttons...)
 * this brings My Launcher back.
 *
 * Both reading the system log and the jump back go through the Fire's own ADB ([LocalAdb]), as
 * the shell user: an app's own start would be held back 5 s after Home. Nothing outside the Fire.
 *
 * Exception: Fire settings are a screen of the Amazon home opened by another app (a START of
 * com.amazon.tv.launcher not "from uid 1000"); while the user stays there nothing bounces.
 */
class HomeGuardService : Service() {

    @Volatile private var running = false
    @Volatile private var adb: LocalAdb? = null
    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        if (worker?.isAlive != true) {
            running = true
            worker = Thread(::watch, "home-guard").apply { isDaemon = true; start() }
        }
        return START_STICKY
    }

    private fun watch() {
        var delay = RETRY_MIN_MS
        while (running) {
            val started = SystemClock.elapsedRealtime()
            runCatching {
                LocalAdb(this).also { adb = it }.use { follow(it) }
            }.onFailure { Log.w(TAG, "local adb", it) }
            adb = null
            if (!running) break
            // A run that lasted is a dropped connection: retry soon. Quick failures (debugging
            // off, key refused) back off, so a refused dialog doesn't come back every second.
            delay = if (SystemClock.elapsedRealtime() - started > 60_000) RETRY_MIN_MS
            else (delay * 2).coerceAtMost(RETRY_MAX_MS)
            SystemClock.sleep(delay)
        }
    }

    /** One logcat run: from now on (-T 1 skips the history), until the stream ends. */
    private fun follow(adb: LocalAdb) {
        adb.open(LOGCAT).use { stream ->
            Log.i(TAG, "following the log")
            var settings = false
            while (running) {
                val line = stream.source.readUtf8Line() ?: break
                val amazon = AMAZON_HOME in line
                when {
                    "START u0" in line && amazon && "from uid 1000" in line -> { settings = false; bringBack(adb) }
                    "START u0" in line && amazon -> settings = true
                    "_resume" in line && amazon -> if (!settings) bringBack(adb)
                    "_resume" in line -> settings = false
                }
            }
        }
    }

    private var lastBack = 0L

    private fun bringBack(adb: LocalAdb) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBack < DEBOUNCE_MS) return
        lastBack = now
        runCatching { adb.run(AM_START) }.onFailure { Log.w(TAG, "bring back", it) }
    }

    override fun onDestroy() {
        running = false
        adb?.close()
        super.onDestroy()
    }

    private fun goForeground() {
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Tasto Home", NotificationManager.IMPORTANCE_MIN))
            Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setPriority(Notification.PRIORITY_MIN)
        }
        val notification = builder
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Tasto Home → My Launcher")
            .setOngoing(true)
            .build()
        runCatching { startForeground(NOTIFICATION_ID, notification) }
            .onFailure { Log.w(TAG, "startForeground", it) }
    }

    private companion object {
        const val TAG = "MyLauncherHome"
        const val CHANNEL = "home"
        const val NOTIFICATION_ID = 8
        const val AMAZON_HOME = "com.amazon.tv.launcher/"
        const val LOGCAT = "logcat -T 1 -b main -b system -b events " +
            "-s ActivityManager:I am_set_resumed_activity:I am_resume_activity:I"
        /** 0x10200000 = NEW_TASK | RESET_TASK_IF_NEEDED, like the Home button itself. */
        const val AM_START = "am start -n app.mylauncher/.MainActivity -f 0x10200000"
        const val RETRY_MIN_MS = 2_000L
        const val RETRY_MAX_MS = 120_000L
        /** START and resume of the same return both match: one jump is enough. */
        const val DEBOUNCE_MS = 800L
    }
}

object HomeGuard {
    /** Starts the guard; harmless to call again. */
    fun start(context: Context) {
        val intent = Intent(context, HomeGuardService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }.onFailure { Log.w("MyLauncherHome", "start", it) }
    }
}
