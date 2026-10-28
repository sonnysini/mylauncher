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

class Prefs(private val context: Context) {

    val layout: Flow<AppLayout> = context.dataStore.data.map { p ->
        AppLayout(
            order = p[ORDER]?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList(),
            hidden = p[HIDDEN] ?: emptySet(),
        )
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

    private companion object {
        val ORDER = stringPreferencesKey("app_order")
        val HIDDEN = stringSetPreferencesKey("app_hidden")
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
