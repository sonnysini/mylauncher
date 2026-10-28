package app.mylauncher.ui

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
import androidx.compose.ui.graphics.Color
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
        Text("My Launcher", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.weight(1f))
        if (weather != null) {
            Text("${weather.symbol}  ${weather.temperature}°", fontSize = 20.sp, color = Color.White)
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
