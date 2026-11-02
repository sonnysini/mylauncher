package app.mylauncher.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/**
 * Self-update from the GitHub releases, the same way OpenTV does it: the launcher is sideloaded,
 * so nothing else on the Fire would ever tell it a new version is out.
 *
 * The check is silent on failure (no network, rate limit, no release): it only ever answers
 * "there is a newer one" or nothing. Installing goes through the system installer screen.
 */
class Updater(private val context: Context, private val currentVersion: String) {

    data class Update(val version: String, val notes: String, val apkUrl: String, val apkBytes: Long)

    /** The newer release, or null when up to date or the answer is unknown. */
    suspend fun check(): Update? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$REPO/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "MyLauncher")
                .build()
            Http.client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return@runCatching null
                val release = JSONObject(r.body!!.string())
                if (release.optBoolean("draft") || release.optBoolean("prerelease")) return@runCatching null
                val tag = release.getString("tag_name")
                if (!isNewer(tag, currentVersion)) return@runCatching null
                val assets = release.getJSONArray("assets")
                val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                    .filter { it.getString("name").endsWith(".apk") }
                    // The fixed-name copy first: the versioned one is the same file.
                    .sortedBy { if (it.getString("name") == "mylauncher.apk") 0 else 1 }
                    .firstOrNull() ?: return@runCatching null
                Update(
                    version = tag.trimStart('v', 'V'),
                    notes = notesFor(release.optString("body")),
                    apkUrl = apk.getString("browser_download_url"),
                    apkBytes = apk.optLong("size"),
                )
            }
        }.getOrNull()
    }

    /** Downloads the APK into the cache and opens the system installer on it. */
    suspend fun install(update: Update, progress: (Float) -> Unit) {
        val apk = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val out = File(dir, "mylauncher-update.apk")
            val request = Request.Builder().url(update.apkUrl).header("User-Agent", "MyLauncher").build()
            Http.client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code}")
                val body = r.body ?: error("empty")
                val total = update.apkBytes.takeIf { it > 0 } ?: body.contentLength()
                body.byteStream().use { input ->
                    out.outputStream().use { sink ->
                        val buffer = ByteArray(64 * 1024)
                        var written = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            sink.write(buffer, 0, read)
                            written += read
                            if (total > 0) progress(written.toFloat() / total)
                        }
                    }
                }
            }
            out
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            },
        )
    }

    companion object {
        const val REPO = "sonnysini/mylauncher"

        /** True when [tag] is strictly higher than [current] (`v0.7.0` vs `0.6.0`). */
        fun isNewer(tag: String, current: String): Boolean {
            val a = parts(tag)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }

        private fun parts(v: String) =
            v.trim().trimStart('v', 'V').split('.', '-', '+').map { p -> p.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

        /** The release body is the CHANGELOG section: keep its bullets, flattened for a plain dialog. */
        fun notesFor(body: String): String =
            body.lines()
                .map { it.trim() }
                .filter { it.startsWith("- ") || it.startsWith("* ") }
                .map { "• " + it.drop(2).replace("**", "").replace("`", "") }
                .take(6)
                .joinToString("\n")
    }
}
