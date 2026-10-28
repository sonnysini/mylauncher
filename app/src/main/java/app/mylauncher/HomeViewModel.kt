package app.mylauncher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.mylauncher.data.AppEntry
import app.mylauncher.data.AppRepository
import app.mylauncher.data.City
import app.mylauncher.data.HomeRow
import app.mylauncher.data.Prefs
import app.mylauncher.data.Weather
import app.mylauncher.data.WeatherRepository
import app.mylauncher.data.nuvio.ContinueItem
import app.mylauncher.data.nuvio.NuvioApi
import app.mylauncher.data.nuvio.NuvioRepository
import app.mylauncher.data.opentv.Match
import app.mylauncher.data.opentv.MatchPhase
import app.mylauncher.data.opentv.OpenTvData
import app.mylauncher.data.opentv.OpenTvRepository
import app.mylauncher.data.opentv.TvChannel
import app.mylauncher.data.ordered
import android.os.SystemClock
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LoginState {
    data object Idle : LoginState
    data object Starting : LoginState
    data class Waiting(val login: NuvioApi.DeviceLogin) : LoginState
    data object Done : LoginState
    data class Failed(val message: String) : LoginState
}

data class NuvioState(
    val available: Boolean = false,
    val signedIn: Boolean = false,
    val items: List<ContinueItem> = emptyList(),
    val loaded: Boolean = false,
    val error: Boolean = false,
)

data class OpenTvState(
    val installed: Boolean = false,
    val permitted: Boolean = false,
    val data: OpenTvData = OpenTvData(),
    val loaded: Boolean = false,
) {
    val anyLive: Boolean get() = data.matches.any { it.phase == MatchPhase.LIVE }
}

