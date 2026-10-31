package io.github.jonathanxoxox.mayohome.ui

import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.github.jonathanxoxox.mayohome.data.AppEntry
import io.github.jonathanxoxox.mayohome.data.Settings
import io.github.jonathanxoxox.mayohome.data.TvSnapshot
import io.github.jonathanxoxox.mayohome.system.Launcher

/** Vorschlag für die Start-Apps, solange keine eigenen gewählt sind: Mayo TV, YouTube, Nuvio, Spotify. */
val DEFAULT_HOME_APPS = listOf(
    TvSnapshot.MAYO_TV,
    "io.gh.reisxd.tizentube.cobalt",
    "io.gh.reisxd.tizentube",
    "com.google.android.youtube.tv",
    "com.nuvio.tv",
    "com.nuvio.app",
    "com.spotify.tv.android",
)

/** Die Apps oben auf der Startseite: die gewählten, sonst der Vorschlag, sonst die ersten vier. */
fun homeApps(apps: List<AppEntry>, s: Settings): List<AppEntry> {
    val chosen = s.favoriteApps.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
    if (chosen.isNotEmpty()) return chosen
    val suggested = DEFAULT_HOME_APPS.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
        .distinctBy { it.label }.take(4)
    return suggested.ifEmpty { orderedApps(apps, s).take(4) }
}

/** Verschiebt [key] in [list] um [delta] Stellen; Schlüssel, die fehlen, bleiben wo sie sind. */
fun <T> List<T>.moved(key: T, delta: Int): List<T> {
    val i = indexOf(key)
    if (i < 0) return this
    val to = (i + delta).coerceIn(0, lastIndex)
    return toMutableList().apply { add(to, removeAt(i)) }
}

private const val WATCH_NEXT = "watchnext"
private const val MAX_HOME_APPS = 6

