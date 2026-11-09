package app.mylauncher.keys

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** What a remapped remote key does. */
sealed interface KeyAction {
    data class OpenApp(val packageName: String) : KeyAction
    data object VolumeUp : KeyAction
    data object VolumeDown : KeyAction
}

/**
 * Remote keys remapped by the user (key code → action), applied by [KeyService]. A key without a
 * rule does what it always did. Process-wide: the service runs in the launcher's process.
 */
object KeyRules {

    private const val PREFS = "keys"

    private val state = MutableStateFlow<Map<Int, KeyAction>?>(null)

    /** While the "press the key" screen is open the service hands every key to it instead. */
    val capturing = MutableStateFlow(false)
    private val capturedFlow = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val captured: SharedFlow<Int> = capturedFlow

    /** True while the accessibility service is connected. */
    val running = MutableStateFlow(false)

    fun rules(context: Context): StateFlow<Map<Int, KeyAction>?> {
        if (state.value == null) state.value = read(context)
        return state
    }

    fun current(context: Context): Map<Int, KeyAction> = rules(context).value.orEmpty()

    fun set(context: Context, keyCode: Int, action: KeyAction) {
        prefs(context).edit().putString(keyCode.toString(), encode(action)).apply()
        state.value = read(context)
    }

    fun remove(context: Context, keyCode: Int) {
        prefs(context).edit().remove(keyCode.toString()).apply()
        state.value = read(context)
    }

    fun capture(keyCode: Int) {
        capturedFlow.tryEmit(keyCode)
    }

    /**
     * Keys that are never remapped: navigation, Home/Back, the menu key and Alexa. Remapping them
     * would lock the user out of the remote.
     */
    fun excluded(keyCode: Int): Boolean = keyCode in EXCLUDED

    /** Whether the service is switched on in the system settings (it is enabled via ADB). */
    fun enabledInSettings(context: Context): Boolean {
        val me = ComponentName(context, KeyService::class.java)
        val list = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val on = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
        return on && list.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun keyName(keyCode: Int): String = NAMES[keyCode]
        ?: KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_").replace('_', ' ').lowercase()
            .replaceFirstChar { it.uppercase() }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun read(context: Context): Map<Int, KeyAction> =
        prefs(context).all.mapNotNull { (k, v) ->
            val code = k.toIntOrNull() ?: return@mapNotNull null
            val action = (v as? String)?.let(::decode) ?: return@mapNotNull null
            code to action
        }.sortedBy { it.first }.toMap()

    private fun encode(action: KeyAction): String = when (action) {
        is KeyAction.OpenApp -> "app:${action.packageName}"
        KeyAction.VolumeUp -> "vol+"
        KeyAction.VolumeDown -> "vol-"
    }

    private fun decode(value: String): KeyAction? = when {
        value.startsWith("app:") -> KeyAction.OpenApp(value.removePrefix("app:"))
        value == "vol+" -> KeyAction.VolumeUp
        value == "vol-" -> KeyAction.VolumeDown
        else -> null
    }

    private val EXCLUDED = setOf(
        KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU,
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_VOICE_ASSIST,
        KeyEvent.KEYCODE_ASSIST, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_SLEEP, KeyEvent.KEYCODE_WAKEUP,
        KeyEvent.KEYCODE_UNKNOWN,
    )

    private val NAMES = mapOf(
        KeyEvent.KEYCODE_TV to "Tasto TV",
        KeyEvent.KEYCODE_LIVE_TV to "Tasto TV",
        KeyEvent.KEYCODE_GUIDE to "Guida",
        KeyEvent.KEYCODE_VOLUME_UP to "Volume +",
        KeyEvent.KEYCODE_VOLUME_DOWN to "Volume −",
        KeyEvent.KEYCODE_VOLUME_MUTE to "Muto",
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to "Play/Pausa",
        KeyEvent.KEYCODE_MEDIA_REWIND to "Indietro veloce",
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD to "Avanti veloce",
        KeyEvent.KEYCODE_CHANNEL_UP to "Canale +",
        KeyEvent.KEYCODE_CHANNEL_DOWN to "Canale −",
        KeyEvent.KEYCODE_SETTINGS to "Impostazioni",
    )
}
