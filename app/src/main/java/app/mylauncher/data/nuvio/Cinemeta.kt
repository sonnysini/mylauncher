package app.mylauncher.data.nuvio

import app.mylauncher.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** Titles, artwork and episode lists for IMDb ids, from Stremio's public Cinemeta addon. */
class Cinemeta {

    data class Episode(val season: Int, val episode: Int, val title: String, val overview: String, val thumbnail: String?)

    data class Meta(
        val name: String,
        val description: String,
        val background: String?,
        val poster: String?,
        val logo: String?,
        val year: String?,
        val episodes: List<Episode>,
    )

    private val cache = ConcurrentHashMap<String, Meta>()

    suspend fun meta(type: String, id: String): Meta? {
        val key = "$type/$id"
        cache[key]?.let { return it }
        if (!id.startsWith("tt")) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url("https://v3-cinemeta.strem.io/meta/$type/$id.json").build()
                Http.client.newCall(request).execute().use { r ->
                    if (!r.isSuccessful) return@runCatching null
                    parse(JSONObject(r.body!!.string()).getJSONObject("meta"))
                }
            }.getOrNull()?.also { cache[key] = it }
        }
    }

    private fun parse(m: JSONObject): Meta {
        val videos = m.optJSONArray("videos")
        val episodes = if (videos == null) emptyList() else (0 until videos.length()).map { videos.getJSONObject(it) }
            .filter { it.has("season") && it.has("episode") }
            .map {
                Episode(
                    season = it.getInt("season"),
                    episode = it.getInt("episode"),
                    title = it.optString("name").ifBlank { it.optString("title") },
                    overview = it.optString("overview").ifBlank { it.optString("description") },
                    thumbnail = it.optString("thumbnail").takeIf(String::isNotBlank),
                )
            }
            .sortedWith(compareBy({ it.season }, { it.episode }))
        return Meta(
            name = m.optString("name"),
            description = m.optString("description"),
            background = m.optString("background").takeIf(String::isNotBlank),
            poster = m.optString("poster").takeIf(String::isNotBlank),
            logo = m.optString("logo").takeIf(String::isNotBlank),
            year = m.optString("releaseInfo").takeIf(String::isNotBlank),
            episodes = episodes,
        )
    }
}
