package app.mylauncher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject

data class Weather(val temperature: Int, val code: Int, val city: String) {
    /** WMO weather code to a glyph that every Fire OS font has. */
    val symbol: String
        get() = when (code) {
            0 -> "☀"
            1, 2 -> "⛅"
            3 -> "☁"
            45, 48 -> "🌫"
            in 51..67, in 80..82 -> "🌧"
            in 71..77, 85, 86 -> "❄"
            in 95..99 -> "⛈"
            else -> "☁"
        }
}

/** Current conditions and city search from Open-Meteo (free, no key). */
class WeatherRepository {

    suspend fun current(city: City): Weather? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=${city.latitude}&longitude=${city.longitude}" +
                "&current=temperature_2m,weather_code&timezone=auto"
            Http.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) return@runCatching null
                val current = JSONObject(r.body!!.string()).getJSONObject("current")
                Weather(
                    temperature = Math.round(current.getDouble("temperature_2m")).toInt(),
                    code = current.getInt("weather_code"),
                    city = city.name,
                )
            }
        }.getOrNull()
    }

    /** Cities matching [query], with region and country to tell namesakes apart. */
    suspend fun search(query: String): List<Pair<City, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
                .addQueryParameter("name", query.trim())
                .addQueryParameter("count", "8")
                .addQueryParameter("language", "it")
                .build()
            Http.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) return@runCatching emptyList()
                val results = JSONObject(r.body!!.string()).optJSONArray("results") ?: return@runCatching emptyList()
                (0 until results.length()).map { i ->
                    val o = results.getJSONObject(i)
                    val city = City(o.getString("name"), o.getDouble("latitude"), o.getDouble("longitude"))
                    val where = listOf(o.optString("admin1"), o.optString("country")).filter { it.isNotBlank() }
                    city to where.joinToString(", ")
                }
            }
        }.getOrDefault(emptyList())
    }
}
