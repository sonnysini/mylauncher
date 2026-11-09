package app.mylauncher.volume

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.IBinder
import android.util.Log
import app.mylauncher.R

/**
 * Holds an [Equalizer] on audio session 0 (the global output mix) with every band at the same
 * negative level: a flat cut of the whole Fire. The effect lives only as long as this process,
 * hence a foreground service that comes back on its own and after a reboot ([BootReceiver]).
 */
class VolumeService : Service() {

    private var equalizer: Equalizer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val percent = Volume.read(this)
        if (percent >= 100) {
            release()
            stopSelf()
            return START_NOT_STICKY
        }
        goForeground()
        val eq = equalizer ?: runCatching { Equalizer(PRIORITY, 0) }
            .onFailure { Log.w(TAG, "equalizer on session 0 refused", it) }
            .getOrNull()
            ?.also { equalizer = it }
        if (eq != null) runCatching {
            val (min, max) = eq.bandLevelRange.let { it[0].toInt() to it[1].toInt() }
            val level = (min * (100 - percent) / 100).toShort()
            for (band in 0 until eq.numberOfBands) eq.setBandLevel(band.toShort(), level)
            eq.enabled = true
            Log.i(TAG, "volume $percent% -> $level mB (range $min..$max mB, ${eq.numberOfBands} bands, control=${eq.hasControl()})")
        }.onFailure { Log.w(TAG, "apply failed", it) }
        return START_STICKY
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private fun release() {
        equalizer?.let { runCatching { it.enabled = false; it.release() } }
        equalizer = null
    }

    private fun goForeground() {
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Volume", NotificationManager.IMPORTANCE_MIN))
            Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setPriority(Notification.PRIORITY_MIN)
        }
        val notification = builder
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Volume ${Volume.read(this)}%")
            .setOngoing(true)
            .build()
        runCatching { startForeground(NOTIFICATION_ID, notification) }
            .onFailure { Log.w(TAG, "startForeground", it) }
    }

    private companion object {
        const val TAG = "MyLauncherVolume"
        const val CHANNEL = "volume"
        const val NOTIFICATION_ID = 7
        /** Highest: takes the global equalizer from any other app holding one. */
        const val PRIORITY = Int.MAX_VALUE
    }
}

/** Puts the cut back after a reboot, before anyone opens the launcher. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Volume.sync(context)
    }
}
