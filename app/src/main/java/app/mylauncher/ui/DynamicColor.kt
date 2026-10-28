package app.mylauncher.ui

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val colorCache = LruCache<Any, Color>(64)

/**
 * The most characteristic colour of [source] (an image URL or an app's banner/icon), for the glow
 * behind the home. Null until known; images are decoded tiny, so this stays cheap on the stick.
 */
@Composable
fun rememberImageColor(source: Any?): Color? {
    val context = LocalContext.current
    var color by remember(source) { mutableStateOf(source?.let { colorCache.get(it) }) }
    LaunchedEffect(source) {
        if (source == null || color != null) return@LaunchedEffect
        val bitmap = when (source) {
            is ImageBitmap -> source.asAndroidBitmap()
            is String -> {
                val request = ImageRequest.Builder(context).data(source).size(64).allowHardware(false).build()
                (context.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap(64, 64, Bitmap.Config.ARGB_8888)
            }
            else -> null
        } ?: return@LaunchedEffect
        val found = withContext(Dispatchers.Default) { pick(Palette.from(bitmap).maximumColorCount(12).generate()) }
        if (found != null) {
            colorCache.put(source, found)
            color = found
        }
    }
    return color
}

/** Vivid colours first: a muted or near-black dominant would make no glow at all. */
private fun pick(palette: Palette): Color? {
    val swatch = palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.darkVibrantSwatch
        ?: palette.dominantSwatch ?: return null
    return Color(swatch.rgb)
}
