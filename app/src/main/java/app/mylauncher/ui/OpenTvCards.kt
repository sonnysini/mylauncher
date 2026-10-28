package app.mylauncher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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

fun Match.scoreLabel(): String? =
    if (phase == MatchPhase.LIVE || phase == MatchPhase.FINISHED) "${homeScore ?: 0} – ${awayScore ?: 0}" else null

fun Match.actionLabel(): String = when {
    phase == MatchPhase.FINISHED && highlights != null -> "OK ▶ Highlights"
    phase == MatchPhase.LIVE && channels.isNotEmpty() -> "OK ▶ Guarda ora"
    phase == MatchPhase.SCHEDULED && channels.isNotEmpty() -> "OK ▶ Guarda in automatico"
    else -> "OK per aprire Calcio in OpenTV"
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

@Composable
fun MatchCard(match: Match, modifier: Modifier, onClick: () -> Unit) {
    val live = match.phase == MatchPhase.LIVE
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = CardDefaults.shape(TileShape),
        scale = CardDefaults.scale(focusedScale = 1.08f),
        border = CardDefaults.border(
            border = if (live) Border(BorderStroke(2.dp, LiveRed.copy(alpha = 0.7f)), shape = TileShape) else Border.None,
            focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = TileShape),
        ),
        colors = CardDefaults.colors(containerColor = Color(0xFF1B2433)),
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF22324D), Color(0xFF141B27))),
            ),
        ) {
            Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Crest(match.homeCrest, 40.dp)
                    Text(
                        match.scoreLabel() ?: hour(match.kickoff),
                        fontSize = if (match.scoreLabel() != null) 22.sp else 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Crest(match.awayCrest, 40.dp)
                }
                Column {
                    Text(
                        "${match.home} – ${match.away}",
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        match.statusLabel(),
                        fontSize = 11.sp, fontWeight = if (live) FontWeight.Bold else FontWeight.Normal,
                        color = if (live) LiveRed else Soft, maxLines = 1,
                    )
                }
            }
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
            Text(match.scoreLabel() ?: "vs", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
