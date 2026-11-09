package app.mylauncher.volume

import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Software volume of the whole Fire, on top of the TV/projector volume.
 *
 * Fire OS keeps the HDMI output at fixed volume, so the Android stream volume does nothing there;
 * the cut is done by [VolumeService] with an equalizer on the global output mix. 100% = off.
 */
object Volume {
    const val STEP = 5

    /** Cut per percentage point: 5 points ≈ 1.2 dB, 70% ≈ -7 dB, 5% ≈ -23 dB. */
    const val DB_PER_POINT = 0.24
    /** Bottom of the Fire's volume effect: 0% is silence. */
    const val MUTE_MB = -9600

    /** Target attenuation for [percent], in millibels (0 = untouched). */
    fun millibels(percent: Int): Int = when {
        percent >= 100 -> 0
        percent <= 0 -> MUTE_MB
        else -> (-(100 - percent) * DB_PER_POINT * 100).toInt()
    }

    private const val PREFS = "volume"
    private const val KEY = "percent"

    private val state = MutableStateFlow<Int?>(null)

    fun percent(context: Context): StateFlow<Int?> {
        if (state.value == null) state.value = read(context)
        return state
    }

    fun read(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, 100).coerceIn(0, 100)

    fun set(context: Context, percent: Int) {
        val value = percent.coerceIn(0, 100)
        state.value = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, value).apply()
        sync(context)
    }

    /** Starts the service when a cut is set (or re-applies it), stops it at 100%. */
    fun sync(context: Context) {
        val intent = Intent(context, VolumeService::class.java)
        runCatching {
            when {
                read(context) >= 100 -> context.stopService(intent)
                Build.VERSION.SDK_INT >= 26 -> context.startForegroundService(intent)
                else -> context.startService(intent)
            }
        }
    }
}
