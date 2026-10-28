package app.mylauncher.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val Background = Color(0xFF0B0F17)
val Accent = Color(0xFF4F8CFF)
val Muted = Color(0xFF8A94A6)

@Composable
fun LauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            background = Background,
            surface = Color(0xFF161D2A),
            onSurface = Color.White,
            onSurfaceVariant = Muted,
        ),
        content = content,
    )
}
