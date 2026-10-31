package io.github.jonathanxoxox.mayohome.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import io.github.jonathanxoxox.mayohome.data.AppEntry
import io.github.jonathanxoxox.mayohome.data.Settings
import io.github.jonathanxoxox.mayohome.system.Launcher

/** Die Apps in der Reihenfolge, die die Einstellungen vorgeben, ohne die ausgeblendeten. */
fun orderedApps(apps: List<AppEntry>, s: Settings): List<AppEntry> {
    val visible = apps.filter { it.packageName !in s.hiddenApps }
    return when (s.appSort) {
        "alpha" -> visible.sortedBy { it.label.lowercase() }
        "recent" -> visible.sortedByDescending { s.appLastUsed[it.packageName] ?: 0L }
        else -> {
            val position = s.appOrder.withIndex().associate { (i, pkg) -> pkg to i }
            visible.sortedWith(compareBy<AppEntry> { position[it.packageName] ?: Int.MAX_VALUE }.thenBy { it.label.lowercase() })
        }
    }
}

@Composable
fun AppsScreen(vm: HomeViewModel, settings: Settings, firstFocus: FocusRequester) {
    val context = LocalContext.current
    val apps by vm.apps.collectAsState()
    val tv by vm.tv.collectAsState()
    val ordered = remember(apps, settings) { orderedApps(apps, settings) }
    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    val columns = settings.appColumns
    val rows = ordered.chunked(columns)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(rows.size, key = { "apps-$it" }) { r ->
            Row(Modifier.padding(horizontal = 48.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                rows[r].forEachIndexed { i, app ->
                    Box(Modifier.weight(1f)) {
                        AppTile(
                            app = app,
                            labels = settings.appLabels,
                            modifier = if (r == 0 && i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            onClick = { vm.launchApp(context, app) },
                            onLongClick = { menuFor = app },
                        )
                    }
                }
                repeat(columns - rows[r].size) { Spacer(Modifier.weight(1f)) }
            }
        }
        item(key = "system-settings") {
            Row(Modifier.padding(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallAction("Android-Einstellungen") { Launcher.openSystemPage(context, AndroidSettings.ACTION_SETTINGS) }
                if (settings.hiddenApps.isNotEmpty()) {
                    Text("${settings.hiddenApps.size} ausgeblendet · in den Einstellungen wieder einblenden",
                        fontSize = 11.sp, color = Palette.Muted, modifier = Modifier.align(Alignment.CenterVertically))
                }
            }
        }
        if (settings.showAppChannels) {
            val channels = tv.channels.filter { it.key !in settings.hiddenAppChannels }
            items(channels.size, key = { "ch-${channels[it].id}" }) { i ->
                val ch = channels[i]
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
    }

    menuFor?.let { app -> AppMenu(app, settings, vm, onDismiss = { menuFor = null }) }
}

@Composable
fun AppTile(
    app: AppEntry,
    labels: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    onLongClick: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val sized = if (width != null) modifier.width(width) else modifier.fillMaxWidth()
    Column(sized, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FocusCard(
            onClick = onClick,
            onLongClick = onLongClick,
            onFocus = { focused = it },
            modifier = Modifier.fillMaxWidth().aspectRatio16x9(),
        ) {
            if (app.banner != null) {
                Image(app.banner, contentDescription = app.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    app.icon?.let { Image(it, contentDescription = app.label, modifier = Modifier.size(40.dp)) }
                    Text(app.label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.Text, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        val showLabel = labels == "always" || (labels == "focus" && focused)
        Text(
            app.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, color = if (showLabel) Palette.Text else Color.Transparent,
        )
    }
}

private fun Modifier.aspectRatio16x9() = this.aspectRatio(16f / 9f)

@Composable
fun SmallAction(label: String, onClick: () -> Unit) {
    FocusCard(onClick = onClick, corner = 50.dp, scale = 1.05f) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

/** Menü bei langem Druck auf eine App: verschieben, ausblenden, Lieblings-App, App-Info, deinstallieren. */
@Composable
private fun AppMenu(app: AppEntry, settings: Settings, vm: HomeViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val apps by vm.apps.collectAsState()
    val first = remember { FocusRequester() }
    fun move(delta: Int) {
        vm.updateSettings { s ->
            val order = orderedApps(apps, s.copy(appSort = s.appSort)).map { it.packageName }.toMutableList()
            val from = order.indexOf(app.packageName)
            if (from < 0) return@updateSettings s
            val to = (from + delta).coerceIn(0, order.lastIndex)
            order.add(to, order.removeAt(from))
            s.copy(appOrder = order, appSort = "manual")
        }
    }
    val isFavorite = app.packageName in settings.favoriteApps
    val entries = listOf(
        "Öffnen" to { vm.launchApp(context, app) },
        "Nach links" to { move(-1) },
        "Nach rechts" to { move(1) },
        "Ganz nach vorne" to { move(-1000) },
        (if (isFavorite) "Von der Startseite entfernen" else "Auf die Startseite (Lieblings-Apps)") to {
            vm.updateSettings { s ->
                s.copy(
                    favoriteApps = if (isFavorite) s.favoriteApps - app.packageName else s.favoriteApps + app.packageName,
                    rows = if (!isFavorite && "apps" !in s.rows) s.rows + "apps" else s.rows,
                )
            }
        },
        "Beim Einschalten öffnen" to { vm.updateSettings { it.copy(bootApp = app.packageName, openOnBoot = true) } },
        "Ausblenden" to { vm.updateSettings { it.copy(hiddenApps = it.hiddenApps + app.packageName) } },
        "App-Info" to {
            Launcher.openSystemPage(context, AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
        },
        "Deinstallieren" to {
            Launcher.start(context, Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
        },
    )
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(320.dp).background(Color(0xFF161C1F), RoundedCornerShape(18.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(app.label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.Text, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
            entries.forEachIndexed { i, (label, action) ->
                FocusCard(
                    onClick = { action(); onDismiss() },
                    scale = 1.02f,
                    container = Color.Transparent,
                    modifier = Modifier.fillMaxWidth().height(40.dp).let { if (i == 0) it.focusRequester(first) else it },
                ) {
                    Text(label, fontSize = 13.sp, color = Palette.Text, modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 12.dp))
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}
