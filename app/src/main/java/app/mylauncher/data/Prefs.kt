package app.mylauncher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "launcher")

/** User layout of the App row: manual order and hidden packages. */
data class AppLayout(val order: List<String> = emptyList(), val hidden: Set<String> = emptySet())

/** The rows of the home, in their default order. */
enum class HomeRow(val label: String) {
    NUVIO("Continua a guardare"),
    CALCIO("Calcio"),
    OPENTV("OpenTV"),
    APPS("App"),
}

/** Where the weather in the header comes from. */
data class City(val name: String, val latitude: Double, val longitude: Double) {
    companion object {
        val DEFAULT = City("Verona", 45.4384, 10.9916)
    }
}

class Prefs(private val context: Context) {

    val layout: Flow<AppLayout> = context.dataStore.data.map { p ->
        AppLayout(
            order = p[ORDER]?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList(),
            hidden = p[HIDDEN] ?: emptySet(),
        )
    }

    val rows: Flow<List<HomeRow>> = context.dataStore.data.map { p ->
        val saved = p[ROWS]?.split('\n')?.mapNotNull { name -> HomeRow.entries.firstOrNull { it.name == name } }.orEmpty()
        // Rows added in a later version go at the end.
        saved + HomeRow.entries.filter { it !in saved }
    }

    val city: Flow<City> = context.dataStore.data.map { p ->
        p[CITY]?.split('\n')?.takeIf { it.size == 3 }?.let { (name, lat, lon) ->
            City(name, lat.toDoubleOrNull() ?: return@let null, lon.toDoubleOrNull() ?: return@let null)
        } ?: City.DEFAULT
    }

    suspend fun setOrder(packages: List<String>) {
        context.dataStore.edit { it[ORDER] = packages.joinToString("\n") }
    }

    suspend fun setHidden(packageName: String, hidden: Boolean) {
        context.dataStore.edit {
            val current = it[HIDDEN] ?: emptySet()
            it[HIDDEN] = if (hidden) current + packageName else current - packageName
        }
    }

    suspend fun setRows(rows: List<HomeRow>) {
        context.dataStore.edit { it[ROWS] = rows.joinToString("\n") { r -> r.name } }
    }

    suspend fun setCity(city: City) {
        context.dataStore.edit { it[CITY] = "${city.name}\n${city.latitude}\n${city.longitude}" }
    }

    private companion object {
        val ORDER = stringPreferencesKey("app_order")
        val HIDDEN = stringSetPreferencesKey("app_hidden")
        val ROWS = stringPreferencesKey("row_order")
        val CITY = stringPreferencesKey("weather_city")
    }
}

/**
 * Applies a saved order: known packages keep their saved place, new ones are appended in the
 * (alphabetical) order they arrive in.
 */
fun <T> List<T>.ordered(order: List<String>, key: (T) -> String): List<T> {
    val rank = order.withIndex().associate { (i, p) -> p to i }
    val (known, fresh) = partition { key(it) in rank }
    return known.sortedBy { rank.getValue(key(it)) } + fresh
}