/**
 * Reiter Start, ruhig gehalten: oben nur die Banner der wichtigsten Apps, darunter "Weiter schauen" und
 * die Kanäle der Apps. Alles lässt sich an Ort und Stelle ordnen: lange OK auf eine App öffnet ihr Menü,
 * ◀ am Anfang einer Reihe zeigt Pfeile zum Verschieben und Ausblenden.
 */
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    settings: Settings,
    firstFocus: FocusRequester,
    onRequestTvPermission: () -> Unit,
    onBackdrop: (Any?) -> Unit,
) {
    val context = LocalContext.current
    val apps by vm.apps.collectAsState()
    val tv by vm.tv.collectAsState()
    val pinned = remember(apps, settings.favoriteApps) { homeApps(apps, settings) }
    var appMenu by remember { mutableStateOf<AppEntry?>(null) }
    var addMenu by remember { mutableStateOf(false) }

    // Reihen in der eingestellten Reihenfolge; neue hängen sich nach Reihenfolge der Start-Apps an.
    val rowKeys = remember(tv, settings, pinned) {
        val order = pinned.map { it.packageName }
        val all = buildList {
            if (tv.watchNext.isNotEmpty()) add(WATCH_NEXT)
            if (settings.showAppChannels) {
                tv.channels.sortedBy { ch -> order.indexOf(ch.packageName).let { if (it < 0) Int.MAX_VALUE else it } }
                    .forEach { add(it.key) }
            }
        }.filter { it !in settings.hiddenAppChannels }
        all.sortedBy { k -> settings.startRowOrder.indexOf(k).let { if (it < 0) Int.MAX_VALUE else it } }
    }
    val hiddenCount = settings.hiddenAppChannels.size

    fun backdrop(image: Any?) { if (settings.startBg == "content") onBackdrop(image) }
    LaunchedEffect(settings.startBg) { if (settings.startBg != "content") onBackdrop(null) }

    fun startApps(s: Settings) = s.favoriteApps.ifEmpty { homeApps(apps, s).map { it.packageName } }
    fun moveRow(key: String, delta: Int) = vm.updateSettings { it.copy(startRowOrder = rowKeys.moved(key, delta)) }
    fun controls(key: String, index: Int) = RowControls(
        onUp = if (index > 0) ({ moveRow(key, -1) }) else null,
        onDown = if (index < rowKeys.lastIndex) ({ moveRow(key, 1) }) else null,
        onHide = { vm.updateSettings { it.copy(hiddenAppChannels = it.hiddenAppChannels + key) } },
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 26.dp, bottom = 56.dp),
        verticalArrangement = Arrangement.spacedBy(30.dp),
    ) {
        item(key = "apps") {
            Row(
                Modifier.padding(horizontal = 48.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pinned.forEachIndexed { i, app ->
                    Box(Modifier.weight(1f)) {
                        AppTile(
                            app = app,
                            labels = "never",
                            modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            // Keine Vorschau an den Apps: OK öffnet die App, nicht einen bestimmten Inhalt.
                            onFocus = { if (it) backdrop(null) },
                            onClick = { vm.launchApp(context, app) },
                            onLongClick = { appMenu = app },
                        )
                    }
                }
                // Weniger als vier Apps: Platz frei lassen, damit die Kacheln nicht riesig werden.
                repeat((4 - pinned.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
                if (pinned.size < MAX_HOME_APPS) {
                    FocusCard(onClick = { addMenu = true }, corner = 50.dp, container = Color(0x14F5F7F7), modifier = Modifier.size(48.dp)) {
                        GlyphIcon(Glyph.PLUS, Palette.Muted, Modifier.align(Alignment.Center).size(18.dp))
                    }
                }
            }
        }
        if (tv.permissionMissing) {
            item(key = "permission") {
                Row(Modifier.padding(horizontal = 48.dp)) {
                    FocusCard(onClick = onRequestTvPermission, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Kanäle deiner Apps freigeben: OK fragt nach der Berechtigung „TV-Listen lesen“.",
                            fontSize = 13.sp, color = Palette.Muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        )
                    }
                }
            }
        }
        rowKeys.forEachIndexed { index, key ->
            if (key == WATCH_NEXT) {
                item(key = WATCH_NEXT) {
                    ShelfRow("Weiter schauen", tv.watchNext.take(20), key = { it.id }, controls = controls(key, index)) { p ->
                        PosterTile(
                            image = p.posterUri, title = p.title, subtitle = "", titleOnFocus = true, width = 196.dp,
                            progress = p.progress, onClick = { vm.openProgram(context, p) },
                            onFocus = { if (it) backdrop(p.posterUri) },
                        )
                    }
                }
            } else {
                val ch = tv.channels.firstOrNull { it.key == key } ?: return@forEachIndexed
                item(key = "ch-$key") {
                    val appName = apps.firstOrNull { it.packageName == ch.packageName }?.label ?: ch.packageName
                    ShelfRow(ch.name, ch.programs, key = { it.id }, note = appName, controls = controls(key, index)) { p ->
                        val wide = p.aspect >= 1f
                        PosterTile(
                            image = p.posterUri, title = p.title, subtitle = "", titleOnFocus = true,
                            width = if (wide) 196.dp else 112.dp, aspect = if (wide) 16f / 9f else 2f / 3f,
                            progress = p.progress, live = p.live, onClick = { vm.openProgram(context, p) },
                            onFocus = { if (it) backdrop(p.posterUri) },
                        )
                    }
                }
            }
        }
        if (hiddenCount > 0) {
            item(key = "hidden") {
                Row(Modifier.padding(horizontal = 48.dp)) {
                    SmallAction(if (hiddenCount == 1) "1 ausgeblendete Reihe wieder zeigen" else "$hiddenCount ausgeblendete Reihen wieder zeigen") {
                        vm.updateSettings { it.copy(hiddenAppChannels = emptySet()) }
                    }
                }
            }
        }
    }

    appMenu?.let { app ->
        val pos = pinned.indexOfFirst { it.packageName == app.packageName }
        val entries = buildList {
            add(MenuEntry("Öffnen") { vm.launchApp(context, app) })
            if (pos > 0) add(MenuEntry("Nach links") { vm.updateSettings { it.copy(favoriteApps = startApps(it).moved(app.packageName, -1)) } })
            if (pos in 0 until pinned.lastIndex) add(MenuEntry("Nach rechts") { vm.updateSettings { it.copy(favoriteApps = startApps(it).moved(app.packageName, 1)) } })
            add(MenuEntry("Von Start entfernen") { vm.updateSettings { it.copy(favoriteApps = startApps(it) - app.packageName) } })
            add(MenuEntry("App-Info") {
                Launcher.openSystemPage(context, AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
            })
        }
        MenuDialog(title = app.label, entries = entries, onDismiss = { appMenu = null })
    }
    if (addMenu) {
        val candidates = orderedApps(apps, settings).filter { a -> pinned.none { it.packageName == a.packageName } }
        MenuDialog(
            title = "App auf Start legen",
            entries = candidates.map { a -> MenuEntry(a.label) { vm.updateSettings { it.copy(favoriteApps = startApps(it) + a.packageName) } } },
            onDismiss = { addMenu = false },
        )
    }
}
