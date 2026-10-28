package app.mylauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/**
 * Modal list of choices drawn inside the activity window.
 *
 * Opened by holding OK, so the repeats and the KeyUp of that same press land here once focus has
 * moved: without the [armed] guard the first option would be clicked on release. Nothing reacts to
 * OK until a fresh press starts.
 */
@Composable
fun OptionsOverlay(
    title: String,
    options: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit,
    /** Option that takes the focus; changing it moves the focus (lists that reorder themselves). */
    focusIndex: Int = 0,
) {
    val first = remember { FocusRequester() }
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(focusIndex, options.size) { runCatching { first.requestFocus() } }
    BackHandler(onBack = onDismiss)

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .onPreviewKeyEvent { e ->
                    val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                    when {
                        armed || !ok -> false
                        e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0 -> { armed = true; false }
                        e.type == KeyEventType.KeyUp -> { armed = true; true }
                        else -> true
                    }
                }
                .trapFocus()
                .width(420.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF161D2A))
                .padding(18.dp),
        ) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(options.size) { i ->
                    val (label, action) = options[i]
                    Surface(
                        onClick = action,
                        modifier = Modifier.fillMaxWidth().let { if (i == focusIndex.coerceIn(options.indices)) it.focusRequester(first) else it },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.Transparent,
                            focusedContainerColor = Color.White,
                            contentColor = Color.White,
                            focusedContentColor = Background,
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                    ) {
                        Text(label, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                    }
                }
            }
        }
    }
}
