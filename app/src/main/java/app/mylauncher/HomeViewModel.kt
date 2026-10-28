package app.mylauncher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.mylauncher.data.AppEntry
import app.mylauncher.data.AppRepository
import app.mylauncher.data.Prefs
import app.mylauncher.data.Weather
import app.mylauncher.data.WeatherRepository
import app.mylauncher.data.ordered
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppsState(val visible: List<AppEntry> = emptyList(), val hidden: List<AppEntry> = emptyList())

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AppRepository(app)
    private val prefs = Prefs(app)
    private val weatherRepo = WeatherRepository()

    private val installed = MutableStateFlow<List<AppEntry>>(emptyList())

    val apps: StateFlow<AppsState> = combine(installed, prefs.layout) { list, layout ->
        val sorted = list.ordered(layout.order) { it.packageName }
        AppsState(
            visible = sorted.filter { it.packageName !in layout.hidden },
            hidden = sorted.filter { it.packageName in layout.hidden },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppsState())

    private val _weather = MutableStateFlow<Weather?>(null)
    val weather: StateFlow<Weather?> = _weather.asStateFlow()

    init {
        reloadApps()
        viewModelScope.launch {
            while (true) {
                weatherRepo.current()?.let { _weather.value = it }
                delay(if (_weather.value == null) 60_000L else 30 * 60_000L)
            }
        }
    }

    fun reloadApps() {
        viewModelScope.launch { installed.value = repo.loadApps() }
    }

    fun launchIntent(app: AppEntry) = repo.launchIntent(app)

    /** Moves [packageName] by [delta] places among the visible apps. */
    fun move(packageName: String, delta: Int) {
        val state = apps.value
        val visible = state.visible.map { it.packageName }.toMutableList()
        val from = visible.indexOf(packageName)
        val to = from + delta
        if (from < 0 || to !in visible.indices) return
        visible.add(to, visible.removeAt(from))
        viewModelScope.launch { prefs.setOrder(visible + state.hidden.map { it.packageName }) }
    }

    fun setHidden(packageName: String, hidden: Boolean) {
        viewModelScope.launch {
            // Freeze the current order first, so an unhidden app comes back where it was.
            if (prefs.layout.first().order.isEmpty()) {
                prefs.setOrder(apps.value.let { s -> (s.visible + s.hidden).map { it.packageName } })
            }
            prefs.setHidden(packageName, hidden)
        }
    }
}
