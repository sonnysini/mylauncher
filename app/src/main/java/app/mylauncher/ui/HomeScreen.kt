package app.mylauncher.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import app.mylauncher.HomeViewModel
import app.mylauncher.LoginState
import app.mylauncher.data.AppEntry
import app.mylauncher.data.nuvio.ContinueItem
import app.mylauncher.data.opentv.Match
import app.mylauncher.data.opentv.TvChannel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.flow.Flow

private val CardWidth = 196.dp
private val CardHeight = 110.dp
private val CardShape = RoundedCornerShape(12.dp)

private fun isOk(key: Key) = key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter

private sealed interface Focused {
    data class App(val app: AppEntry) : Focused
    data class Nuvio(val item: ContinueItem) : Focused
    data class Game(val match: Match) : Focused
    data class Tv(val channel: TvChannel) : Focused
}

private enum class Overlay { None, Settings, Profiles, Login, Hidden }

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    homePressed: Flow<Unit>,
    onLaunch: (Intent) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    val nuvio by viewModel.nuvioState.collectAsStateWithLifecycle()
    val login by viewModel.login.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val profileId by viewModel.profileId.collectAsStateWithLifecycle()
    val openTv by viewModel.openTvState.collectAsStateWithLifecycle()
    val matches = openTv.data.matches
    val tvChannels = openTv.data.channels
    // A match on now: Calcio goes to the top and takes the first focus.
    val liveFirst = openTv.anyLive
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(openTv) { now = System.currentTimeMillis() }

    val appsListState = rememberLazyListState()
    val nuvioListState = rememberLazyListState()
    val calcioListState = rememberLazyListState()
    val tvListState = rememberLazyListState()
    val rowsScroll = rememberScrollState()
    val requesters = remember { mutableStateMapOf<String, FocusRequester>() }
    fun requesterFor(key: String) = requesters.getOrPut(key) { FocusRequester() }

    var focused by remember { mutableStateOf<Focused?>(null) }
    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    var overlay by remember { mutableStateOf(Overlay.None) }
    var moving by remember { mutableStateOf<String?>(null) }
    var movingArmed by remember { mutableStateOf(false) }
    var initialFocusDone by remember { mutableStateOf(false) }
    var userMoved by remember { mutableStateOf(false) }

    fun refocusApp(pkg: String?) {
        val target = pkg?.takeIf { p -> apps.visible.any { it.packageName == p } }
            ?: apps.visible.firstOrNull()?.packageName ?: return
        runCatching { requesterFor("app:$target").requestFocus() }
    }

    /** Top-left of the home: a live match, else the first "Continua a guardare" tile, else the first app. */
    fun focusHome(): Boolean {
        val live = matches.firstOrNull().takeIf { liveFirst }
        val first = nuvio.items.firstOrNull()
        val key = when {
            live != null -> "match:${live.id}"
            first != null -> "nuvio:${first.contentId}"
            else -> "app:${apps.visible.firstOrNull()?.packageName ?: return false}"
        }
        return runCatching { requesterFor(key).requestFocus() }.isSuccess
    }

    LaunchedEffect(apps.visible.isNotEmpty(), nuvio.items.isNotEmpty(), liveFirst) {
        if (apps.visible.isEmpty()) return@LaunchedEffect
        // Nuvio loads after the app list: move to it once, unless the user already went somewhere.
        if (!initialFocusDone || (!userMoved && (nuvio.items.isNotEmpty() || liveFirst))) {
            initialFocusDone = focusHome()
        }
    }
    LaunchedEffect(homePressed) {
        homePressed.collect {
            menuFor = null; overlay = Overlay.None; moving = null
            viewModel.cancelLogin()
            rowsScroll.scrollTo(0)
            appsListState.scrollToItem(0)
            nuvioListState.scrollToItem(0)
            calcioListState.scrollToItem(0)
            tvListState.scrollToItem(0)
            viewModel.refreshOpenTv()
            focusHome()
            userMoved = false
        }
    }
    LaunchedEffect(apps.visible, moving) {
        val pkg = moving ?: return@LaunchedEffect
        val index = apps.visible.indexOfFirst { it.packageName == pkg }
        val shown = appsListState.layoutInfo.visibleItemsInfo.map { it.index }
        if (index >= 0 && (shown.isEmpty() || index <= shown.first() || index >= shown.last())) {
            appsListState.animateScrollToItem((index - 1).coerceAtLeast(0))
        }
    }

    BackHandler(enabled = true) {}
    BackHandler(enabled = moving != null) { val p = moving; moving = null; refocusApp(p) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Background)
            .onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown) userMoved = true; false },
    ) {
        Backdrop((focused as? Focused.Nuvio)?.item?.backdrop)

        Column(Modifier.fillMaxSize().padding(top = 28.dp)) {
            Header(weather, onOpenSettings = { overlay = Overlay.Settings }, modifier = Modifier.padding(horizontal = 48.dp))

            Hero(focused, moving != null, Modifier.padding(horizontal = 48.dp).height(190.dp))

            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rowsScroll)) {
                @Composable
                fun CalcioRow() {
                    if (!openTv.permitted || (matches.isEmpty() && !openTv.loaded)) return
                    RowTitle("Calcio", if (liveFirst) "Serie A · in diretta ora" else "Serie A · da OpenTV")
                    LazyRow(
                        state = calcioListState,
                        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        items(matches, key = { "match:${it.id}" }) { match ->
                            MatchCard(
                                match,
                                Modifier
                                    .size(CardWidth, CardHeight)
                                    .focusRequester(requesterFor("match:${match.id}"))
                                    .onFocusChanged { if (it.isFocused) focused = Focused.Game(match) },
                            ) { onLaunch(viewModel.matchIntent(match)) }
                        }
                        item(key = "__calcio_all") {
                            TextCard(
                                if (matches.isEmpty()) "Nessuna partita · apri Calcio" else "Tutte ›",
                                Modifier.onFocusChanged { if (it.isFocused) focused = null },
                            ) { onLaunch(viewModel.footballIntent()) }
                        }
                    }
                }

                if (liveFirst) CalcioRow()

                if (nuvio.available) {
                    RowTitle("Continua a guardare", "Nuvio")
                    LazyRow(
                        state = nuvioListState,
                        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        when {
                            !nuvio.signedIn -> item(key = "__link") {
                                TextCard("Collega il tuo account Nuvio", Modifier.onFocusChanged { if (it.isFocused) focused = null }) {
                                    viewModel.startLogin(); overlay = Overlay.Login
                                }
                            }
                            nuvio.items.isEmpty() -> item(key = "__empty") {
                                TextCard(
                                    when {
                                        !nuvio.loaded -> "Carico…"
                                        nuvio.error -> "Nuvio non risponde"
                                        else -> "Niente in corso"
                                    },
                                    Modifier.onFocusChanged { if (it.isFocused) focused = null },
                                ) { viewModel.refreshNuvio(force = true) }
                            }
                            else -> items(nuvio.items, key = { "nuvio:${it.contentId}" }) { item ->
                                NuvioCard(
                                    item,
                                    Modifier
                                        .focusRequester(requesterFor("nuvio:${item.contentId}"))
                                        .onFocusChanged { if (it.isFocused) focused = Focused.Nuvio(item) },
                                ) { onLaunch(viewModel.nuvioIntent(item)) }
                            }
                        }
                    }
                }

                if (!liveFirst) CalcioRow()

                if (openTv.permitted && tvChannels.isNotEmpty()) {
                    RowTitle("OpenTV", "Visti di recente e preferiti")
                    LazyRow(
                        state = tvListState,
                        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        items(tvChannels, key = { "tv:${it.id}" }) { channel ->
                            ChannelCard(
                                channel,
                                now,
                                Modifier
                                    .size(CardWidth, CardHeight)
                                    .focusRequester(requesterFor("tv:${channel.id}"))
                                    .onFocusChanged { if (it.isFocused) focused = Focused.Tv(channel) },
                            ) { onLaunch(viewModel.channelIntent(channel)) }
                        }
                    }
                }

                RowTitle("App", if (moving != null) "◀ ▶ per spostare, OK per confermare" else "Tieni premuto OK per spostare o nascondere")
                LazyRow(
                    state = appsListState,
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.onPreviewKeyEvent { e ->
                        val pkg = moving ?: return@onPreviewKeyEvent false
                        when {
                            e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft -> viewModel.move(pkg, -1)
                            e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight -> viewModel.move(pkg, 1)
                            // Same hold-OK guard as the menus: only a fresh press confirms.
                            isOk(e.key) && e.type == KeyEventType.KeyDown &&
                                e.nativeKeyEvent.repeatCount == 0 -> movingArmed = true
                            isOk(e.key) && e.type == KeyEventType.KeyUp && movingArmed -> { moving = null; refocusApp(pkg) }
                            e.key == Key.Back -> return@onPreviewKeyEvent false
                        }
                        true
                    },
                ) {
                    items(apps.visible, key = { it.packageName }) { app ->
                        AppCard(
                            app = app,
                            moving = moving == app.packageName,
                            modifier = Modifier
                                .focusRequester(requesterFor("app:${app.packageName}"))
                                .onFocusChanged { if (it.isFocused) focused = Focused.App(app) },
                            onClick = { onLaunch(viewModel.launchIntent(app)) },
                            onLongClick = { menuFor = app },
                        )
                    }
                    if (apps.hidden.isNotEmpty()) {
                        item(key = "__hidden") {
                            TextCard(
                                "App nascoste (${apps.hidden.size})",
                                modifier = Modifier.onFocusChanged { if (it.isFocused) focused = null },
                                onClick = { overlay = Overlay.Hidden },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        menuFor?.let { app ->
            OptionsOverlay(
                title = app.label,
                options = listOf(
                    "Sposta" to { menuFor = null; movingArmed = false; moving = app.packageName; refocusApp(app.packageName) },
                    "Nascondi" to { menuFor = null; viewModel.setHidden(app.packageName, true); refocusApp(null) },
                    "Annulla" to { menuFor = null; refocusApp(app.packageName) },
                ),
                onDismiss = { menuFor = null; refocusApp(app.packageName) },
            )
        }

        val close = { overlay = Overlay.None; focusHome(); Unit }
        when (overlay) {
            Overlay.Settings -> OptionsOverlay(
                title = "Impostazioni",
                options = buildList<Pair<String, () -> Unit>> {
                    if (nuvio.available) {
                        if (nuvio.signedIn) {
                            add("Profilo Nuvio: ${profiles.firstOrNull { it.index == profileId }?.name ?: profileId}" to {
                                viewModel.loadProfiles(); overlay = Overlay.Profiles
                            })
                            add("Scollega account Nuvio" to { viewModel.signOut(); close() })
                        } else {
                            add("Collega account Nuvio" to { viewModel.startLogin(); overlay = Overlay.Login })
                        }
                    }
                    add("Impostazioni Fire TV" to { overlay = Overlay.None; onOpenSettings() })
                    add("Chiudi" to close)
                },
                onDismiss = close,
            )
            Overlay.Profiles -> {
                LaunchedEffect(Unit) { viewModel.loadProfiles() }
                OptionsOverlay(
                    title = "Profilo Nuvio",
                    options = profiles.map { p ->
                        (if (p.index == profileId) "✓ " else "") + p.name.ifBlank { "Profilo ${p.index}" } to {
                            viewModel.setProfile(p.index); close()
                        }
                    }.ifEmpty { listOf("Carico…" to {}) } + ("Chiudi" to close),
                    onDismiss = close,
                )
            }
            Overlay.Login -> LoginOverlay(
                state = login,
                onRetry = viewModel::startLogin,
                onClose = { viewModel.cancelLogin(); close() },
            )
            Overlay.Hidden -> OptionsOverlay(
                title = "Mostra di nuovo",
                options = apps.hidden.map { app ->
                    app.label to {
                        viewModel.setHidden(app.packageName, false)
                        if (apps.hidden.size <= 1) close()
                    }
                } + ("Chiudi" to close),
                onDismiss = close,
            )
            Overlay.None -> Unit
        }
    }
}

@Composable
private fun RowTitle(title: String, subtitle: String) {
    Column(Modifier.padding(start = 48.dp, top = 6.dp)) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(subtitle, fontSize = 13.sp, color = Muted)
    }
}

/** Backdrop of the focused title, top right, fading into the background on the left and bottom. */
@Composable
private fun Backdrop(url: String?) {
    val context = LocalContext.current
    Crossfade(targetState = url, animationSpec = tween(400), label = "backdrop") { target ->
        if (target == null) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(listOf(Accent.copy(alpha = 0.22f), Color.Transparent), Offset(260f, 0f), 1100f),
                ),
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(target).size(1280, 720).crossfade(false).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth(0.68f).fillMaxHeight(0.72f),
                )
                Box(
                    Modifier.fillMaxWidth(0.68f).fillMaxHeight(0.72f).background(
                        Brush.horizontalGradient(0f to Background, 0.45f to Background.copy(alpha = 0.55f), 1f to Color.Transparent),
                    ),
                )
                Box(
                    Modifier.fillMaxWidth(0.68f).fillMaxHeight(0.72f).background(
                        Brush.verticalGradient(0.5f to Color.Transparent, 1f to Background),
                    ),
                )
            }
        }
    }
}

