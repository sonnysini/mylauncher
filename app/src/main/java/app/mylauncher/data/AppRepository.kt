package app.mylauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A launchable app. [banner] is the 16:9 TV banner when the app ships one, else null. [system]
 * apps came with Fire OS and cannot be uninstalled.
 */
data class AppEntry(
    val packageName: String,
    val label: String,
    val component: ComponentName,
    val leanback: Boolean,
    val system: Boolean,
    val banner: ImageBitmap?,
    val icon: ImageBitmap,
)

class AppRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    /**
     * Every launchable app except this launcher. TV (leanback) entries win over phone ones for the
     * same package; sideloaded phone-only apps still show up so nothing is unreachable.
     */
    suspend fun loadApps(): List<AppEntry> = withContext(Dispatchers.IO) {
        val leanback = query(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val phone = query(Intent.CATEGORY_LAUNCHER)
        val byPackage = LinkedHashMap<String, Pair<ResolveInfo, Boolean>>()
        leanback.forEach { byPackage[it.activityInfo.packageName] = it to true }
        phone.forEach { byPackage.putIfAbsent(it.activityInfo.packageName, it to false) }
        byPackage.remove(context.packageName)
        byPackage.values.mapNotNull { (info, isTv) -> runCatching { toEntry(info, isTv) }.getOrNull() }
            .sortedBy { it.label.lowercase() }
    }

    fun launchIntent(app: AppEntry): Intent =
        Intent(Intent.ACTION_MAIN)
            .addCategory(if (app.leanback) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    /** System confirmation dialog for removing [app]. */
    fun uninstallIntent(app: AppEntry): Intent =
        Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * The app's page in the Fire settings, with Force stop and Clear cache: a launcher cannot do
     * either itself, both need permissions Fire OS keeps for system apps.
     */
    fun detailsIntent(app: AppEntry): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun query(category: String): List<ResolveInfo> =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)

    private fun toEntry(info: ResolveInfo, isTv: Boolean): AppEntry {
        val ai = info.activityInfo
        // Banners are 320x180; drawn at card size, so a fixed bitmap keeps memory predictable on
        // a 1 GB stick (a 4K-sized drawable would otherwise be rasterised at full size).
        val banner = (ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm))
            ?.toBitmap(BANNER_W, BANNER_H, Bitmap.Config.ARGB_8888)?.asImageBitmap()
        val icon = ai.loadIcon(pm).toFittedBitmap(ICON_PX).asImageBitmap()
        return AppEntry(
            packageName = ai.packageName,
            label = info.loadLabel(pm).toString(),
            component = ComponentName(ai.packageName, ai.name),
            leanback = isTv,
            system = ai.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            banner = banner,
            icon = icon,
        )
    }

    /**
     * The icon in a square bitmap at its own proportions: some Fire icons are not square, and
     * toBitmap(size, size) would stretch them.
     */
    private fun Drawable.toFittedBitmap(px: Int): Bitmap {
        val w = intrinsicWidth.takeIf { it > 0 } ?: px
        val h = intrinsicHeight.takeIf { it > 0 } ?: px
        val scale = px.toFloat() / maxOf(w, h)
        val dw = (w * scale).toInt().coerceAtLeast(1)
        val dh = (h * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val left = (px - dw) / 2
        val top = (px - dh) / 2
        val old = copyBounds()
        setBounds(left, top, left + dw, top + dh)
        draw(Canvas(bitmap))
        bounds = old
        return bitmap
    }

    private companion object {
        const val BANNER_W = 320
        const val BANNER_H = 180
        const val ICON_PX = 192
    }
}
