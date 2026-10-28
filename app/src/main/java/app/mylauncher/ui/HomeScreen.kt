package app.mylauncher.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import app.mylauncher.data.AppEntry
import kotlinx.coroutines.flow.Flow

private val CardWidth = 196.dp
private val CardHeight = 110.dp
private val CardShape = RoundedCornerShape(12.dp)

private fun isOk(key: Key) = key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    homePressed: Flow<Unit>,
    onLaunch: (Intent) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val weather by viewModel.weather.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val requesters = remember { mutableStateMapOf<String, FocusRequester>() }
    fun requesterFor(pkg: String) = requesters.getOrPut(pkg) { FocusRequester() }

    var focused by remember { mutableStateOf<AppEntry?>(null) }
    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    var showHidden by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<String?>(null) }
    var movingArmed by remember { mutableStateOf(false) }
    var initialFocusDone by remember { mutableStateOf(false) }

    fun refocus(pkg: String?) {
        val target = pkg?.takeIf { p -> apps.visible.any { it.packageName == p } }
            ?: apps.visible.firstOrNull()?.packageName ?: return
        runCatching { requesterFor(target).requestFocus() }
    }

    LaunchedEffect(apps.visible.isNotEmpty()) {
        if (apps.visible.isNotEmpty() && !initialFocusDone) {
            initialFocusDone = true
            refocus(null)
        }
    }
    LaunchedEffect(homePressed) {
        homePressed.collect {
            menuFor = null; showHidden = false; moving = null
            listState.scrollToItem(0)
            refocus(null)
        }
    }
    // Keep the card being moved on screen.
    LaunchedEffect(apps.visible, moving) {
        val pkg = moving ?: return@LaunchedEffect
        val index = apps.visible.indexOfFirst { it.packageName == pkg }
        val shown = listState.layoutInfo.visibleItemsInfo.map { it.index }
        if (index >= 0 && (shown.isEmpty() || index <= shown.first() || index >= shown.last())) {
            listState.animateScrollToItem((index - 1).coerceAtLeast(0))
        }
    }

    // A launcher has nowhere to go back to.
    BackHandler(enabled = true) {}
    BackHandler(enabled = moving != null) { val p = moving; moving = null; refocus(p) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Background)
            .background(
                Brush.radialGradient(
                    colors = listOf(Accent.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(260f, 0f),
                    radius = 1100f,
                ),
            ),
    ) {
        Column(Modifier.fillMaxSize().padding(top = 28.dp)) {
            Header(weather, onOpenSettings, Modifier.padding(horizontal = 48.dp))

            Hero(focused, moving != null, Modifier.padding(horizontal = 48.dp).height(200.dp))

            Text(
                "App",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(start = 48.dp),
            )
            Text(
                if (moving != null) "◀ ▶ per spostare, OK per confermare" else "Tieni premuto OK per spostare o nascondere",
                fontSize = 13.sp,
                color = Muted,
                modifier = Modifier.padding(start = 48.dp, bottom = 10.dp),
            )

            LazyRow(
                state = listState,
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
                        isOk(e.key) && e.type == KeyEventType.KeyUp && movingArmed -> { moving = null; refocus(pkg) }
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
                            .focusRequester(requesterFor(app.packageName))
                            .onFocusChanged { if (it.isFocused) focused = app },
                        onClick = { onLaunch(viewModel.launchIntent(app)) },
                        onLongClick = { menuFor = app },
                    )
                }
                if (apps.hidden.isNotEmpty()) {
                    item(key = "__hidden") {
                        TextCard(
                            "App nascoste (${apps.hidden.size})",
                            modifier = Modifier.onFocusChanged { if (it.isFocused) focused = null },
                            onClick = { showHidden = true },
                        )
                    }
                }
            }
        }

        menuFor?.let { app ->
            OptionsOverlay(
                title = app.label,
                options = listOf(
                    "Sposta" to { menuFor = null; movingArmed = false; moving = app.packageName; refocus(app.packageName) },
                    "Nascondi" to { menuFor = null; viewModel.setHidden(app.packageName, true); refocus(null) },
                    "Annulla" to { menuFor = null; refocus(app.packageName) },
                ),
                onDismiss = { menuFor = null; refocus(app.packageName) },
            )
        }
        if (showHidden) {
            OptionsOverlay(
                title = "Mostra di nuovo",
                options = apps.hidden.map { app ->
                    app.label to {
                        viewModel.setHidden(app.packageName, false)
                        if (apps.hidden.size <= 1) showHidden = false
                    }
                } + ("Chiudi" to { showHidden = false; refocus(null) }),
                onDismiss = { showHidden = false; refocus(null) },
            )
        }
    }
}

@Composable
private fun Hero(app: AppEntry?, moving: Boolean, modifier: Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        if (app != null) {
            Column {
                Text(app.label, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                Text(
                    if (moving) "Spostamento in corso" else "OK per aprire",
                    fontSize = 15.sp,
                    color = Muted,
                )
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