@Composable
private fun Hero(focused: Focused?, moving: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        when (focused) {
            is Focused.App -> Column {
                Text(focused.app.label, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                Text(if (moving) "Spostamento in corso" else "OK per aprire", fontSize = 15.sp, color = Muted)
            }
            is Focused.Nuvio -> Column(Modifier.fillMaxWidth(0.55f)) {
                val item = focused.item
                if (item.logo != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(item.logo).size(600, 180).build(),
                        contentDescription = item.title,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart,
                        modifier = Modifier.heightIn(max = 64.dp).widthIn(max = 320.dp),
                    )
                } else {
                    Text(item.title, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(8.dp))
                val extra = item.minutesLeft?.let { " · mancano $it min" }.orEmpty()
                Text(item.subtitle + extra, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Accent, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                Text(item.description, fontSize = 14.sp, color = Color(0xFFC9D1DE), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            is Focused.Game -> MatchHero(focused.match)
            is Focused.Tv -> ChannelHero(focused.channel)
            null -> Unit
        }
    }
}

@Composable
private fun NuvioCard(item: ContinueItem, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        onClick = onClick,
        modifier = modifier.size(CardWidth, CardHeight),
        shape = CardDefaults.shape(CardShape),
        scale = CardDefaults.scale(focusedScale = 1.08f),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = CardShape)),
        colors = CardDefaults.colors(containerColor = Color(0xFF1B2433)),
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(item.image).size(392, 220).build(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))))
            Column(Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) {
                Text(item.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.subtitle, fontSize = 11.sp, color = Color(0xFFC9D1DE), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            item.progress?.let { p ->
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.25f)))
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(p).height(4.dp).background(Accent))
            }
        }
    }
}

@Composable
private fun AppCard(
    app: AppEntry,
    moving: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.size(CardWidth, CardHeight),
        shape = CardDefaults.shape(CardShape),
        scale = CardDefaults.scale(focusedScale = 1.08f),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, if (moving) Accent else Color.White), shape = CardShape),
        ),
        colors = CardDefaults.colors(containerColor = Color(0xFF1B2433)),
    ) {
        if (app.banner != null) {
            Image(app.banner, contentDescription = app.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Column(
                Modifier.fillMaxSize().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(app.icon, contentDescription = null, modifier = Modifier.size(52.dp))
                Spacer(Modifier.height(6.dp))
                Text(app.label, fontSize = 13.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TextCard(label: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.size(CardWidth, CardHeight),
        shape = CardDefaults.shape(CardShape),
        scale = CardDefaults.scale(focusedScale = 1.08f),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = CardShape)),
        colors = CardDefaults.colors(containerColor = Color(0xFF161D2A)),
    ) {
        Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 15.sp, color = Muted, textAlign = TextAlign.Center)
        }
    }
}
