package app.mylauncher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import app.mylauncher.data.opentv.Match
import app.mylauncher.data.opentv.MatchPhase
import app.mylauncher.data.opentv.TvChannel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val LiveRed = Color(0xFFFF4D5E)
private val Soft = Color(0xFFC9D1DE)
private val TileShape = RoundedCornerShape(12.dp)

private val hourFormat = SimpleDateFormat("HH:mm", Locale.ITALY)
private val dayFormat = SimpleDateFormat("EEE d", Locale.ITALY)

fun hour(millis: Long): String = hourFormat.format(Date(millis))

/** "Oggi 20:45", "Domani 15:00", "sab 2 · 18:00". */
fun kickoffLabel(millis: Long): String {
    val day = Calendar.getInstance().apply { timeInMillis = millis }
    val today = Calendar.getInstance()
    fun same(offset: Int) = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
        .let { it.get(Calendar.YEAR) == day.get(Calendar.YEAR) && it.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR) }
    val prefix = when {
        same(0) -> "Oggi"
        same(1) -> "Domani"
        same(-1) -> "Ieri"
        else -> dayFormat.format(Date(millis))
    }
    return "$prefix ${hour(millis)}"
}

fun Match.statusLabel(): String = when (phase) {
    MatchPhase.LIVE -> "● ${minute ?: "LIVE"}"
    MatchPhase.FINISHED -> if (highlights != null) "Finale · ▶ Highlights" else "Finale"
    MatchPhase.OFF -> "Rinviata"
    MatchPhase.SCHEDULED -> kickoffLabel(kickoff)
}

fun Match.actionLabel(): String = when {
    phase == MatchPhase.FINISHED && highlights != null -> "OK ▶ Highlights"
    phase == MatchPhase.LIVE && channels.isNotEmpty() -> "OK ▶ Guarda ora"
    phase == MatchPhase.SCHEDULED && channels.isNotEmpty() -> "OK ▶ Guarda in automatico"
    else -> "OK per aprire Calcio in MyTV"
}

@Composable
fun Crest(url: String, size: Dp) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context).data(url).size(128, 128).build(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(size),
    )
}

/**
 * A team's main colour, toned down so the crests read on it (same table as MyTV's tiles).
 * Teams not listed get a dark colour of their own from the name.
 */
private val teamColors = listOf(
    "atalanta" to 0xFF1C4E8F, "bologna" to 0xFF1A2F48, "cagliari" to 0xFF8C1A2B, "como" to 0xFF1B3C8C,
    "cremonese" to 0xFF8E1B2C, "empoli" to 0xFF0E4FA0, "fiorentina" to 0xFF4B2A85, "frosinone" to 0xFF1D4C9C,
    "genoa" to 0xFF7A1424, "verona" to 0xFF113A78, "inter" to 0xFF00487A, "juventus" to 0xFF1E1E1E,
    "lazio" to 0xFF4C87B9, "lecce" to 0xFF9C8A10, "milan" to 0xFFB0101A, "monza" to 0xFFB5121B,
    "napoli" to 0xFF0E6E9E, "palermo" to 0xFFB5527A, "parma" to 0xFF1E3D7B, "pisa" to 0xFF1A2B5C,
    "roma" to 0xFF8E1F2F, "salernitana" to 0xFF6E1B1F, "sampdoria" to 0xFF1B4A9E, "sassuolo" to 0xFF0F6B3A,
    "spezia" to 0xFF2A2A2A, "torino" to 0xFF7A1A08, "udinese" to 0xFF2B2B2B, "venezia" to 0xFF0B5A3C,
)

private fun teamColor(name: String): Color {
    val n = name.lowercase()
    teamColors.firstOrNull { n.contains(it.first) }?.let { return Color(it.second) }
    val hue = ((n.hashCode() and 0x7fffffff) % 360).toFloat()
    return Color.hsv(hue, 0.55f, 0.38f)
}