data class AppsState(val visible: List<AppEntry> = emptyList(), val hidden: List<AppEntry> = emptyList())

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AppRepository(app)
    private val prefs = Prefs(app)
    private val weatherRepo = WeatherRepository()

    private val nuvio = NuvioRepository(app)

    private val installed = MutableStateFlow<List<AppEntry>>(emptyList())

    private val openTv = OpenTvRepository(app)
    private val _openTv = MutableStateFlow(OpenTvState())
    val openTvState: StateFlow<OpenTvState> = _openTv.asStateFlow()
    private var openTvJob: Job? = null

    val apps: StateFlow<AppsState> = combine(installed, prefs.layout) { list, layout ->
        val sorted = list.ordered(layout.order) { it.packageName }
        AppsState(
            visible = sorted.filter { it.packageName !in layout.hidden },
            hidden = sorted.filter { it.packageName in layout.hidden },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppsState())

    private val _weather = MutableStateFlow<Weather?>(null)
    val weather: StateFlow<Weather?> = _weather.asStateFlow()
    val city: StateFlow<City> = prefs.city.stateIn(viewModelScope, SharingStarted.Eagerly, City.DEFAULT)

    val rows: StateFlow<List<HomeRow>> = prefs.rows.stateIn(viewModelScope, SharingStarted.Eagerly, HomeRow.entries)

    private val _citySearch = MutableStateFlow<List<Pair<City, String>>?>(null)
    /** Results of the last city search; null while none was run (or one is running). */
    val citySearch: StateFlow<List<Pair<City, String>>?> = _citySearch.asStateFlow()

    private val _nuvio = MutableStateFlow(NuvioState(available = nuvio.api.configured))
    val nuvioState: StateFlow<NuvioState> = _nuvio.asStateFlow()

    private val _login = MutableStateFlow<LoginState>(LoginState.Idle)
    val login: StateFlow<LoginState> = _login.asStateFlow()
    private var loginJob: Job? = null

    private val _profiles = MutableStateFlow<List<NuvioApi.Profile>>(emptyList())
    val profiles: StateFlow<List<NuvioApi.Profile>> = _profiles.asStateFlow()
    val profileId: StateFlow<Int> = nuvio.store.profileId.stateIn(viewModelScope, SharingStarted.Eagerly, 1)

    private var lastNuvioRefresh = 0L

    init {
        reloadApps()
        viewModelScope.launch {
            nuvio.signedIn.collect { signedIn ->
                _nuvio.value = _nuvio.value.copy(signedIn = signedIn, items = if (signedIn) _nuvio.value.items else emptyList())
                if (signedIn) refreshNuvio(force = true)
            }
        }
        viewModelScope.launch {
            prefs.city.collectLatest { city ->
                if (_weather.value?.city != city.name) _weather.value = null
                while (true) {
                    weatherRepo.current(city)?.let { _weather.value = it }
                    delay(if (_weather.value == null) 60_000L else 30 * 60_000L)
                }
            }
        }
    }

    /** Called on every return to the home screen; throttled so app switching stays cheap. */
    fun refreshNuvio(force: Boolean = false) {
        val state = _nuvio.value
        if (!state.available || !state.signedIn) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastNuvioRefresh < NUVIO_MIN_INTERVAL_MS) return
        lastNuvioRefresh = now
        viewModelScope.launch {
            val result = runCatching { nuvio.continueWatching() }
            _nuvio.value = _nuvio.value.copy(
                items = result.getOrNull() ?: _nuvio.value.items,
                loaded = true,
                error = result.isFailure,
            )
        }
    }

    fun nuvioIntent(item: ContinueItem) = nuvio.openIntent(item)

    /** OpenTV's rows: on every return home and on a timer (faster while a match is live). */
    fun refreshOpenTv() {
        if (openTvJob?.isActive == true) return
        openTvJob = viewModelScope.launch {
            val installed = openTv.installed
            val permitted = installed && openTv.permitted
            val data = if (permitted) runCatching { openTv.load() }.getOrNull() else OpenTvData()
            _openTv.value = OpenTvState(installed, permitted, data ?: _openTv.value.data, loaded = true)
        }
    }

    fun channelIntent(channel: TvChannel) = openTv.channelIntent(channel)
    fun matchIntent(match: Match) = openTv.matchIntent(match)
    fun footballIntent() = openTv.footballIntent()
    fun openTvIntent() = openTv.openIntent()

    fun startLogin() {
        loginJob?.cancel()
        loginJob = viewModelScope.launch {
            _login.value = LoginState.Starting
            val login = runCatching { nuvio.api.startDeviceLogin() }.getOrElse {
                _login.value = LoginState.Failed("Impossibile avviare l'accesso")
                return@launch
            }
            _login.value = LoginState.Waiting(login)
            while (isActive) {
                delay(login.pollSeconds * 1000L)
                when (runCatching { nuvio.api.pollDeviceLogin(login) }.getOrDefault("pending")) {
                    "approved" -> {
                        _login.value = runCatching { nuvio.api.exchangeDeviceLogin(login) }
                            .fold({ LoginState.Done }, { LoginState.Failed("Accesso non riuscito") })
                        return@launch
                    }
                    "expired", "used", "cancelled" -> {
                        _login.value = LoginState.Failed("Codice scaduto, riprova")
                        return@launch
                    }
                }
            }
        }
    }

    fun cancelLogin() {
        loginJob?.cancel()
        _login.value = LoginState.Idle
    }

    fun loadProfiles() {
        viewModelScope.launch { _profiles.value = runCatching { nuvio.api.profiles() }.getOrDefault(emptyList()) }
    }

    fun setProfile(id: Int) {
        viewModelScope.launch {
            nuvio.store.setProfile(id)
            refreshNuvio(force = true)
        }
    }

    fun signOut() {
        viewModelScope.launch { nuvio.store.clear() }
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

    /** Moves [row] one place up (wrapping the first one to the bottom). */
    fun moveRowUp(row: HomeRow) {
        val list = rows.value.toMutableList()
        val from = list.indexOf(row)
        if (from < 0) return
        list.removeAt(from)
        list.add(if (from == 0) list.size else from - 1, row)
        viewModelScope.launch { prefs.setRows(list) }
    }

    fun searchCity(query: String) {
        if (query.isBlank()) return
        _citySearch.value = null
        viewModelScope.launch { _citySearch.value = weatherRepo.search(query) }
    }

    fun setCity(city: City) {
        viewModelScope.launch { prefs.setCity(city) }
        _citySearch.value = null
    }

    private companion object {
        const val NUVIO_MIN_INTERVAL_MS = 60_000L
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
