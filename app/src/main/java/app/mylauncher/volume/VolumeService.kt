package app.mylauncher.volume

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.IBinder
import android.util.Log
import app.mylauncher.R
import java.util.UUID

/**
 * Holds a volume effect on audio session 0 (the global output mix): a flat cut of the whole
 * Fire. The effect lives only as long as this process, hence a foreground service that comes
 * back on its own and after a reboot ([BootReceiver]).
 */
class VolumeService : Service() {

    private var equalizer: Equalizer? = null
    private var volumeEffect: AudioEffect? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val percent = Volume.read(this)
        if (percent >= 100) {
            release()
            stopSelf()
            return START_NOT_STICKY
        }
        goForeground()
        val mb = Volume.millibels(percent)
        if (!applyVolumeEffect(percent, mb)) applyEqualizer(percent, mb)
        return START_STICKY
    }

    /**
     * Fire OS ships the NXP bundle "Volume" effect (-96..0 dB) but the SDK only exposes it to
     * system code: built through the hidden AudioEffect constructor, on session 0 like the
     * equalizer. Covers the whole curve, down to silence.
     */
    private fun applyVolumeEffect(percent: Int, mb: Int): Boolean {
        val effect = volumeEffect ?: runCatching {
            AudioEffect::class.java
                .getConstructor(UUID::class.java, UUID::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .newInstance(VOLUME_TYPE, VOLUME_BUNDLE, PRIORITY, 0)
        }.onFailure { Log.w(TAG, "volume effect on session 0 refused", it) }.getOrNull()?.also { volumeEffect = it }
        ?: return false
        return runCatching {
            val set = AudioEffect::class.java.getMethod("setParameter", Int::class.javaPrimitiveType, Short::class.javaPrimitiveType)
            val status = set.invoke(effect, VOLUME_PARAM_LEVEL, mb.coerceIn(Volume.MUTE_MB, 0).toShort()) as Int
            check(status == AudioEffect.SUCCESS) { "setParameter status $status" }
            effect.enabled = true
            // The equalizer of an older version would add its own cut on top.
            equalizer?.let { runCatching { it.enabled = false; it.release() } }
            equalizer = null
            Log.i(TAG, "volume $percent% -> $mb mB (volume effect, control=${effect.hasControl()})")
        }.onFailure {
            Log.w(TAG, "volume effect failed, falling back to equalizer", it)
            runCatching { effect.release() }
            volumeEffect = null
        }.isSuccess
    }

    /** Fallback: every band at the same level, capped at the equalizer's floor (about -15 dB). */
    private fun applyEqualizer(percent: Int, mb: Int) {
        val eq = equalizer ?: runCatching { Equalizer(PRIORITY, 0) }
            .onFailure { Log.w(TAG, "equalizer on session 0 refused", it) }
            .getOrNull()
            ?.also { equalizer = it }
            ?: return
        runCatching {
            val (min, max) = eq.bandLevelRange.let { it[0].toInt() to it[1].toInt() }
            val level = mb.coerceIn(min, 0).toShort()
            for (band in 0 until eq.numberOfBands) eq.setBandLevel(band.toShort(), level)
            eq.enabled = true
            Log.i(TAG, "volume $percent% -> $level mB (equalizer, range $min..$max mB, control=${eq.hasControl()})")
        }.onFailure { Log.w(TAG, "apply failed", it) }
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private fun release() {
        volumeEffect?.let { runCatching { it.enabled = false; it.release() } }
        volumeEffect = null
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
        /** OpenSL ES volume interface (SL_IID_VOLUME) and its NXP bundle implementation. */
        val VOLUME_TYPE: UUID = UUID.fromString("09e8ede0-ddde-11db-b4f6-0002a5d5c51b")
        val VOLUME_BUNDLE: UUID = UUID.fromString("119341a0-8469-11df-81f9-0002a5d5c51b")
        const val VOLUME_PARAM_LEVEL = 0
    }
}

/** Puts the cut back after a reboot, before anyone opens the launcher. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Volume.sync(context)
    }
}
