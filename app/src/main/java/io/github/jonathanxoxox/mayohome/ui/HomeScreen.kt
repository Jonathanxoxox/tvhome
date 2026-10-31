package io.github.jonathanxoxox.mayohome.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
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
    val epg by vm.epg.collectAsState()
    val now by vm.now.collectAsState()
    val pinned = remember(apps, settings.favoriteApps) { homeApps(apps, settings) }
    var focusedPkg by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val shelfApp = pinned.firstOrNull { it.packageName == focusedPkg } ?: pinned.firstOrNull()
    val shelf = remember(shelfApp, tv, mayo, epg, now) { shelfApp?.let { shelfFor(it, vm, tv, mayo, epg, now) } }
    val order = pinned.map { it.packageName }
    val channels = remember(tv, settings) {
        if (!settings.showAppChannels) emptyList()
        else tv.channels.filter { it.key !in settings.hiddenAppChannels }
            .sortedBy { ch -> order.indexOf(ch.packageName).let { if (it < 0) Int.MAX_VALUE else it } }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 0.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item(key = "shelf") { TopShelf(shelf) }
        item(key = "apps") {
            Row(Modifier.padding(horizontal = 48.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                pinned.forEachIndexed { i, app ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        AppTile(
                            app = app,
                            labels = "never",
                            modifier = (if (i == 0) Modifier.focusRequester(firstFocus) else Modifier).onFocusChanged {
                                if (it.isFocused) {
                                    focusedPkg = app.packageName
                                    // Zurück in der App-Reihe: das Top Shelf wieder ganz zeigen.
                                    if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
                                        scope.launch { listState.animateScrollToItem(0) }
                                    }
                                }
                            },
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

/** Was das Top Shelf für eine App zeigt. */
private data class Shelf(
    val kicker: String,
    val title: String,
    val meta: String,
    val image: Any?,
    val imageIsTile: Boolean,
    val live: Boolean,
    val progress: Float?,
)

/**
 * Inhalt der Fläche über den Apps: bei Mayo TV ein laufendes Spiel oder der zuletzt gesehene Sender,
 * sonst das Erste aus "Weiter schauen" oder aus einem Kanal der App, zur Not ihr Banner.
 */
private fun shelfFor(
    app: AppEntry,
    vm: HomeViewModel,
    tv: TvSnapshot,
    mayo: MayoSnapshot,
    epg: Map<String, List<io.github.jonathanxoxox.mayohome.data.EpgProgram>>,
    now: Long,
): Shelf {
    val pkg = app.packageName
    if (pkg == TvSnapshot.MAYO_TV) {
        tv.mayo(TvSnapshot.MAYO_FOR_YOU).firstOrNull { it.live }?.let {
            return Shelf("Live · Für dich", it.title, it.description, it.posterUri, true, true, it.progress)
        }
        mayo.recent.firstOrNull()?.let { ch ->
            val program = vm.catalogIdFor(ch.name)?.let { id -> epg[id].orEmpty().firstOrNull { it.runningAt(now) } }
            val title = ch.nowTitle ?: program?.title ?: ch.name
            return Shelf("Zuletzt gesehen · ${ch.name}", title, program?.genres?.split(',')?.firstOrNull()?.trim().orEmpty(),
                program?.imageUrl ?: ch.logo, program?.imageUrl == null, false, ch.progressAt(now) ?: program?.progressAt(now))
        }
    }
    tv.watchNext.firstOrNull { it.packageName == pkg }?.let {
        return Shelf("Weiter schauen", it.title, it.subtitle, it.posterUri, it.aspect < 1.2f, false, it.progress)
    }
    tv.channels.firstOrNull { it.packageName == pkg }?.let { ch ->
        ch.programs.firstOrNull()?.let {
            return Shelf(ch.name, it.title, it.subtitle.ifBlank { it.description }, it.posterUri, it.aspect < 1.2f, it.live, it.progress)
        }
    }
    return Shelf(app.label, app.label, "OK öffnet die App", app.banner, true, false, null)
}

@Composable
private fun TopShelf(shelf: Shelf?) {
    val page = LocalLook.current.page
    Box(Modifier.fillMaxWidth().height(230.dp)) {
        AnimatedContent(targetState = shelf, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "shelf") { item ->
            Box(Modifier.fillMaxSize()) {
                if (item == null) return@Box
                val image = item.image
                if (image != null && !item.imageIsTile) {
                    if (image is ImageBitmap) Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(heroScrim(page)))
                }
                if (image != null && item.imageIsTile) {
                    val m = Modifier.align(Alignment.CenterEnd).padding(end = 48.dp, top = 20.dp, bottom = 20.dp)
                        .fillMaxHeight().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp))
                    if (image is ImageBitmap) Image(image, null, contentScale = ContentScale.Fit, modifier = m)
                    else AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Fit, modifier = m)
                }
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(70.dp).background(Brush.verticalGradient(0f to Color.Transparent, 1f to page)))
                Column(Modifier.align(Alignment.CenterStart).padding(start = 52.dp).widthIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (item.live) Box(Modifier.size(7.dp).background(Palette.Live, CircleShape))
                        Text(item.kicker.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFDCE3E5), letterSpacing = 0.6.sp, maxLines = 1)
                    }
                    Text(item.title, fontSize = 28.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Palette.Text)
                    if (item.meta.isNotBlank()) Text(item.meta, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Color(0xFFC9D2D4))
                    if (item.progress != null && item.progress > 0f) {
                        Box(Modifier.padding(top = 2.dp).width(160.dp).height(3.dp).background(Color(0x40F5F7F7), RoundedCornerShape(2.dp))) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(item.progress).background(LocalLook.current.accent, RoundedCornerShape(2.dp)))
                        }
                    }
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
