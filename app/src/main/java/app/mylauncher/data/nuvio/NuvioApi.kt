package app.mylauncher.data.nuvio

import android.os.Build
import app.mylauncher.BuildConfig
import app.mylauncher.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.SecureRandom

/**
 * Minimal client for the Nuvio account backend (a Supabase project): the TV QR login and the
 * read-only watch progress pull. The endpoints and payloads follow NuvioTV (GPL-3.0,
 * github.com/NuvioMedia/NuvioTV, core/auth/AuthManager.kt and core/sync/WatchProgressSyncService.kt).
 * The backend is not a documented public API, so every call here may start failing after a Nuvio
 * update; callers treat failures as "no data" and never break the home screen.
 */
class NuvioApi(private val store: NuvioStore) {

    class HttpError(val code: Int, body: String) : IOException("HTTP $code: ${body.take(200)}")

    val configured: Boolean get() = BASE_URL.isNotBlank() && ANON_KEY.isNotBlank()

    private val refreshLock = Mutex()

    data class DeviceLogin(
        val deviceCode: String,
        val userCode: String,
        val url: String,
        val nonce: String,
        val pollSeconds: Int,
    )

    suspend fun startDeviceLogin(): DeviceLogin {
        val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val params = JSONObject()
            .put("p_device_nonce", nonce)
            .put("p_redirect_base_url", DEVICE_LOGIN_URL)
            .put("p_device_type", "tv")
            .put("p_device_name", "My Launcher (${Build.MODEL})")
        val row = JSONArray(post("/rest/v1/rpc/start_device_login_session", params, token = null)).getJSONObject(0)
        return DeviceLogin(
            deviceCode = row.getString("device_code"),
            userCode = row.getString("user_code"),
            url = row.getString("verification_uri_complete"),
            nonce = nonce,
            pollSeconds = row.optInt("poll_interval_seconds", 3).coerceAtLeast(2),
        )
    }

    /** "pending", "approved", "expired", "used" or "cancelled". */
    suspend fun pollDeviceLogin(login: DeviceLogin): String {
        val params = JSONObject().put("p_code", login.deviceCode).put("p_device_nonce", login.nonce)
        return JSONArray(post("/rest/v1/rpc/poll_tv_login_session", params, token = null))
            .getJSONObject(0).getString("status").lowercase()
    }

    suspend fun exchangeDeviceLogin(login: DeviceLogin) {
        val payload = JSONObject().put("code", login.deviceCode).put("device_nonce", login.nonce)
        saveTokens(JSONObject(post("/functions/v1/tv-logins-exchange", payload, token = null)))
    }

    data class Profile(val index: Int, val name: String)

    suspend fun profiles(): List<Profile> {
        val rows = JSONArray(authorized("/rest/v1/rpc/sync_pull_profiles", JSONObject()))
        return (0 until rows.length()).map { rows.getJSONObject(it) }
            .map { Profile(it.getInt("profile_index"), it.optString("name")) }
            .sortedBy { it.index }
    }

    data class Progress(
        val contentId: String,
        val contentType: String,
        val videoId: String,
        val season: Int?,
        val episode: Int?,
        val position: Long,
        val duration: Long,
        val lastWatched: Long,
    )

    suspend fun watchProgress(profileId: Int, limit: Int = 40): List<Progress> {
        val params = JSONObject().put("p_profile_id", profileId).put("p_limit", limit)
        val rows = JSONArray(authorized("/rest/v1/rpc/sync_pull_watch_progress", params))
        return (0 until rows.length()).map { rows.getJSONObject(it) }.map {
            Progress(
                contentId = it.getString("content_id"),
                contentType = it.getString("content_type"),
                videoId = it.optString("video_id"),
                season = it.optIntOrNull("season"),
                episode = it.optIntOrNull("episode"),
                position = it.optLong("position"),
                duration = it.optLong("duration"),
                lastWatched = it.optLong("last_watched"),
            )
        }
    }

    // --- plumbing -------------------------------------------------------------------------

    private suspend fun authorized(path: String, body: JSONObject): String {
        val token = validAccessToken() ?: throw HttpError(401, "not signed in")
        return try {
            post(path, body, token)
        } catch (e: HttpError) {
            if (e.code != 401) throw e
            post(path, body, refresh(force = true) ?: throw e)
        }
    }

    private suspend fun validAccessToken(): String? {
        val s = store.session() ?: return null
        return if (s.expiresAtMs - System.currentTimeMillis() > 60_000) s.accessToken else refresh(force = false)
    }

    private suspend fun refresh(force: Boolean): String? = refreshLock.withLock {
        val s = store.session() ?: return null
        if (!force && s.expiresAtMs - System.currentTimeMillis() > 60_000) return s.accessToken
        try {
            val body = JSONObject().put("refresh_token", s.refreshToken)
            saveTokens(JSONObject(post("/auth/v1/token?grant_type=refresh_token", body, token = null)))
            store.session()?.accessToken
        } catch (e: HttpError) {
            // A rejected refresh token means the session is gone (signed out elsewhere, rotated).
            if (e.code in 400..499) store.clear()
            null
        }
    }

    private suspend fun saveTokens(json: JSONObject) {
        store.save(
            accessToken = json.getString("access_token"),
            refreshToken = json.getString("refresh_token"),
            expiresAtMs = System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000,
        )
    }

    private suspend fun post(path: String, body: JSONObject, token: String?): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(BASE_URL.trimEnd('/') + path)
            .header("apikey", ANON_KEY)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer ${token ?: ANON_KEY}")
            .post(body.toString().toRequestBody(Http.json))
            .build()
        Http.client.newCall(request).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw HttpError(r.code, text)
            text
        }
    }

    private companion object {
        val BASE_URL: String = BuildConfig.NUVIO_SUPABASE_URL
        val ANON_KEY: String = BuildConfig.NUVIO_SUPABASE_ANON_KEY
        const val DEVICE_LOGIN_URL = "https://nuvio.tv/link"
    }
}

private fun JSONObject.optIntOrNull(name: String): Int? = if (isNull(name) || !has(name)) null else optInt(name)
