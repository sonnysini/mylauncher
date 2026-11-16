package app.mylauncher.home

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import app.mylauncher.MainActivity
import app.mylauncher.R

/**
 * Keeps the Amazon home out of the way: Fire OS doesn't let a third-party launcher take the Home
 * button, so every time the Amazon home comes to the front (Home, Back, remote app buttons...)
 * this brings My Launcher back.
 *
 * It follows the system log, which needs READ_LOGS, granted once per Fire over ADB:
 * `pm grant app.mylauncher android.permission.READ_LOGS`. Without it the service doesn't start.
 *
 * Exception: Fire settings are a screen of the Amazon home opened by another app (a START of
 * com.amazon.tv.launcher not "from uid 1000"); while the user stays there nothing bounces.
 */
class HomeGuardService : Service() {

    @Volatile private var running = false
    private var worker: Thread? = null
    private var process: Process? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!HomeGuard.canRead(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        goForeground()
        if (intent?.getBooleanExtra(HomeGuard.EXTRA_BOOT, false) == true) {
            // At power-on the Fire opens its own home before this service runs: nothing in the
            // log to react to, so look at what is in front now.
            Thread({ if (amazonHomeInFront()) bringBack() }, "home-guard-boot").start()
        }
        if (worker?.isAlive != true) {
            running = true
            worker = Thread(::watch, "home-guard").apply { isDaemon = true; start() }
        }
        return START_STICKY
    }

    private fun watch() {
        while (running) {
            runCatching { follow() }.onFailure { Log.w(TAG, "logcat", it) }
            if (running) SystemClock.sleep(RESTART_DELAY_MS)
        }
    }

    /** Last activity resumed, from the log history: is it the Amazon home? */
    private fun amazonHomeInFront(): Boolean = runCatching {
        val p = ProcessBuilder("logcat", "-d", "-b", "events", "-s", "am_set_resumed_activity:I")
            .redirectErrorStream(true).start()
        val last = p.inputStream.bufferedReader().useLines { lines ->
            lines.lastOrNull { "am_set_resumed_activity" in it }
        }
        runCatching { p.destroy() }
        last != null && AMAZON_HOME in last
    }.getOrDefault(false)

    /** One logcat run: from now on (-T 1 skips the history), until it dies. */
    private fun follow() {
        val p = ProcessBuilder(
            "logcat", "-T", "1", "-b", "main", "-b", "system", "-b", "events",
            "-s", "ActivityManager:I", "am_set_resumed_activity:I", "am_resume_activity:I",
        ).redirectErrorStream(true).start()
        process = p
        Log.i(TAG, "following the log")
        var settings = false
        p.inputStream.bufferedReader().useLines { lines ->
            for (line in lines) {
                if (!running) break
                val amazon = AMAZON_HOME in line
                when {
                    "START u0" in line && amazon && "from uid 1000" in line -> { settings = false; bringBack() }
                    "START u0" in line && amazon -> settings = true
                    "_resume" in line && amazon -> if (!settings) bringBack()
                    "_resume" in line -> settings = false
                }
            }
        }
        runCatching { p.destroy() }
    }

    private var lastBack = 0L

    private fun bringBack() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBack < DEBOUNCE_MS) return
        lastBack = now
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
            )
        }.onFailure { Log.w(TAG, "bring back", it) }
    }

    override fun onDestroy() {
        running = false
        runCatching { process?.destroy() }
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
        const val RESTART_DELAY_MS = 2_000L
        /** START and resume of the same return both match: one jump is enough. */
        const val DEBOUNCE_MS = 800L
    }
}

object HomeGuard {
    fun canRead(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED

    /** Starts the guard if READ_LOGS was granted; harmless to call again. */
    const val EXTRA_BOOT = "boot"

    /** [boot]: just powered on, bring the launcher up if the Amazon home is in front. */
    fun start(context: Context, boot: Boolean = false) {
        if (!canRead(context)) return
        val intent = Intent(context, HomeGuardService::class.java).putExtra(EXTRA_BOOT, boot)
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }.onFailure { Log.w("MyLauncherHome", "start", it) }
    }
}
