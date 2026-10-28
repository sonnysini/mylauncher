package app.mylauncher.data.nuvio

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.nuvioStore by preferencesDataStore(name = "nuvio")

/** Nuvio session tokens and the chosen profile, in the app's private storage. */
class NuvioStore(private val context: Context) {

    data class Session(val accessToken: String, val refreshToken: String, val expiresAtMs: Long)

    val signedIn: Flow<Boolean> = context.nuvioStore.data.map { it[REFRESH] != null }
    val profileId: Flow<Int> = context.nuvioStore.data.map { it[PROFILE] ?: 1 }

    suspend fun session(): Session? {
        val p = context.nuvioStore.data.first()
        return Session(p[ACCESS] ?: return null, p[REFRESH] ?: return null, p[EXPIRES] ?: 0)
    }

    suspend fun save(accessToken: String, refreshToken: String, expiresAtMs: Long) {
        context.nuvioStore.edit {
            it[ACCESS] = accessToken
            it[REFRESH] = refreshToken
            it[EXPIRES] = expiresAtMs
        }
    }

    suspend fun setProfile(id: Int) {
        context.nuvioStore.edit { it[PROFILE] = id }
    }

    suspend fun clear() {
        context.nuvioStore.edit { it.clear() }
    }

    private companion object {
        val ACCESS = stringPreferencesKey("access_token")
        val REFRESH = stringPreferencesKey("refresh_token")
        val EXPIRES = longPreferencesKey("expires_at")
        val PROFILE = intPreferencesKey("profile_id")
    }
}
