package app.mylauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.mylauncher.volume.Volume

/**
 * Software volume: ◀ ▶ on the bar move it in 5% steps and apply at once, so it can be judged by
 * ear while a show plays behind. Back closes.
 */
@Composable
fun VolumeOverlay(percent: Int, onChange: (Int) -> Unit, onDismiss: () -> Unit) {
    val bar = remember { FocusRequester() }
    var barFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { bar.requestFocus() } }
    BackHandler(onBack = onDismiss)

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .trapFocus()
                .width(420.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF161D2A))
                .padding(horizontal = 24.dp, vertical = 22.dp),
        ) {
            Text("Volume", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(5.dp))
            Text(
                "Abbassa l'audio di tutta la Fire (si somma al volume del proiettore)",
                fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("◀", fontSize = 20.sp, color = Color.White.copy(alpha = if (barFocused) 0.8f else 0.3f))
                Row(
                    Modifier
                        .weight(1f)
                        .focusRequester(bar)
                        .onFocusChanged { barFocused = it.isFocused }
                        .onKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                            when (e.key) {
                                Key.DirectionLeft -> { onChange(percent - Volume.STEP); true }
                                Key.DirectionRight -> { onChange(percent + Volume.STEP); true }
                                else -> false
                            }
                        }
                        .focusable()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (barFocused) Color.White else Color.White.copy(alpha = 0.12f))
                        .padding(11.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.5.dp),
                ) {
                    val on = percent / Volume.STEP
                    repeat(100 / Volume.STEP) { i ->
                        val lit = i < on
                        Box(
                            Modifier
                                .weight(1f)
                                .height(22.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    when {
                                        barFocused && lit -> Background
                                        barFocused -> Color(0xFFD5D9E0)
                                        lit -> Color.White
                                        else -> Color.White.copy(alpha = 0.15f)
                                    },
                                ),
                        )
                    }
                }
                Text("▶", fontSize = 20.sp, color = Color.White.copy(alpha = if (barFocused) 0.8f else 0.3f))
            }
            Spacer(Modifier.height(15.dp))
            Text(
                "$percent%", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "◀ ▶ a passi del 5% · cambia subito · Indietro chiude",
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f),
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
            Surface(
                onClick = { onChange(100) },
                modifier = Modifier.fillMaxWidth(),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = Color.White,
                    contentColor = Color.White,
                    focusedContentColor = Background,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
            ) {
                Text("Ripristina 100%", fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            }
        }
    }
}
