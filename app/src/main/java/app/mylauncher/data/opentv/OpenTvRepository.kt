package app.mylauncher.data.opentv

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A channel tile: OpenTV's recent or favourite channel with what is on now. */
data class TvChannel(
    val id: Long,
    val name: String,
    val logo: String?,
    val number: Int?,
    val nowTitle: String?,
    val nowStart: Long?,
    val nowEnd: Long?,
    val recent: Boolean,
) {
    fun progressAt(now: Long): Float? {
        val s = nowStart ?: return null
        val e = nowEnd ?: return null
        if (e <= s) return null
        return ((now - s).toFloat() / (e - s)).coerceIn(0f, 1f)
    }
}

enum class MatchPhase { SCHEDULED, LIVE, FINISHED, OFF }

/** A Serie A match from OpenTV's Football tab. */
data class Match(
    val id: Long,
    /** live | today | finished | upcoming */
    val section: String,
    val phase: MatchPhase,
    val kickoff: Long,
    val minute: String?,
    val round: String?,
    val home: String,
    val homeCrest: String,
    val homeScore: Int?,
    val away: String,
    val awayCrest: String,
    val awayScore: Int?,
    val channels: List<String>,
    val highlights: String?,
)

data class OpenTvData(
    val channels: List<TvChannel> = emptyList(),
    val matches: List<Match> = emptyList(),
)

/**
 * Reads OpenTV's launcher provider (same signing key → signature permission) and builds the
 * intents that open a channel, a match or the Football tab in it.
 */
class OpenTvRepository(private val context: Context) {

    val installed: Boolean
        get() = runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess

    val permitted: Boolean
        get() = context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    suspend fun load(): OpenTvData = withContext(Dispatchers.IO) {
        val recent = channels("recent", recent = true)
        val favourites = channels("favourites", recent = false)
        val seen = recent.mapTo(HashSet()) { it.id }
        OpenTvData(
            channels = recent + favourites.filter { it.id !in seen },
            matches = matches(),
        )
    }

    private fun channels(kind: String, recent: Boolean): List<TvChannel> =
        query("channels/$kind") { c ->
            TvChannel(
                id = c.long("id") ?: return@query null,
                name = c.string("name").orEmpty(),
                logo = c.string("logo"),
                number = c.long("number")?.toInt(),
                nowTitle = c.string("now_title"),
                nowStart = c.long("now_start"),
                nowEnd = c.long("now_end"),
                recent = recent,
            )
        }

    private fun matches(): List<Match> =
        query("football") { c ->
            Match(
                id = c.long("id") ?: return@query null,
                section = c.string("section").orEmpty(),
                phase = runCatching { MatchPhase.valueOf(c.string("phase").orEmpty()) }.getOrDefault(MatchPhase.OFF),
                kickoff = c.long("kickoff") ?: 0L,
                minute = c.string("minute"),
                round = c.string("round"),
                home = c.string("home").orEmpty(),
                homeCrest = c.string("home_crest").orEmpty(),
                homeScore = c.long("home_score")?.toInt(),
                away = c.string("away").orEmpty(),
                awayCrest = c.string("away_crest").orEmpty(),
                awayScore = c.long("away_score")?.toInt(),
                channels = c.string("channels").orEmpty().split('|').filter { it.isNotBlank() },
                highlights = c.string("highlights"),
            )
        }

    private fun <T> query(path: String, row: (Cursor) -> T?): List<T> {
        if (!permitted) return emptyList()
        val uri = Uri.parse("content://$PACKAGE.launcher/$path")
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                buildList { while (c.moveToNext()) row(c)?.let(::add) }
            }.orEmpty()
        }.onFailure { Log.w(TAG, "query $path", it) }.getOrDefault(emptyList())
    }

    fun channelIntent(channel: TvChannel): Intent = main().putExtra("opentv.play_channel", channel.id)

    /** Live/coming: Watch automatically; finished: its highlights; otherwise the Football tab. */
    fun matchIntent(match: Match): Intent = main().putExtra("opentv.open", "football").apply {
        when {
            match.phase == MatchPhase.FINISHED && match.highlights != null -> {
                putExtra("opentv.match_id", match.id)
                putExtra("opentv.match_action", "highlights")
                putExtra("opentv.highlights_id", match.highlights)
            }
            match.phase != MatchPhase.FINISHED && match.channels.isNotEmpty() -> {
                putExtra("opentv.match_id", match.id)
                putExtra("opentv.match_action", "watch")
            }
        }
    }

    fun footballIntent(): Intent = main().putExtra("opentv.open", "football")

    fun openIntent(): Intent? = context.packageManager.getLeanbackLaunchIntentForPackage(PACKAGE)

    private fun main() = Intent(Intent.ACTION_MAIN)
        .setClassName(PACKAGE, "app.opentv.MainActivity")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun Cursor.index(name: String) = getColumnIndex(name).takeIf { it >= 0 && !isNull(it) }
    private fun Cursor.string(name: String) = index(name)?.let(::getString)
    private fun Cursor.long(name: String) = index(name)?.let(::getLong)

    companion object {
        const val PACKAGE = "app.opentv.sonny"
        const val PERMISSION = "$PACKAGE.permission.LAUNCHER"
        private const val TAG = "OpenTvRepository"
    }
}
