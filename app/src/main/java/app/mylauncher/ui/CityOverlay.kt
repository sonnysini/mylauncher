package app.mylauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.mylauncher.data.City

/** Weather city: type a name with the Fire keyboard, pick one of Open-Meteo's matches. */
@Composable
fun CityOverlay(
    current: City,
    results: List<Pair<City, String>>?,
    onSearch: (String) -> Unit,
    onPick: (City) -> Unit,
    onClose: () -> Unit,
) {
    val field = remember { FocusRequester() }
    val firstResult = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf(false) }
    var fieldFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
    // The Fire keyboard is full screen: once the matches are in, it goes and the first one takes focus.
    LaunchedEffect(results) {
        if (searched && !results.isNullOrEmpty()) runCatching { firstResult.requestFocus() }
    }
    BackHandler(onBack = onClose)

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .trapFocus()
                .width(460.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF161D2A))
                .padding(18.dp),
        ) {
            Text("Città meteo", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Ora: ${current.name} · scrivi e premi Cerca sulla tastiera", fontSize = 13.sp, color = Muted)
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { searched = true; keyboard?.hide(); onSearch(query) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(field)
                    .onFocusChanged { fieldFocused = it.isFocused }
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0B0F17))
                    .border(2.dp, if (fieldFocused) Color.White else Color(0xFF2A3446), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Nome della città", fontSize = 18.sp, color = Muted)
                    inner()
                },
            )
            Spacer(Modifier.height(10.dp))
            when {
                !searched -> Unit
                results == null -> Text("Cerco…", fontSize = 15.sp, color = Muted, modifier = Modifier.padding(8.dp))
                results.isEmpty() -> Text("Nessuna città trovata", fontSize = 15.sp, color = Muted, modifier = Modifier.padding(8.dp))
                else -> LazyColumn(Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(results.size) { i ->
                        val (city, where) = results[i]
                        Choice("${city.name}  ·  $where", if (i == 0) Modifier.focusRequester(firstResult) else Modifier) { onPick(city) }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Choice("Chiudi", Modifier, onClose)
        }
    }
}

@Composable
private fun Choice(label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White,
            contentColor = Color.White,
            focusedContentColor = Background,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Text(label, fontSize = 16.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp))
    }
}
