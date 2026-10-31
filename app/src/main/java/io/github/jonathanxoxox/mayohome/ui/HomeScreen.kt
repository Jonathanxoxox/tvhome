package io.github.jonathanxoxox.mayohome.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.github.jonathanxoxox.mayohome.data.AppEntry
import io.github.jonathanxoxox.mayohome.data.MayoSnapshot
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
 * Reiter Start: die wichtigsten Apps groß, jede mit einer Zeile, was dort gerade ist, und darunter
 * die Kanäle, die diese Apps (und andere) für den Startbildschirm anbieten.
 */
@Composable
fun HomeScreen(vm: HomeViewModel, settings: Settings, firstFocus: FocusRequester, onRequestTvPermission: () -> Unit) {
    val context = LocalContext.current
    val apps by vm.apps.collectAsState()
    val tv by vm.tv.collectAsState()
    val mayo by vm.mayo.collectAsState()
    val pinned = remember(apps, settings.favoriteApps) { homeApps(apps, settings) }
    val order = pinned.map { it.packageName }
    val channels = remember(tv, settings) {
        if (!settings.showAppChannels) emptyList()
        else tv.channels.filter { it.key !in settings.hiddenAppChannels }
            .sortedBy { ch -> order.indexOf(ch.packageName).let { if (it < 0) Int.MAX_VALUE else it } }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item(key = "apps") {
            Row(Modifier.padding(horizontal = 48.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                pinned.forEachIndexed { i, app ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        AppTile(
                            app = app,
                            labels = "never",
                            modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            onClick = { vm.launchApp(context, app) },
                        )
                        Text(app.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text, maxLines = 1,
                            modifier = Modifier.padding(start = 4.dp))
                        Text(status(app.packageName, tv, mayo), fontSize = 11.sp, color = Palette.Muted, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
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
                        image = p.posterUri, title = p.title,
                        subtitle = p.subtitle.ifBlank { apps.firstOrNull { it.packageName == p.packageName }?.label.orEmpty() },
                        progress = p.progress, onClick = { vm.openProgram(context, p) },
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
                        image = p.posterUri, title = p.title, subtitle = p.subtitle,
                        width = if (wide) 180.dp else 110.dp, aspect = if (wide) 16f / 9f else 2f / 3f,
                        progress = p.progress, live = p.live, onClick = { vm.openProgram(context, p) },
                    )
                }
            }
        }
        if (channels.isEmpty() && tv.watchNext.isEmpty() && !tv.permissionMissing) {
            item(key = "empty") {
                Box(Modifier.padding(horizontal = 52.dp)) {
                    Text(
                        "Hier erscheinen die Kanäle deiner Apps, z. B. „Weiter schauen“ aus Nuvio oder „Für dich“ aus Mayo TV, sobald die Apps welche anbieten.",
                        fontSize = 12.sp, color = Palette.Muted,
                    )
                }
            }
        }
    }
}

/** Eine Zeile unter der App-Kachel: was in der App gerade wartet. */
private fun status(pkg: String, tv: TvSnapshot, mayo: MayoSnapshot): String {
    if (pkg == TvSnapshot.MAYO_TV) {
        tv.mayo(TvSnapshot.MAYO_FOR_YOU).firstOrNull { it.live }?.let { return "Läuft: ${it.title}" }
        mayo.recent.firstOrNull()?.let { return "Zuletzt: ${it.name}" + (it.nowTitle?.let { t -> " · $t" } ?: "") }
    }
    tv.watchNext.firstOrNull { it.packageName == pkg }?.let { return "Weiter: ${it.title}" }
    tv.channels.firstOrNull { it.packageName == pkg }?.programs?.firstOrNull()?.let { return it.title }
    return ""
}
