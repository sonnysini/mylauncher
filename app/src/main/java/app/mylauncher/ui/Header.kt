package app.mylauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.mylauncher.data.Weather
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun Header(weather: Weather?, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Logo()
        Spacer(Modifier.weight(1f))
        if (weather != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text("${weather.symbol}  ${weather.temperature}°", fontSize = 20.sp, color = Color.White)
                Text(weather.city, fontSize = 12.sp, color = Muted, maxLines = 1)
            }
            Spacer(Modifier.width(28.dp))
        }
        Clock()
        Spacer(Modifier.width(20.dp))
        Surface(
            onClick = onOpenSettings,
            modifier = Modifier.size(44.dp),
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.White,
                contentColor = Color.White,
                focusedContentColor = Background,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = "Impostazioni", modifier = Modifier.padding(9.dp))
        }
    }
}

@Composable
private fun Clock() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L)
        }
    }
    val time = remember(now) { SimpleDateFormat("HH:mm", Locale.ITALIAN).format(Date(now)) }
    val date = remember(now) { SimpleDateFormat("EEE d MMM", Locale.ITALIAN).format(Date(now)) }
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
        Text(time, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Text(date, fontSize = 12.sp, color = Muted)
    }
}

/** The app icon's tiles (three blue, the focused one white) beside a two-tone wordmark, as in the banner. */
@Composable
private fun Logo() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(34.dp)) {
            val gap = size.width * 0.1f
            val tile = (size.width - gap) / 2
            val radius = CornerRadius(tile * 0.24f)
            listOf(Offset(0f, 0f), Offset(tile + gap, 0f), Offset(0f, tile + gap)).forEach {
                drawRoundRect(Accent, it, Size(tile, tile), radius)
            }
            // Slightly larger, like a focused tile.
            val grow = tile * 0.08f
            drawRoundRect(
                Color.White,
                Offset(tile + gap - grow, tile + gap - grow),
                Size(tile + 2 * grow, tile + 2 * grow),
                radius,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Color.White)) { append("My ") }
                withStyle(SpanStyle(color = Accent)) { append("Launcher") }
            },
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
