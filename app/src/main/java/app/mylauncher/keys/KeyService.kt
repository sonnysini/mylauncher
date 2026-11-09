package app.mylauncher.keys

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import app.mylauncher.volume.Volume

/**
 * Applies [KeyRules] to the remote, whichever app is in front. Only filters key events: it reads
 * no window content. Volume keys show a small bar over the current app (an accessibility overlay,
 * no extra permission needed).
 */
class KeyService : AccessibilityService() {

    /** Keys whose DOWN was taken: their UP is taken too, so the app never sees half a press. */
    private val consumed = HashSet<Int>()
    private val main = Handler(Looper.getMainLooper())
    private var hud: VolumeHud? = null
    private val hideHud = Runnable { hud?.let { runCatching { windowManager().removeView(it) } }; hud = null }

    override fun onServiceConnected() {
        super.onServiceConnected()
        KeyRules.rules(this)
        KeyRules.running.value = true
        Log.i(TAG, "connected, ${KeyRules.current(this).size} rules")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        KeyRules.running.value = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        KeyRules.running.value = false
        main.removeCallbacks(hideHud)
        hideHud.run()
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (KeyRules.excluded(code)) return false
        if (event.action == KeyEvent.ACTION_UP) return consumed.remove(code)
        if (event.action != KeyEvent.ACTION_DOWN) return code in consumed

        if (KeyRules.capturing.value) {
            if (event.repeatCount == 0) KeyRules.capture(code)
            consumed += code
            return true
        }
        val action = KeyRules.current(this)[code] ?: return false
        consumed += code
        when (action) {
            is KeyAction.OpenApp -> if (event.repeatCount == 0) open(action.packageName)
            KeyAction.VolumeUp -> stepVolume(+Volume.STEP)
            KeyAction.VolumeDown -> stepVolume(-Volume.STEP)
        }
        return true
    }

    private fun open(packageName: String) {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Log.w(TAG, "$packageName not installed")
            return
        }
        runCatching {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        }.onFailure { Log.w(TAG, "open $packageName", it) }
    }

    private fun stepVolume(delta: Int) {
        val value = (Volume.read(this) + delta).coerceIn(0, 100)
        Volume.set(this, value)
        showHud(value)
    }

    private fun showHud(percent: Int) {
        val view = hud ?: VolumeHud(this).also { v ->
            val density = resources.displayMetrics.density
            val params = WindowManager.LayoutParams(
                (380 * density).toInt(),
                (86 * density).toInt(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = (36 * density).toInt()
            }
            if (runCatching { windowManager().addView(v, params) }.onFailure { Log.w(TAG, "hud", it) }.isFailure) return
            hud = v
        }
        view.percent = percent
        main.removeCallbacks(hideHud)
        main.postDelayed(hideHud, HUD_MS)
    }

    private fun windowManager() = getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private companion object {
        const val TAG = "MyLauncherKeys"
        const val HUD_MS = 2_000L
    }
}

/** "Volume 45%" with the same 20-step bar as the launcher's volume screen. */
private class VolumeHud(context: Context) : View(context) {

    var percent = 100
        set(value) { field = value; invalidate() }

    private val density = context.resources.displayMetrics.density
    private val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(235, 0x16, 0x1D, 0x2A) }
    private val lit = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val dim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(40, 255, 255, 255) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 18 * density
        typeface = Typeface.DEFAULT_BOLD
    }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = 18 * density
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, 16 * density, 16 * density, box)
        canvas.drawText("Volume", pad, pad + 16 * density, text)
        val label = "$percent%"
        canvas.drawText(label, w - pad - text.measureText(label), pad + 16 * density, text)

        val steps = 100 / Volume.STEP
        val gap = 3.5f * density
        val top = h - pad - 20 * density
        val segment = (w - 2 * pad - gap * (steps - 1)) / steps
        val on = percent / Volume.STEP
        for (i in 0 until steps) {
            val left = pad + i * (segment + gap)
            rect.set(left, top, left + segment, top + 20 * density)
            canvas.drawRoundRect(rect, 3 * density, 3 * density, if (i < on) lit else dim)
        }
    }
}