/** Paints the box in two colours split on a slant: home left, away right. */
private fun Modifier.split(home: Color, away: Color): Modifier = drawBehind {
    drawRect(away)
    val path = Path().apply {
        moveTo(0f, 0f)
        lineTo(size.width * 0.58f, 0f)
        lineTo(size.width * 0.42f, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(path, home)
}

private val weekdayFormat = SimpleDateFormat("EEE", Locale.ITALY)

/** The tile's chip: "OGGI 21:00", "GIO 18:45", "SAB 21 15:00", the live minute, Highlights. */
private fun Match.chipLabel(): String = when (phase) {
    MatchPhase.LIVE -> minute ?: "LIVE"
    MatchPhase.FINISHED -> if (highlights != null) "▶ HIGHLIGHTS" else "FINALE"
    MatchPhase.OFF -> "RINVIATA"
    MatchPhase.SCHEDULED -> {
        val now = Calendar.getInstance()
        val day = Calendar.getInstance().apply { timeInMillis = kickoff }
        val days = ((day.apply { set(Calendar.HOUR_OF_DAY, 12) }.timeInMillis -
            (now.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 12) }.timeInMillis) / 86_400_000L).toInt()
        val label = when {
            days == 0 -> "Oggi"
            days in 1..6 -> weekdayFormat.format(Date(kickoff)).trimEnd('.')
            else -> dayFormat.format(Date(kickoff)).replace(".", "")
        }
        label.uppercase() + " " + hour(kickoff)
    }
}

/**
 * A game as MyTV draws it: the two teams' colours split on a slant, the crests big, a chip
 * with day and time (or the live minute) top left. The names are in the hero above.
 */
@Composable
fun MatchCard(match: Match, modifier: Modifier, onClick: () -> Unit) {
    val live = match.phase == MatchPhase.LIVE
    val highlight = match.phase == MatchPhase.FINISHED && match.highlights != null
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = CardDefaults.shape(TileShape),
        scale = CardDefaults.scale(focusedScale = 1.08f),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = TileShape)),
        colors = CardDefaults.colors(containerColor = Color(0xFF1B2433)),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().split(teamColor(match.home), teamColor(match.away))) {
            val crest = (maxWidth.value * 0.26f).coerceIn(32f, 52f).dp
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.4f)))))
            Row(
                Modifier.fillMaxSize().padding(horizontal = maxWidth * 0.11f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Crest(match.homeCrest, crest)
                Crest(match.awayCrest, crest)
            }
            Text(
                match.chipLabel(),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (highlight) Color.Black else Color.White,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when {
                            live -> Color(0xFFE50914)
                            highlight -> Color.White.copy(alpha = 0.92f)
                            else -> Color.Black.copy(alpha = 0.65f)
                        },
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
fun ChannelCard(channel: TvChannel, now: Long, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = CardDefaults.shape(TileShape),
        scale = CardDefaults.scale(focusedScale = 1.08f),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = TileShape)),
        colors = CardDefaults.colors(containerColor = Color(0xFF1B2433)),
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    if (channel.logo != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(channel.logo).size(240, 120).build(),
                            contentDescription = channel.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 4.dp),
                        )
                    } else {
                        Text(channel.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2)
                    }
                }
                Text(
                    channel.nowTitle ?: channel.name,
                    fontSize = 11.sp, color = Soft, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            channel.progressAt(now)?.let { p ->
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.2f)))
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(p).height(3.dp).background(Accent))
            }
        }
    }
}

@Composable
fun MatchHero(match: Match) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Crest(match.homeCrest, 64.dp)
            Spacer(Modifier.width(14.dp))
            Text(match.home, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            Spacer(Modifier.width(18.dp))
            Text("vs", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.width(18.dp))
            Text(match.away, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            Spacer(Modifier.width(14.dp))
            Crest(match.awayCrest, 64.dp)
        }
        Spacer(Modifier.height(10.dp))
        val live = match.phase == MatchPhase.LIVE
        val round = match.round?.let { " · Serie A, giornata $it" } ?: " · Serie A"
        Text(
            match.statusLabel() + round,
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            color = if (live) LiveRed else Accent,
        )
        if (match.channels.isNotEmpty() && match.phase != MatchPhase.FINISHED) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                match.channels.take(4).forEach { name ->
                    Text(
                        name, fontSize = 12.sp, color = Color.White,
                        modifier = Modifier.background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(match.actionLabel(), fontSize = 14.sp, color = Muted)
    }
}

@Composable
fun ChannelHero(channel: TvChannel) {
    Column(Modifier.fillMaxWidth(0.6f)) {
        Text(channel.name, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        if (channel.nowTitle != null) {
            val span = if (channel.nowStart != null && channel.nowEnd != null) " · ${hour(channel.nowStart)} – ${hour(channel.nowEnd)}" else ""
            Text("In onda: ${channel.nowTitle}$span", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Accent, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
        }
        Text((if (channel.recent) "Visto di recente" else "Preferito") + " · OK per guardare", fontSize = 14.sp, color = Muted)
    }
}
