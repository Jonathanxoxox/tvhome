package io.github.jonathanxoxox.mayohome.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.json.JSONArray
import org.json.JSONObject

/** Reihen unter dem Poster im Reiter Live TV, in der Reihenfolge, die der Nutzer einstellt. */
enum class StartRow(val key: String, val label: String) {
    CATEGORY("category", "Kategorie aus Mayo TV"),
    RECENT("recent", "Zuletzt gesehen"),
    CHANNELS("channels", "Sender aus dem Katalog"),
    CONTINUE("continue", "Weiter schauen");

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key }
    }
}

/** Arten von Sendungen, die als Highlight oben im Poster erscheinen dürfen. */
enum class HighlightKind(val key: String, val label: String) {
    FOOTBALL("football", "Fußball"),
    SPORT("sport", "Anderer Sport"),
    FILM("film", "Filme"),
    SERIES("series", "Serien"),
    SHOW("show", "Shows"),
    DOCU("docu", "Dokus");

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key }
    }
}

/**
 * Alle Einstellungen von Mayo Home. Gespeichert wird das Ganze als ein JSON-Text, damit Sichern und
 * Wiederherstellen dieselbe Form benutzen wie der Speicher.
 */
data class Settings(
    // Startseite: Poster
    val heroUseMayo: Boolean = true,
    val heroUseEpg: Boolean = true,
    val heroUseWatchNext: Boolean = true,
    val footballFirst: Boolean = true,
    val favoriteTeams: List<String> = emptyList(),
    val highlightKinds: Set<String> = setOf("football", "sport", "film", "series"),
    val heroRotateSeconds: Int = 10,
    val heroMax: Int = 6,
    val heroLookaheadHours: Int = 3,
    // Live TV: Reihen unter dem Poster und die Kategorie aus Mayo TV
    val rows: List<String> = listOf("category", "recent"),
    val hiddenRows: Set<String> = emptySet(),
    val liveCategoryId: Long = -1L,
    val liveCategoryName: String = "",
    // Start: die wichtigsten Apps (Paketnamen, in dieser Reihenfolge); leer = Vorschlag
    val favoriteApps: List<String> = emptyList(),
    /** Reihenfolge der Reihen auf Start ("watchnext" und die Schlüssel der App-Kanäle). */
    val startRowOrder: List<String> = emptyList(),
    /** Hintergrund auf Start: "aurora" (sanfter Verlauf), "plain" (schlicht) oder "content" (Bild des Ausgewählten). */
    val startBg: String = "aurora",
    // Sender aus dem Katalog (MagentaTV-IDs), in dieser Reihenfolge
    val channels: List<String> = DEFAULT_CHANNELS,
    // Apps
    val appColumns: Int = 6,
    val appLabels: String = "focus",
    val appSort: String = "manual",
    val appOrder: List<String> = emptyList(),
    val hiddenApps: Set<String> = emptySet(),
    val showAppChannels: Boolean = true,
    val hiddenAppChannels: Set<String> = emptySet(),
    val appLastUsed: Map<String, Long> = emptyMap(),
    // Aussehen
    val accent: String = "white",
    val background: String = "black",
    val corners: String = "medium",
    val focusScale: String = "medium",
    val animations: Boolean = true,
    val clock24: Boolean = true,
    val showDate: Boolean = false,
    val uiScale: Int = 100,
    val startTab: String = "start",
    // Hauptlauncher
    val homeOverride: Boolean = true,
    val openOnBoot: Boolean = true,
    val bootApp: String = "",
    val settingsPin: String = "",
    val autoUpdate: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("heroUseMayo", heroUseMayo)
        put("heroUseEpg", heroUseEpg)
        put("heroUseWatchNext", heroUseWatchNext)
        put("footballFirst", footballFirst)
        put("favoriteTeams", JSONArray(favoriteTeams))
        put("highlightKinds", JSONArray(highlightKinds.toList()))
        put("heroRotateSeconds", heroRotateSeconds)
        put("heroMax", heroMax)
        put("heroLookaheadHours", heroLookaheadHours)
        put("liveRows", JSONArray(rows))
        put("liveHiddenRows", JSONArray(hiddenRows.toList()))
        put("liveCategoryId", liveCategoryId)
        put("liveCategoryName", liveCategoryName)
        put("favoriteApps", JSONArray(favoriteApps))
        put("startRowOrder", JSONArray(startRowOrder))
        put("startBg", startBg)
        put("channels", JSONArray(channels))
        put("appColumns", appColumns)
        put("appLabels", appLabels)
        put("appSort", appSort)
        put("appOrder", JSONArray(appOrder))
        put("hiddenApps", JSONArray(hiddenApps.toList()))
        put("showAppChannels", showAppChannels)
        put("hiddenAppChannels", JSONArray(hiddenAppChannels.toList()))
        put("appLastUsed", JSONObject().apply { appLastUsed.forEach { (k, v) -> put(k, v) } })
        put("accent", accent)
        put("background", background)
        put("corners", corners)
        put("focusScale", focusScale)
        put("animations", animations)
        put("clock24", clock24)
        put("showDate", showDate)
        put("uiScale", uiScale)
        put("startTab", startTab)
        put("homeOverride", homeOverride)
        put("openOnBoot", openOnBoot)
        put("bootApp", bootApp)
        put("settingsPin", settingsPin)
        put("autoUpdate", autoUpdate)
    }

    companion object {
        /** Sky Sport, Sky Cinema und die großen Sender, als Vorschlag beim ersten Start. */
        val DEFAULT_CHANNELS = listOf(
            "206", "42", "31", "5556", "5555", "195", "6210", "264", "373", "408", "374", "404",
        )

        fun fromJson(o: JSONObject): Settings {
            val d = Settings()
            fun strings(key: String, fallback: List<String>): List<String> =
                o.optJSONArray(key)?.let { a -> List(a.length()) { a.optString(it) }.filter { it.isNotBlank() } } ?: fallback
            val lastUsed = o.optJSONObject("appLastUsed")?.let { obj ->
                obj.keys().asSequence().associateWith { obj.optLong(it) }
            } ?: d.appLastUsed
            return Settings(
                heroUseMayo = o.optBoolean("heroUseMayo", d.heroUseMayo),
                heroUseEpg = o.optBoolean("heroUseEpg", d.heroUseEpg),
                heroUseWatchNext = o.optBoolean("heroUseWatchNext", d.heroUseWatchNext),
                footballFirst = o.optBoolean("footballFirst", d.footballFirst),
                favoriteTeams = strings("favoriteTeams", d.favoriteTeams),
                highlightKinds = strings("highlightKinds", d.highlightKinds.toList()).toSet(),
                heroRotateSeconds = o.optInt("heroRotateSeconds", d.heroRotateSeconds),
                heroMax = o.optInt("heroMax", d.heroMax),
                heroLookaheadHours = o.optInt("heroLookaheadHours", d.heroLookaheadHours),
                rows = strings("liveRows", d.rows),
                hiddenRows = strings("liveHiddenRows", emptyList()).toSet(),
                liveCategoryId = o.optLong("liveCategoryId", d.liveCategoryId),
                liveCategoryName = o.optString("liveCategoryName", d.liveCategoryName),
                favoriteApps = strings("favoriteApps", d.favoriteApps),
                startRowOrder = strings("startRowOrder", d.startRowOrder),
                startBg = o.optString("startBg", d.startBg),
                channels = strings("channels", d.channels),
                appColumns = o.optInt("appColumns", d.appColumns).coerceIn(4, 8),
                appLabels = o.optString("appLabels", d.appLabels),
                appSort = o.optString("appSort", d.appSort),
                appOrder = strings("appOrder", d.appOrder),
                hiddenApps = strings("hiddenApps", emptyList()).toSet(),
                showAppChannels = o.optBoolean("showAppChannels", d.showAppChannels),
                hiddenAppChannels = strings("hiddenAppChannels", emptyList()).toSet(),
                appLastUsed = lastUsed,
                accent = o.optString("accent", d.accent),
                background = o.optString("background", d.background),
                corners = o.optString("corners", d.corners),
                focusScale = o.optString("focusScale", d.focusScale),
                animations = o.optBoolean("animations", d.animations),
                clock24 = o.optBoolean("clock24", d.clock24),
                showDate = o.optBoolean("showDate", d.showDate),
                uiScale = o.optInt("uiScale", d.uiScale).coerceIn(80, 130),
                startTab = o.optString("startTab", d.startTab),
                homeOverride = o.optBoolean("homeOverride", d.homeOverride),
                openOnBoot = o.optBoolean("openOnBoot", d.openOnBoot),
                bootApp = o.optString("bootApp", d.bootApp),
                settingsPin = o.optString("settingsPin", d.settingsPin),
                autoUpdate = o.optBoolean("autoUpdate", d.autoUpdate),
            )
        }

        fun parse(text: String?): Settings =
            if (text.isNullOrBlank()) Settings() else runCatching { fromJson(JSONObject(text)) }.getOrDefault(Settings())
    }
}

private val Context.store: DataStore<Preferences> by preferencesDataStore(name = "mayo_home")
private val KEY = stringPreferencesKey("settings_json")

/** Liest und schreibt [Settings]; [state] ist der aktuelle Stand für Oberfläche und Dienste. */
class SettingsStore(private val context: Context, scope: CoroutineScope) {
    val state: StateFlow<Settings> = context.store.data
        .map { Settings.parse(it[KEY]) }
        .stateIn(scope, SharingStarted.Eagerly, Settings())

    /** Der gespeicherte Stand, direkt gelesen (für Dienste, die vor der Oberfläche laufen). */
    suspend fun current(): Settings = Settings.parse(context.store.data.first()[KEY])

    suspend fun update(change: (Settings) -> Settings) {
        context.store.edit { prefs -> prefs[KEY] = change(Settings.parse(prefs[KEY])).toJson().toString() }
    }

    suspend fun replace(settings: Settings) {
        context.store.edit { it[KEY] = settings.toJson().toString() }
    }
}
