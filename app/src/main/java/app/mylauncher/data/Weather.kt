package app.mylauncher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class Weather(val temperature: Int, val code: Int) {
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

/** Current conditions from Open-Meteo (free, no key). Verona by default. */
class WeatherRepository(
    private val latitude: Double = 45.4384,
    private val longitude: Double = 10.9916,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun current(): Weather? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,weather_code&timezone=auto"
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) return@runCatching null
                val current = JSONObject(r.body!!.string()).getJSONObject("current")
                Weather(
                    temperature = Math.round(current.getDouble("temperature_2m")).toInt(),
                    code = current.getInt("weather_code"),
                )
            }
        }.getOrNull()
    }
}
