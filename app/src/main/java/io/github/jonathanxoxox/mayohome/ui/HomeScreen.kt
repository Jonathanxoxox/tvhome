package io.github.jonathanxoxox.mayohome.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.github.jonathanxoxox.mayohome.data.AppEntry
import io.github.jonathanxoxox.mayohome.data.Settings
import io.github.jonathanxoxox.mayohome.data.TvSnapshot

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

/**
 * Reiter Start, ruhig gehalten: oben nur die Banner der wichtigsten Apps, darunter "Weiter schauen" und
 * die Kanäle der Apps. Statt einer eigenen Fläche liegt das Bild des ausgewählten Inhalts gedimmt im
 * Hintergrund der ganzen Seite ([onBackdrop]).
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
    val channels = remember(tv, settings, pinned) {
        val order = pinned.map { it.packageName }
        if (!settings.showAppChannels) emptyList()
        else tv.channels.filter { it.key !in settings.hiddenAppChannels }
            .sortedBy { ch -> order.indexOf(ch.packageName).let { if (it < 0) Int.MAX_VALUE else it } }
    }
    fun backdrop(image: Any?) { if (settings.startBg == "content") onBackdrop(image) }
    LaunchedEffect(settings.startBg) { if (settings.startBg != "content") onBackdrop(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 26.dp, bottom = 56.dp),
        verticalArrangement = Arrangement.spacedBy(30.dp),
    ) {
        item(key = "apps") {
            Row(Modifier.padding(horizontal = 48.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                pinned.forEachIndexed { i, app ->
                    Box(Modifier.weight(1f)) {
                        AppTile(
                            app = app,
                            labels = "never",
                            modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            // Keine Vorschau an den Apps: OK öffnet die App, nicht einen bestimmten Inhalt.
                            onFocus = { if (it) backdrop(null) },
                            onClick = { vm.launchApp(context, app) },
                        )
                    }
                }
                // Weniger als vier Apps: Platz frei lassen, damit die Kacheln nicht riesig werden.
                repeat((4 - pinned.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
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
        if (tv.watchNext.isNotEmpty()) {
            item(key = "watchnext") {
                ShelfRow("Weiter schauen", tv.watchNext.take(20), key = { it.id }) { p ->
                    PosterTile(
                        image = p.posterUri, title = p.title, subtitle = "", titleOnFocus = true, width = 196.dp,
                        progress = p.progress, onClick = { vm.openProgram(context, p) },
                        onFocus = { if (it) backdrop(p.posterUri) },
                    )
                }
            }
        }
        channels.forEach { ch ->
            item(key = "ch-${ch.id}") {
                val appName = apps.firstOrNull { it.packageName == ch.packageName }?.label ?: ch.packageName
                ShelfRow(ch.name, ch.programs, key = { it.id }, note = appName) { p ->
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
}
