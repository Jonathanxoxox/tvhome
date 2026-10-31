package io.github.jonathanxoxox.mayohome.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.jonathanxoxox.mayohome.MayoHomeApp
import io.github.jonathanxoxox.mayohome.data.AppEntry
import io.github.jonathanxoxox.mayohome.data.ChannelProgram
import io.github.jonathanxoxox.mayohome.data.EpgProgram
import io.github.jonathanxoxox.mayohome.data.HeroAction
import io.github.jonathanxoxox.mayohome.data.Settings
import io.github.jonathanxoxox.mayohome.data.TvChannelsRepository
import io.github.jonathanxoxox.mayohome.data.TvSnapshot
import io.github.jonathanxoxox.mayohome.system.Launcher
import io.github.jonathanxoxox.mayohome.system.UpdateInfo
import io.github.jonathanxoxox.mayohome.system.Updater
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MayoHomeApp

    val settings: StateFlow<Settings> = app.settings.state

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val _tv = MutableStateFlow(TvSnapshot())
    val tv: StateFlow<TvSnapshot> = _tv.asStateFlow()

    private val _epg = MutableStateFlow<Map<String, List<EpgProgram>>>(emptyMap())
    val epg: StateFlow<Map<String, List<EpgProgram>>> = _epg.asStateFlow()

    private val _now = MutableStateFlow(System.currentTimeMillis())
    /** Die aktuelle Zeit, jede Minute neu, damit Fortschritt und Highlights mitlaufen. */
    val now: StateFlow<Long> = _now.asStateFlow()

    val epgError: String? get() = app.epg.lastError
    val catalog get() = app.epg.catalog
    fun channelName(id: String) = app.epg.name(id)

    private var lastEpgLoad = 0L

    // --- Update ---

    sealed interface UpdateState {
        data object Idle : UpdateState
        data object Checking : UpdateState
        data object UpToDate : UpdateState
        data class Available(val info: UpdateInfo) : UpdateState
        data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
        data class Installing(val info: UpdateInfo) : UpdateState
        data class Failed(val message: String) : UpdateState
    }

    private val updater = Updater(application)
    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()
    private var lastUpdateCheck = 0L

    fun checkForUpdate(manual: Boolean) = viewModelScope.launch {
        if (_update.value is UpdateState.Downloading || _update.value is UpdateState.Installing) return@launch
        if (!manual && System.currentTimeMillis() - lastUpdateCheck < 6 * 3_600_000L) return@launch
        lastUpdateCheck = System.currentTimeMillis()
        if (manual) _update.value = UpdateState.Checking
        _update.value = try {
            updater.check()?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate
        } catch (e: Exception) {
            if (manual) UpdateState.Failed(e.message ?: "Keine Verbindung") else UpdateState.Idle
        }
    }

    fun installUpdate() = viewModelScope.launch {
        val info = (_update.value as? UpdateState.Available)?.info
        if (info == null) {
            checkForUpdate(manual = true)
            return@launch
        }
        if (!updater.canInstall()) {
            updater.openInstallPermission()
            return@launch
        }
        try {
            _update.value = UpdateState.Downloading(info, 0f)
            val file = updater.download(info) { p -> _update.value = UpdateState.Downloading(info, p) }
            _update.value = UpdateState.Installing(info)
            updater.install(file)
        } catch (e: Exception) {
            _update.value = UpdateState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    init {
        refreshApps()
        refreshTv()
        viewModelScope.launch {
            if (app.settings.current().autoUpdate) checkForUpdate(manual = false)
        }
        viewModelScope.launch {
            // Neu laden, sobald sich die gewählten Sender ändern.
            settings.map { it.channels to it.heroUseEpg }.distinctUntilChanged().collect { refreshEpg(force = true) }
        }
        viewModelScope.launch {
            var minutes = 0
            while (true) {
                delay(60_000)
                _now.value = System.currentTimeMillis()
                minutes++
                if (minutes % 5 == 0) refreshTv()
                if (minutes % 15 == 0) refreshEpg(force = true)
                if (minutes % 360 == 0 && settings.value.autoUpdate) checkForUpdate(manual = false)
            }
        }
    }

    fun onResume() {
        _now.value = System.currentTimeMillis()
        refreshTv()
        refreshEpg(force = false)
    }

    fun refreshApps() = viewModelScope.launch { _apps.value = app.apps.load() }

    fun refreshTv() = viewModelScope.launch { _tv.value = app.tv.load() }

    fun refreshEpg(force: Boolean) = viewModelScope.launch {
        val s = app.settings.current()
        val now = System.currentTimeMillis()
        if (!force && now - lastEpgLoad < 10 * 60_000L) return@launch
        lastEpgLoad = now
        if (s.channels.isEmpty()) { _epg.value = emptyMap(); return@launch }
        val loaded = app.epg.load(s.channels, now - 4 * 3_600_000L, now + 10 * 3_600_000L)
        if (loaded.isNotEmpty() || force) _epg.value = loaded
    }

    fun updateSettings(change: (Settings) -> Settings) = viewModelScope.launch { app.settings.update(change) }

    fun replaceSettings(s: Settings) = viewModelScope.launch { app.settings.replace(s) }

    // --- Öffnen ---

    fun launchApp(context: Context, entry: AppEntry) {
        if (Launcher.start(context, Intent(entry.launchIntent))) {
            updateSettings { it.copy(appLastUsed = it.appLastUsed + (entry.packageName to System.currentTimeMillis())) }
        }
    }

    fun openProgram(context: Context, p: ChannelProgram) {
        val intent = TvChannelsRepository.intentOf(p.intentUri)
            ?: context.packageManager.getLeanbackLaunchIntentForPackage(p.packageName)
            ?: context.packageManager.getLaunchIntentForPackage(p.packageName)
        Launcher.start(context, intent)
    }

    fun openHero(context: Context, action: HeroAction) = when (action) {
        is HeroAction.Link -> Launcher.start(context, TvChannelsRepository.intentOf(action.uri))
        is HeroAction.Channel -> openChannel(context, action.channelName)
    }

    /**
     * Öffnet einen Sender in Mayo TV. Mayo TV zeigt seine Lieblingssender und zuletzt gesehenen Sender als
     * Kanäle an; dort steht der passende Link. Gibt es keinen, öffnet sich Mayo TV einfach.
     */
    fun openChannel(context: Context, channelName: String): Boolean {
        val wanted = normalize(channelName)
        val tv = _tv.value
        val candidates = tv.mayo(TvSnapshot.MAYO_FAVORITES) + tv.mayoRecent()
        // Mayo TV schreibt den Sendernamen in Titel oder Beschreibung seiner Kacheln; kurze Wörter zählen nicht,
        // damit "Tor" nicht auf "Sky Sport Top Event" passt.
        val hit = candidates.firstOrNull { p ->
            listOf(p.title, p.subtitle, p.description).any { normalize(it).let { n -> n.length >= 3 && (n.contains(wanted) || wanted == n) } }
        }
        val intent = TvChannelsRepository.intentOf(hit?.intentUri)
            ?: context.packageManager.getLeanbackLaunchIntentForPackage(TvSnapshot.MAYO_TV)
            ?: context.packageManager.getLaunchIntentForPackage(TvSnapshot.MAYO_TV)
        if (intent == null) {
            Toast.makeText(context, "Mayo TV ist nicht installiert.", Toast.LENGTH_SHORT).show()
            return false
        }
        return Launcher.start(context, intent)
    }

    private fun normalize(s: String) = s.lowercase(Locale.GERMAN).filter { it.isLetterOrDigit() }
}
