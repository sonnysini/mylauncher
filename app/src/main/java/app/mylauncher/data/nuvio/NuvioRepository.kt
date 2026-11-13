package app.mylauncher.data.nuvio

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** One tile of "Continua a guardare". */
data class ContinueItem(
    val contentId: String,
    val type: String,
    val title: String,
    /** "S2 E5 · Episode title", "Prossimo: S2 E6 · …" or the year for films. */
    val subtitle: String,
    val description: String,
    /** 16:9 artwork for the tile: episode still or the title's backdrop. */
    val image: String?,
    val backdrop: String?,
    val logo: String?,
    /** 0..1, null for an up-next episode or one barely started. */
    val progress: Float?,
    val minutesLeft: Int?,
    /** What "play" opens in Nuvio: the film id, or "tt…:season:episode". */
    val videoId: String,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeTitle: String? = null,
    val poster: String? = null,
)

class NuvioRepository(private val context: Context) {

    val store = NuvioStore(context)
    val api = NuvioApi(store)
    private val cinemeta = Cinemeta()
    private val tmdb = Tmdb()

    val signedIn: Flow<Boolean> = store.signedIn

    suspend fun continueWatching(): List<ContinueItem> = coroutineScope {
        val profile = store.profileId.first()
        // Latest entry per title: series keep one progress row per episode.
        val latest = api.watchProgress(profile)
            .sortedByDescending { it.lastWatched }
            .distinctBy { it.contentId }
            .take(MAX_ITEMS)
        latest.map { async { toItem(it) } }.awaitAll().filterNotNull()
    }

    /**
     * Drops the title from "Continua a guardare" here and in Nuvio: deletes every progress row
     * of it on the server, with the keys Nuvio uses ("tt…" and "tt…_s1e2").
     */
    suspend fun remove(item: ContinueItem) {
        val profile = store.profileId.first()
        val keys = api.watchProgress(profile, limit = REMOVE_LOOKUP)
            .filter { it.contentId == item.contentId }
            .map { p -> if (p.season != null && p.episode != null) "${p.contentId}_s${p.season}e${p.episode}" else p.contentId }
            .plus(item.contentId)
            .distinct()
        api.deleteProgress(profile, keys)
    }

    private suspend fun toItem(p: NuvioApi.Progress): ContinueItem? {
        val fraction = if (p.duration > 0) (p.position.toFloat() / p.duration).coerceIn(0f, 1f) else 0f
        val finished = fraction >= FINISHED
        val meta = cinemeta.meta(p.contentType, p.contentId)
        val isSeries = p.contentType == "series" && p.season != null && p.episode != null

        // Italian where TMDB has it; Cinemeta (English) fills the gaps.
        val italian = tmdb.title(p.contentType, p.contentId)
        val title = italian?.title ?: meta?.name ?: p.contentId

        if (!isSeries) {
            if (finished) return null
            return ContinueItem(
                contentId = p.contentId, type = p.contentType,
                title = title,
                subtitle = meta?.year.orEmpty(),
                description = italian?.overview ?: meta?.description.orEmpty(),
                image = meta?.background ?: meta?.poster, backdrop = meta?.background, logo = meta?.logo,
                progress = fraction.takeIf { it >= MIN_PROGRESS }, minutesLeft = minutesLeft(p),
                videoId = p.videoId.ifBlank { p.contentId }, poster = meta?.poster,
            )
        }

        val episodes = meta?.episodes.orEmpty()
        val index = episodes.indexOfFirst { it.season == p.season && it.episode == p.episode }
        // A finished episode turns into "next episode"; at the end of what is out, the tile goes.
        val shown = if (finished) episodes.getOrNull(index + 1)?.takeIf { index >= 0 } ?: return null
        else episodes.getOrNull(index)
        val season = shown?.season ?: p.season!!
        val episode = shown?.episode ?: p.episode!!
        val italianEpisode = tmdb.episode(p.contentId, season, episode)
        val episodeTitle = italianEpisode?.title ?: shown?.title?.takeIf(String::isNotBlank)
        val label = "S$season E$episode" + (episodeTitle?.let { " · $it" } ?: "")
        return ContinueItem(
            contentId = p.contentId, type = p.contentType,
            title = title,
            subtitle = if (finished) "Prossimo: $label" else label,
            description = italianEpisode?.overview
                ?: shown?.overview?.takeIf(String::isNotBlank)
                ?: italian?.overview ?: meta?.description.orEmpty(),
            image = shown?.thumbnail ?: meta?.background, backdrop = meta?.background, logo = meta?.logo,
            progress = if (finished) null else fraction.takeIf { it >= MIN_PROGRESS },
            minutesLeft = if (finished) null else minutesLeft(p),
            videoId = if (!finished && p.videoId.isNotBlank()) p.videoId else "${p.contentId}:$season:$episode",
            season = season, episode = episode, episodeTitle = episodeTitle, poster = meta?.poster,
        )
    }

    private fun minutesLeft(p: NuvioApi.Progress): Int? =
        if (p.duration > 0) ((p.duration - p.position) / 60_000).toInt().coerceAtLeast(1) else null

    /**
     * Opens Nuvio straight on the source list of the film or episode, the same intent Nuvio puts
     * behind its own Android TV "Watch next" row: with "Riutilizza ultimo link" or autoplay on in
     * Nuvio it starts playing by itself. Falls back to the detail page if Nuvio can't take it.
     */
    fun openIntent(item: ContinueItem): Intent {
        val play = Intent(Intent.ACTION_VIEW)
            .setClassName(NUVIO_PACKAGE, NUVIO_ACTIVITY)
            .putExtra("launchMode", "stream")
            .putExtra("contentId", item.contentId)
            .putExtra("contentType", item.type)
            .putExtra("videoId", item.videoId)
            .putExtra("name", item.title)
            .apply {
                item.poster?.let { putExtra("poster", it) }
                item.backdrop?.let { putExtra("backdrop", it) }
                item.logo?.let { putExtra("logo", it) }
                item.season?.let { putExtra("season", it) }
                item.episode?.let { putExtra("episode", it) }
                item.episodeTitle?.let { putExtra("episodeTitle", it) }
            }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (play.resolveActivity(context.packageManager) != null) return play
        return Intent(Intent.ACTION_VIEW, Uri.parse("nuvio://meta?type=${item.type}&id=${Uri.encode(item.contentId)}"))
            .setPackage(NUVIO_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    companion object {
        const val NUVIO_PACKAGE = "com.nuvio.tv"
        private const val NUVIO_ACTIVITY = "com.nuvio.tv.MainActivity"
        private const val MAX_ITEMS = 15
        /** Rows read to find every episode of a title being removed. */
        private const val REMOVE_LOOKUP = 500
        private const val FINISHED = 0.92f
        /** Below this an episode was only just opened: an empty bar reads as broken. */
        private const val MIN_PROGRESS = 0.02f
    }
}
