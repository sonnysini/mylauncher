package app.mylauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import app.mylauncher.keys.KeyRules

/**
 * "Press the key to customise". The accessibility service hands the key over while this is open
 * (it sees keys Fire OS keeps from apps); without the service the window's own key events are
 * used. Navigation, Home, Back, ☰ and Alexa are not accepted; Back cancels.
 */
@Composable
fun KeyCaptureOverlay(onKey: (Int) -> Unit, onDismiss: () -> Unit) {
    val current by rememberUpdatedState(onKey)
    val box = remember { FocusRequester() }
    DisposableEffect(Unit) {
        KeyRules.capturing.value = true
        onDispose { KeyRules.capturing.value = false }
    }
    LaunchedEffect(Unit) {
        runCatching { box.requestFocus() }
        KeyRules.captured.collect { current(it) }
    }
    BackHandler(onBack = onDismiss)

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .focusRequester(box)
                .onPreviewKeyEvent { e ->
                    val code = e.nativeKeyEvent.keyCode
                    if (KeyRules.excluded(code)) return@onPreviewKeyEvent false
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) current(code)
                    true
                }
                .focusable()
                .width(420.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF161D2A))
                .padding(horizontal = 24.dp, vertical = 26.dp),
        ) {
            Text("Premi il tasto da personalizzare…", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(10.dp))
            Text(
                "Frecce, OK, Home, Indietro, ☰ e Alexa non si possono cambiare.\nIndietro annulla.",
                fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f),
            )
        }
    }
}
