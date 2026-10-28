package app.mylauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.mylauncher.LoginState
import kotlinx.coroutines.delay

/** Nuvio QR login: scan with the phone, approve on nuvio.tv, the TV picks the session up. */
@Composable
fun LoginOverlay(state: LoginState, onRetry: () -> Unit, onClose: () -> Unit) {
    val button = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { button.requestFocus() } }
    LaunchedEffect(state) {
        if (state is LoginState.Done) { delay(1500); onClose() }
    }
    BackHandler(onBack = onClose)

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .trapFocus()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF161D2A))
                .padding(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                if (state is LoginState.Waiting) {
                    val qr = remember(state.login.url) { qrBitmap(state.login.url) }
                    Image(qr, contentDescription = "QR", filterQuality = FilterQuality.None, modifier = Modifier.size(210.dp))
                } else {
                    Text(if (state is LoginState.Done) "✓" else "…", fontSize = 64.sp, color = Background)
                }
            }
            Spacer(Modifier.width(28.dp))
            Column(Modifier.width(340.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Collega Nuvio", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                when (state) {
                    is LoginState.Waiting -> {
                        Text("Inquadra il QR col telefono e accedi al tuo account Nuvio.", fontSize = 15.sp, color = Muted)
                        Text("Oppure apri nuvio.tv/link e inserisci:", fontSize = 15.sp, color = Muted)
                        Text(state.login.userCode, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    LoginState.Done -> Text("Collegato!", fontSize = 18.sp, color = Color.White)
                    is LoginState.Failed -> Text(state.message, fontSize = 15.sp, color = Color(0xFFFF8A80))
                    else -> Text("Preparo il codice…", fontSize = 15.sp, color = Muted)
                }
                Spacer(Modifier.height(8.dp))
                val failed = state is LoginState.Failed
                Surface(
                    onClick = if (failed) onRetry else onClose,
                    modifier = Modifier.focusRequester(button),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color(0xFF243044),
                        focusedContainerColor = Color.White,
                        contentColor = Color.White,
                        focusedContentColor = Background,
                    ),
                ) {
                    Text(if (failed) "Riprova" else "Annulla", fontSize = 16.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                }
            }
        }
    }
}
