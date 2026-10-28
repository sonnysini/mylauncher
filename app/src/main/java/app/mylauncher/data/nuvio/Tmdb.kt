package app.mylauncher.data.nuvio

import app.mylauncher.BuildConfig
import app.mylauncher.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** Italian titles and plots from TMDB. Cinemeta stays the source for artwork and episode lists. */
class Tmdb {

    data class Texts(val title: String?, val overview: String?)

    val configured: Boolean get() = KEY.isNotBlank()

    private val ids = ConcurrentHashMap<String, Int>()
    private val texts = ConcurrentHashMap<String, Texts>()

    suspend fun title(type: String, imdbId: String): Texts? {
        val id = tmdbId(type, imdbId) ?: return null
        return cached("${type}/$id") { get(if (type == "series") "tv/$id" else "movie/$id") }
    }

    suspend fun episode(imdbId: String, season: Int, episode: Int): Texts? {
        val id = tmdbId("series", imdbId) ?: return null
        return cached("tv/$id/$season/$episode") { get("tv/$id/season/$season/episode/$episode") }
    }

    private suspend fun tmdbId(type: String, imdbId: String): Int? {
        if (!configured || !imdbId.startsWith("tt")) return null
        ids["$type/$imdbId"]?.let { return it }
        val json = request("find/$imdbId", "&external_source=imdb_id") ?: return null
        val results = json.optJSONArray(if (type == "series") "tv_results" else "movie_results")
        val id = results?.optJSONObject(0)?.optInt("id")?.takeIf { it > 0 } ?: return null
        ids["$type/$imdbId"] = id
        return id
    }

    private suspend fun get(path: String): Texts? = request(path)?.let { j ->
        Texts(
            title = (j.optString("title").ifBlank { j.optString("name") }).takeIf(String::isNotBlank),
            overview = j.optString("overview").takeIf(String::isNotBlank),
        )
    }

    private suspend fun cached(key: String, load: suspend () -> Texts?): Texts? =
        texts[key] ?: load()?.also { texts[key] = it }

    private suspend fun request(path: String, extra: String = ""): JSONObject? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.themoviedb.org/3/$path?api_key=$KEY&language=it-IT$extra"
            Http.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (r.isSuccessful) JSONObject(r.body!!.string()) else null
            }
        }.getOrNull()
    }

    private companion object {
        val KEY: String = BuildConfig.TMDB_API_KEY
    }
}
