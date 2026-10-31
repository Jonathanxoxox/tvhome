package io.github.jonathanxoxox.mayohome.ui

import android.content.Context
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.tv.material3.Text
import io.github.jonathanxoxox.mayohome.BuildConfig
import io.github.jonathanxoxox.mayohome.data.Backup
import io.github.jonathanxoxox.mayohome.data.HighlightKind
import io.github.jonathanxoxox.mayohome.data.Settings
import io.github.jonathanxoxox.mayohome.data.StartRow
import io.github.jonathanxoxox.mayohome.data.TvChannelsRepository
import io.github.jonathanxoxox.mayohome.system.Launcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class Category(val key: String, val label: String)

private val CATEGORIES = listOf(
    Category("start", "Start"),
    Category("live", "Live TV"),
    Category("channels", "Sender-Katalog"),
    Category("apps", "Apps"),
    Category("look", "Aussehen"),
    Category("launcher", "Hauptlauncher"),
    Category("backup", "Sichern & Schutz"),
    Category("about", "Über"),
)

@Composable
fun SettingsScreen(
    vm: HomeViewModel,
    settings: Settings,
    onClose: () -> Unit,
    onRequestTvPermission: () -> Unit,
) {
    var unlocked by remember { mutableStateOf(settings.settingsPin.isBlank()) }
    if (!unlocked) {
        PinGate(settings.settingsPin, onUnlock = { unlocked = true }, onCancel = onClose)
        return
    }
    var category by remember { mutableStateOf("start") }
    var sub by remember { mutableStateOf<String?>(null) }
    val categoryFocus = remember { CATEGORIES.map { FocusRequester() } }
    val contentFocus = remember { FocusRequester() }
    var wasInSub by remember { mutableStateOf(false) }
    BackHandler { if (sub != null) sub = null else onClose() }
    LaunchedEffect(Unit) { runCatching { categoryFocus[0].requestFocus() } }
    // Wechselt die rechte Seite, verschwindet der ausgewählte Eintrag. Den Fokus deshalb gezielt
    // weitergeben: in eine Unterseite hinein, oder beim Verlassen zurück auf die Kategorie.
    LaunchedEffect(sub) {
        if (sub != null) {
            wasInSub = true
            delay(80)
            runCatching { contentFocus.requestFocus() }
        } else if (wasInSub) {
            wasInSub = false
            delay(30)
            val index = CATEGORIES.indexOfFirst { it.key == category }.coerceAtLeast(0)
            runCatching { categoryFocus[index].requestFocus() }
        }
    }

    Row(Modifier.fillMaxSize().background(Color(0xFF0D1113))) {
        Column(
            Modifier.width(240.dp).fillMaxHeight().padding(start = 48.dp, top = 40.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Einstellungen", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Text, modifier = Modifier.padding(bottom = 14.dp, start = 12.dp))
            CATEGORIES.forEachIndexed { i, c ->
                val selected = category == c.key && sub == null
                FocusCard(
                    onClick = { category = c.key; sub = null },
                    onFocus = { if (it) { category = c.key; sub = null } },
                    container = if (selected) Palette.Card else Color.Transparent,
                    scale = 1.03f,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).focusRequester(categoryFocus[i]),
                ) {
                    Text(c.label, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = Palette.Text, modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 14.dp))
                }
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxHeight().focusRequester(contentFocus).focusGroup(),
            contentPadding = PaddingValues(start = 16.dp, end = 64.dp, top = 76.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (sub) {
                null -> when (category) {
                    "start" -> homeItems(vm, settings) { sub = it }
                    "live" -> startItems(vm, settings) { sub = it }
                    "channels" -> channelItems(vm, settings) { sub = it }
                    "apps" -> appItems(vm, settings) { sub = it }
                    "look" -> lookItems(vm, settings)
                    "launcher" -> item { LauncherSection(vm, settings, onRequestTvPermission) { sub = it } }
                    "backup" -> item { BackupSection(vm, settings) { sub = it } }
                    "about" -> aboutItems(vm, settings) { sub = it }
                }
                "teams" -> item { TeamsEditor(vm, settings) { sub = null } }
                "kinds" -> kindItems(vm, settings)
                "rows" -> rowItems(vm, settings)
                "channelPicker" -> channelPicker(vm, settings)
                "channelOrder" -> channelOrder(vm, settings)
                "hiddenApps" -> hiddenAppItems(vm, settings)
                "appChannels" -> appChannelItems(vm, settings)
                "bootApp" -> bootAppItems(vm, settings) { sub = null }
                "homeApps" -> homeAppItems(vm, settings)
                "category" -> categoryItems(vm, settings) { sub = null }
                "pin" -> item { PinEditor(vm, settings) { sub = null } }
                "diagnose" -> item { DiagnoseSection(vm, settings) }
            }
        }
    }
}

// ---------- Bausteine ----------

@Composable
private fun SettingRow(title: String, subtitle: String? = null, value: String? = null, toggle: Boolean? = null, onClick: () -> Unit) {
    FocusCard(onClick = onClick, scale = 1.02f, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Palette.Text)
                if (!subtitle.isNullOrBlank()) Text(subtitle, fontSize = 11.sp, lineHeight = 15.sp, color = Palette.Muted)
            }
            if (value != null) Text(value, fontSize = 13.sp, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp).width(160.dp))
            if (toggle != null) Box(Modifier.padding(start = 12.dp)) { SwitchPill(toggle) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.Muted, modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 2.dp))
}

@Composable
private fun InfoBox(text: String) {
    Text(
        text, fontSize = 12.sp, lineHeight = 17.sp, color = Palette.Muted,
        modifier = Modifier.fillMaxWidth().border(1.dp, Palette.Line, RoundedCornerShape(12.dp)).padding(14.dp),
    )
}

private fun <T> next(options: List<T>, current: T): T = options[(options.indexOf(current) + 1).mod(options.size)]

// ---------- Startseite ----------

private fun LazyListScope.startItems(vm: HomeViewModel, s: Settings, open: (String) -> Unit) {
    item { SectionTitle("Kategorie unter dem Poster") }
    item {
        SettingRow("Kategorie aus Mayo TV", "Eine Gruppe deiner Playlist, z. B. „Magenta TV“",
            value = s.liveCategoryName.ifBlank { "keine" }) { open("category") }
    }
    item { SectionTitle("Großes Poster") }
    item { SettingRow("Fußball zuerst", "Spiele deiner Teams stehen immer vorne", toggle = s.footballFirst) { vm.updateSettings { it.copy(footballFirst = !it.footballFirst) } } }
    item { SettingRow("Lieblingsteams", "Werden im Programm erkannt, z. B. „Bayern“, „Schalke“", value = s.favoriteTeams.joinToString(", ").ifBlank { "keine" }) { open("teams") } }
    item {
        val labels = HighlightKind.entries.filter { it.key in s.highlightKinds }.joinToString(", ") { it.label }
        SettingRow("Was als Highlight gilt", value = labels.ifBlank { "nichts" }) { open("kinds") }
    }
    item { SettingRow("Spiele aus Mayo TV", "Die Kachel „Für dich“ aus Mayo TV mit deinen Teams", toggle = s.heroUseMayo) { vm.updateSettings { it.copy(heroUseMayo = !it.heroUseMayo) } } }
    item { SettingRow("Highlights aus dem TV-Programm", "Mit dem Bild aus der TV-Zeitschrift (MagentaTV)", toggle = s.heroUseEpg) { vm.updateSettings { it.copy(heroUseEpg = !it.heroUseEpg) } } }
    item { SettingRow("Weiter schauen im Poster", "Angefangene Filme und Folgen, z. B. aus Nuvio", toggle = s.heroUseWatchNext) { vm.updateSettings { it.copy(heroUseWatchNext = !it.heroUseWatchNext) } } }
    item {
        val options = listOf(0, 6, 10, 15, 20, 30)
        SettingRow("Automatisch wechseln", value = if (s.heroRotateSeconds == 0) "aus" else "alle ${s.heroRotateSeconds} s") {
            vm.updateSettings { it.copy(heroRotateSeconds = next(options, it.heroRotateSeconds.takeIf { v -> v in options } ?: 10)) }
        }
    }
    item {
        val options = listOf(3, 4, 6, 8, 10)
        SettingRow("Anzahl Highlights", value = "${s.heroMax}") { vm.updateSettings { it.copy(heroMax = next(options, it.heroMax.takeIf { v -> v in options } ?: 6)) } }
    }
    item {
        val options = listOf(1, 2, 3, 4, 6)
        SettingRow("Vorschau auf kommende Sendungen", value = "${s.heroLookaheadHours} Std.") {
            vm.updateSettings { it.copy(heroLookaheadHours = next(options, it.heroLookaheadHours.takeIf { v -> v in options } ?: 3)) }
        }
    }
    item { SectionTitle("Reihen unter dem Poster") }
    item { SettingRow("Reihen und Reihenfolge", value = s.rows.filter { it !in s.hiddenRows }.mapNotNull { StartRow.of(it)?.label }.joinToString(", ")) { open("rows") } }
}

// ---------- Start ----------

private val TABS = linkedMapOf("start" to "Start", "live" to "Live TV", "apps" to "Apps")

private fun LazyListScope.homeItems(vm: HomeViewModel, s: Settings, open: (String) -> Unit) {
    item { SectionTitle("Apps oben") }
    item {
        val apps by vm.apps.collectAsState()
        SettingRow("Apps auf der Startseite", "Am besten vier; lange OK auf eine App im Reiter Apps geht auch",
            value = homeApps(apps, s).joinToString(", ") { it.label }) { open("homeApps") }
    }
    item {
        val bgs = linkedMapOf("aurora" to "Sanfter Verlauf", "plain" to "Schlicht", "content" to "Bild des Ausgewählten")
        SettingRow("Hintergrund", value = bgs[s.startBg]) { vm.updateSettings { it.copy(startBg = next(bgs.keys.toList(), it.startBg.takeIf { b -> b in bgs } ?: "aurora")) } }
    }
    item { SectionTitle("Kanäle der Apps") }
    item { SettingRow("Kanäle unter den Apps zeigen", "Reihen, die Apps für den Startbildschirm anbieten (wie in Projectivy)", toggle = s.showAppChannels) { vm.updateSettings { it.copy(showAppChannels = !it.showAppChannels) } } }
    item { SettingRow("Einzelne Kanäle ein- und ausblenden", value = "${s.hiddenAppChannels.size} ausgeblendet") { open("appChannels") } }
    item { SectionTitle("Beim Öffnen") }
    item {
        SettingRow("Reiter beim Öffnen", value = TABS[s.startTab] ?: "Start") {
            vm.updateSettings { it.copy(startTab = next(TABS.keys.toList(), it.startTab.takeIf { t -> t in TABS } ?: "start")) }
        }
    }
}

private fun LazyListScope.homeAppItems(vm: HomeViewModel, s: Settings) {
    item { SectionTitle("OK nimmt eine App auf oder heraus · lange drücken schiebt sie nach vorne") }
    item {
        val apps by vm.apps.collectAsState()
        val chosen = s.favoriteApps.ifEmpty { homeApps(apps, s).map { it.packageName } }
        val sorted = chosen.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } } + apps.filter { it.packageName !in chosen }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sorted.forEach { app ->
                val on = app.packageName in chosen
                FocusCard(
                    onClick = {
                        vm.updateSettings {
                            val base = it.favoriteApps.ifEmpty { chosen }
                            it.copy(favoriteApps = if (app.packageName in base) base - app.packageName else base + app.packageName)
                        }
                    },
                    onLongClick = {
                        vm.updateSettings {
                            val list = it.favoriteApps.ifEmpty { chosen }.toMutableList()
                            val i = list.indexOf(app.packageName)
                            if (i > 0) list.add(i - 1, list.removeAt(i))
                            it.copy(favoriteApps = list)
                        }
                    },
                    scale = 1.02f,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        val pos = chosen.indexOf(app.packageName)
                        Text(if (pos >= 0) "${pos + 1}." else "", fontSize = 14.sp, color = Palette.Muted, modifier = Modifier.width(32.dp))
                        Text(app.label, fontSize = 14.sp, color = Palette.Text, modifier = Modifier.weight(1f))
                        SwitchPill(on)
                    }
                }
            }
        }
    }
}

private fun LazyListScope.categoryItems(vm: HomeViewModel, s: Settings, onDone: () -> Unit) {
    item { SectionTitle("Welche Kategorie aus Mayo TV unter dem Poster stehen soll") }
    item {
        val mayo by vm.mayo.collectAsState()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!mayo.available) {
                InfoBox("Mayo TV liefert keine Kategorien. Entweder ist Mayo TV nicht installiert oder noch zu alt: Die Schnittstelle für Mayo Home kommt mit dem nächsten Update von Mayo TV." +
                    (vm.mayoError?.let { "\n\n$it" } ?: ""))
                SettingRow("Neu laden") { vm.refreshMayo() }
            }
            if (mayo.available) {
                SettingRow("Keine", toggle = s.liveCategoryId < 0) {
                    vm.updateSettings { it.copy(liveCategoryId = -1L, liveCategoryName = "") }
                    onDone()
                }
            }
            mayo.categories.forEach { cat ->
                SettingRow(cat.name, toggle = s.liveCategoryId == cat.id) {
                    vm.updateSettings { it.copy(liveCategoryId = cat.id, liveCategoryName = cat.name) }
                    onDone()
                }
            }
        }
    }
}

private fun LazyListScope.kindItems(vm: HomeViewModel, s: Settings) {
    item { SectionTitle("Was als Highlight im Poster erscheinen darf") }
    items(HighlightKind.entries) { k ->
        SettingRow(k.label, toggle = k.key in s.highlightKinds) {
            vm.updateSettings { it.copy(highlightKinds = if (k.key in it.highlightKinds) it.highlightKinds - k.key else it.highlightKinds + k.key) }
        }
    }
}

private fun LazyListScope.rowItems(vm: HomeViewModel, s: Settings) {
    item { SectionTitle("OK blendet ein oder aus · lange drücken schiebt nach oben") }
    val all = s.rows + StartRow.entries.map { it.key }.filter { it !in s.rows }
    items(all) { key ->
        val row = StartRow.of(key) ?: return@items
        val visible = key in s.rows && key !in s.hiddenRows
        FocusCard(
            onClick = {
                vm.updateSettings {
                    when {
                        key !in it.rows -> it.copy(rows = it.rows + key, hiddenRows = it.hiddenRows - key)
                        key in it.hiddenRows -> it.copy(hiddenRows = it.hiddenRows - key)
                        else -> it.copy(hiddenRows = it.hiddenRows + key)
                    }
                }
            },
            onLongClick = {
                vm.updateSettings {
                    val list = (it.rows + if (key in it.rows) emptyList() else listOf(key)).toMutableList()
                    val i = list.indexOf(key)
                    if (i > 0) list.add(i - 1, list.removeAt(i))
                    it.copy(rows = list)
                }
            },
            scale = 1.02f,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(row.label, fontSize = 14.sp, color = Palette.Text, modifier = Modifier.weight(1f))
                SwitchPill(visible)
            }
        }
    }
}

@Composable
private fun TeamsEditor(vm: HomeViewModel, s: Settings, onDone: () -> Unit) {
    var text by remember { mutableStateOf(s.favoriteTeams.joinToString(", ")) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Lieblingsteams, mit Komma getrennt")
        TextInput(text, { text = it }, focus, KeyboardType.Text) {
            vm.updateSettings { it.copy(favoriteTeams = text.split(',').map { t -> t.trim() }.filter { t -> t.isNotEmpty() }) }
            onDone()
        }
        InfoBox("Mayo Home sucht diese Namen in den Titeln der Sendungen, z. B. „Bayern München – Dortmund“. Kurze, eindeutige Namen wie „Bayern“ oder „Leverkusen“ funktionieren am besten. Spiele, die Mayo TV schon in „Für dich“ zeigt, erscheinen ohnehin.")
        SettingRow("Speichern") {
            vm.updateSettings { it.copy(favoriteTeams = text.split(',').map { t -> t.trim() }.filter { t -> t.isNotEmpty() }) }
            onDone()
        }
    }
}

@Composable
private fun TextInput(value: String, onChange: (String) -> Unit, focus: FocusRequester, type: KeyboardType, onDone: () -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = Palette.Text, fontSize = 16.sp, fontFamily = Inter),
        cursorBrush = SolidColor(Palette.Text),
        keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth().focusRequester(focus)
            .background(Palette.Card, RoundedCornerShape(12.dp)).border(2.dp, Palette.Text, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

// ---------- Sender ----------

private fun LazyListScope.channelItems(vm: HomeViewModel, s: Settings, open: (String) -> Unit) {
    item { SectionTitle("Deine Sender für die Reihe „Sender“ und die Highlights") }
    item { SettingRow("Sender auswählen", "Aus dem Katalog mit über 300 Sendern", value = "${s.channels.size} gewählt") { open("channelPicker") } }
    item { SettingRow("Reihenfolge ändern", value = s.channels.take(3).joinToString(", ") { vm.channelName(it) }) { open("channelOrder") } }
    item { SettingRow("Programm neu laden") { vm.refreshEpg(force = true) } }
    item {
        InfoBox(
            (vm.epgError?.let { "Letzter Stand: $it\n\n" } ?: "") +
                "Bilder und Programm kommen von MagentaTV (dieselben Daten wie in deren TV-Zeitschrift). Das ist keine offizielle Schnittstelle und funktioniert womöglich nur in Deutschland. " +
                "Beim Klick auf einen Sender öffnet Mayo Home ihn in Mayo TV, wenn der Sender dort unter deinen Favoriten oder zuletzt gesehenen steht; sonst öffnet sich Mayo TV.",
        )
    }
}

private fun LazyListScope.channelPicker(vm: HomeViewModel, s: Settings) {
    item { SectionTitle("OK wählt einen Sender aus oder ab") }
    items(vm.catalog, key = { it.id }) { ch ->
        SettingRow(ch.name, toggle = ch.id in s.channels) {
            vm.updateSettings { if (ch.id in it.channels) it.copy(channels = it.channels - ch.id) else it.copy(channels = it.channels + ch.id) }
        }
    }
}

private fun LazyListScope.channelOrder(vm: HomeViewModel, s: Settings) {
    item { SectionTitle("OK schiebt einen Sender eine Stelle nach vorne · lange drücken entfernt ihn") }
    items(s.channels, key = { it }) { id ->
        FocusCard(
            onClick = {
                vm.updateSettings {
                    val list = it.channels.toMutableList()
                    val i = list.indexOf(id)
                    if (i > 0) list.add(i - 1, list.removeAt(i))
                    it.copy(channels = list)
                }
            },
            onLongClick = { vm.updateSettings { it.copy(channels = it.channels - id) } },
            scale = 1.02f,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
                Text("${s.channels.indexOf(id) + 1}.", fontSize = 14.sp, color = Palette.Muted, modifier = Modifier.width(36.dp))
                Text(vm.channelName(id), fontSize = 14.sp, color = Palette.Text)
            }
        }
    }
}

// ---------- Apps ----------

private fun LazyListScope.appItems(vm: HomeViewModel, s: Settings, open: (String) -> Unit) {
    item { SectionTitle("Raster") }
    item { SettingRow("Spalten", value = "${s.appColumns}") { vm.updateSettings { it.copy(appColumns = next(listOf(4, 5, 6, 7, 8), it.appColumns)) } } }
    item {
        val labels = mapOf("focus" to "nur beim Auswählen", "always" to "immer", "never" to "nie")
        SettingRow("App-Namen anzeigen", value = labels[s.appLabels]) { vm.updateSettings { it.copy(appLabels = next(labels.keys.toList(), it.appLabels)) } }
    }
    item {
        val sorts = mapOf("manual" to "eigene Reihenfolge", "alpha" to "alphabetisch", "recent" to "zuletzt benutzt")
        SettingRow("Sortierung", "Eigene Reihenfolge: lange auf eine App drücken und verschieben", value = sorts[s.appSort]) {
            vm.updateSettings { it.copy(appSort = next(sorts.keys.toList(), it.appSort)) }
        }
    }
    item { SettingRow("Ausgeblendete Apps", value = "${s.hiddenApps.size}") { open("hiddenApps") } }
    item { SettingRow("Reihenfolge zurücksetzen") { vm.updateSettings { it.copy(appOrder = emptyList()) } } }
}

private fun LazyListScope.hiddenAppItems(vm: HomeViewModel, s: Settings) {
    item { SectionTitle("OK blendet eine App ein oder aus") }
    item {
        val apps by vm.apps.collectAsState()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            apps.forEach { app ->
                SettingRow(app.label, toggle = app.packageName !in s.hiddenApps) {
                    vm.updateSettings {
                        if (app.packageName in it.hiddenApps) it.copy(hiddenApps = it.hiddenApps - app.packageName)
                        else it.copy(hiddenApps = it.hiddenApps + app.packageName)
                    }
                }
            }
        }
    }
}

private fun LazyListScope.appChannelItems(vm: HomeViewModel, s: Settings) {
    item { SectionTitle("OK blendet einen Kanal ein oder aus") }
    item {
        val tv by vm.tv.collectAsState()
        val apps by vm.apps.collectAsState()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tv.channels.isEmpty()) InfoBox("Noch keine Kanäle gefunden. Entweder bieten deine Apps keine an, oder Mayo Home darf sie noch nicht lesen (siehe Hauptlauncher → TV-Programmdaten).")
            tv.channels.forEach { ch ->
                val appName = apps.firstOrNull { it.packageName == ch.packageName }?.label ?: ch.packageName
                SettingRow(ch.name, appName, toggle = ch.key !in s.hiddenAppChannels) {
                    vm.updateSettings {
                        if (ch.key in it.hiddenAppChannels) it.copy(hiddenAppChannels = it.hiddenAppChannels - ch.key)
                        else it.copy(hiddenAppChannels = it.hiddenAppChannels + ch.key)
                    }
                }
            }
        }
    }
}

private fun LazyListScope.bootAppItems(vm: HomeViewModel, s: Settings, onDone: () -> Unit) {
    item { SectionTitle("Was beim Einschalten der Box öffnet") }
    item { SettingRow("Mayo Home", toggle = s.bootApp.isBlank()) { vm.updateSettings { it.copy(bootApp = "") }; onDone() } }
    item {
        val apps by vm.apps.collectAsState()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            apps.forEach { app ->
                SettingRow(app.label, toggle = s.bootApp == app.packageName) { vm.updateSettings { it.copy(bootApp = app.packageName) }; onDone() }
            }
        }
    }
}

// ---------- Aussehen ----------

private fun LazyListScope.lookItems(vm: HomeViewModel, s: Settings) {
    item { SectionTitle("Farben") }
    item { SettingRow("Akzentfarbe", "Für Fortschrittsbalken und Schalter", value = Palette.Accents[s.accent]?.first) { vm.updateSettings { it.copy(accent = next(Palette.Accents.keys.toList(), it.accent)) } } }
    item {
        val bgs = mapOf("black" to "Schwarz", "aurora" to "Sanfter Verlauf", "petrol" to "Petrol", "night" to "Nachtblau", "hero" to "Bild des Posters")
        SettingRow("Hintergrund für Live TV und Apps", value = bgs[s.background]) { vm.updateSettings { it.copy(background = next(bgs.keys.toList(), it.background)) } }
    }
    item { SectionTitle("Form und Bewegung") }
    item {
        val corners = mapOf("small" to "klein", "medium" to "mittel", "large" to "groß")
        SettingRow("Ecken", value = corners[s.corners]) { vm.updateSettings { it.copy(corners = next(corners.keys.toList(), it.corners)) } }
    }
    item {
        val scales = mapOf("off" to "aus", "small" to "leicht", "medium" to "mittel", "large" to "stark")
        SettingRow("Vergrößern beim Auswählen", value = scales[s.focusScale]) { vm.updateSettings { it.copy(focusScale = next(scales.keys.toList(), it.focusScale)) } }
    }
    item { SettingRow("Animationen", toggle = s.animations) { vm.updateSettings { it.copy(animations = !it.animations) } } }
    item { SettingRow("Größe der Oberfläche", "Für weit entfernte oder kleine Fernseher", value = "${s.uiScale} %") { vm.updateSettings { it.copy(uiScale = next(listOf(85, 90, 100, 110, 120), it.uiScale)) } } }
    item { SectionTitle("Uhr") }
    item { SettingRow("24-Stunden-Uhr", toggle = s.clock24) { vm.updateSettings { it.copy(clock24 = !it.clock24) } } }
    item { SettingRow("Datum anzeigen", toggle = s.showDate) { vm.updateSettings { it.copy(showDate = !it.showDate) } } }
}

// ---------- Hauptlauncher ----------

@Composable
private fun LauncherSection(vm: HomeViewModel, s: Settings, onRequestTvPermission: () -> Unit, open: (String) -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val isDefault = remember(tick) { Launcher.isDefaultHome(context) }
    val guard = remember(tick) { Launcher.isGuardEnabled(context) }
    val overlay = remember(tick) { Launcher.canDrawOverlays(context) }
    val epg = remember(tick) {
        TvChannelsRepository.PERMISSIONS.all { context.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED }
    }
    val bootLabel = s.bootApp.takeIf { it.isNotBlank() }?.let { pkg -> vm.apps.value.firstOrNull { it.packageName == pkg }?.label ?: pkg } ?: "Mayo Home"

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Home-Taste")
        InfoBox(
            when {
                isDefault -> "Mayo Home ist der Standard-Startbildschirm. Die Home-Taste führt immer hierher."
                guard && s.homeOverride -> "Der Home-Wächter ist aktiv: Erscheint der alte Startbildschirm, öffnet sich sofort Mayo Home."
                else -> "Noch landet die Home-Taste beim vorinstallierten Startbildschirm. Schalte unten den Home-Wächter ein, oder mache Mayo Home per ADB zum Standard."
            },
        )
        SettingRow("Home-Taste immer zu Mayo Home", "Nutzt den Home-Wächter in den Bedienungshilfen", toggle = s.homeOverride) { vm.updateSettings { it.copy(homeOverride = !it.homeOverride) } }
        val canWrite = remember(tick) { Launcher.canWriteSecure(context) }
        SettingRow(
            "Home-Wächter einschalten",
            if (canWrite) "Mayo Home trägt sich selbst ein, andere Dienste bleiben an" else "Bedienungshilfen → Mayo Home: Home-Taste",
            value = if (guard) "an" else "aus",
        ) {
            if (Launcher.enableGuard(context)) { tick++; toast(context, "Home-Wächter ist an.") }
            else Launcher.openSystemPage(context, AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)
        }
        SettingRow("Standard-Startbildschirm", value = if (isDefault) "Mayo Home" else "anderer") {
            Launcher.openSystemPage(context, AndroidSettings.ACTION_HOME_SETTINGS)
        }
        SectionTitle("Einschalten")
        SettingRow("Beim Einschalten öffnen", toggle = s.openOnBoot) { vm.updateSettings { it.copy(openOnBoot = !it.openOnBoot) } }
        SettingRow("Was öffnet", value = bootLabel) { open("bootApp") }
        SettingRow("Start aus dem Hintergrund erlauben", "Hilft beim Öffnen nach dem Einschalten", value = if (overlay) "erlaubt" else "nicht erlaubt") {
            Launcher.openSystemPage(context, AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
        }
        SectionTitle("Berechtigungen")
        SettingRow("TV-Programmdaten lesen", "Für „Weiter schauen“ und die Kanäle deiner Apps", value = if (epg) "erlaubt" else "nicht erlaubt") {
            onRequestTvPermission()
            vm.refreshTv()
        }
        SettingRow("Kanäle der Apps neu laden") { vm.refreshTv(); vm.refreshApps() }
        SectionTitle("Per ADB (falls ein Menü fehlt)")
        InfoBox(
            "Home-Wächter (einmal erlauben, danach schaltet Mayo Home ihn selbst ein, ohne Projectivy oder Button Mapper abzuschalten):\n${Launcher.guardAdbCommand(context)}\n\n" +
                "Mayo Home zum Standard machen:\n${Launcher.homeAdbCommand(context)}\n\n" +
                "Start aus dem Hintergrund:\n${Launcher.overlayAdbCommand(context)}\n\n" +
                "TV-Programmdaten:\n${Launcher.epgAdbCommand(context)}",
        )
    }
}

// ---------- Sichern & Schutz ----------

@Composable
private fun BackupSection(vm: HomeViewModel, s: Settings, open: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Sichern")
        SettingRow("Einstellungen exportieren", "Als ${Backup.FILE_NAME} im Download-Ordner") {
            scope.launch { toast(context, runCatching { "Gespeichert: " + Backup.export(context, s) }.getOrElse { "Fehler: ${it.message}" }) }
        }
        SettingRow("Einstellungen importieren", "Liest dieselbe Datei wieder ein") {
            scope.launch {
                val loaded = runCatching { Backup.import(context) }.getOrNull()
                if (loaded == null) toast(context, "Keine Sicherung gefunden.") else { vm.replaceSettings(loaded); toast(context, "Einstellungen übernommen.") }
            }
        }
        SettingRow("Auf Standard zurücksetzen") { vm.replaceSettings(Settings()); toast(context, "Zurückgesetzt.") }
        SectionTitle("Schutz")
        SettingRow("PIN für die Einstellungen", "Damit niemand versehentlich etwas verstellt", value = if (s.settingsPin.isBlank()) "aus" else "an") { open("pin") }
    }
}

@Composable
private fun PinEditor(vm: HomeViewModel, s: Settings, onDone: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Neue PIN (4 Ziffern), leer lassen zum Abschalten")
        TextInput(text, { if (it.length <= 4 && it.all(Char::isDigit)) text = it }, focus, KeyboardType.NumberPassword) {
            vm.updateSettings { it.copy(settingsPin = text) }
            onDone()
        }
        SettingRow(if (text.isBlank()) "PIN abschalten" else "PIN speichern") {
            vm.updateSettings { it.copy(settingsPin = text) }
            onDone()
        }
        if (s.settingsPin.isNotBlank()) InfoBox("Aktuell ist eine PIN gesetzt.")
    }
}

@Composable
private fun PinGate(pin: String, onUnlock: () -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onCancel() }
    Box(Modifier.fillMaxSize().background(Color(0xF20D1113)), contentAlignment = Alignment.Center) {
        Column(Modifier.width(320.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("PIN eingeben", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
            TextInput(text, {
                if (it.length <= 4 && it.all(Char::isDigit)) text = it
                if (it == pin) onUnlock()
            }, focus, KeyboardType.NumberPassword) { if (text == pin) onUnlock() }
        }
    }
}

// ---------- Über ----------

private fun LazyListScope.aboutItems(vm: HomeViewModel, s: Settings, open: (String) -> Unit) {
    item { SectionTitle("Mayo Home") }
    item { InfoBox("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n\nEin eigener Startbildschirm für Android TV: Highlights aus deinen Sendern, Spiele deiner Teams aus Mayo TV, „Weiter schauen“ aus deinen Apps und alle Apps auf einer Seite.") }
    item { SectionTitle("Updates") }
    item { UpdateRow(vm) }
    item { SettingRow("Automatisch nach Updates suchen", "Beim Start und alle 6 Stunden; oben erscheint dann ein Hinweis", toggle = s.autoUpdate) { vm.updateSettings { it.copy(autoUpdate = !it.autoUpdate) } } }
    item { SectionTitle("Hilfe") }
    item { SettingRow("Diagnose", "Was Mayo Home gerade findet: Programm, Kanäle, Berechtigungen") { open("diagnose") } }
    item { SettingRow("Alles neu laden") { vm.refreshApps(); vm.refreshTv(); vm.refreshEpg(force = true) } }
    item { CrashSection() }
}

@Composable
private fun CrashSection() {
    val context = LocalContext.current
    var text by remember { mutableStateOf(io.github.jonathanxoxox.mayohome.system.CrashLog.last(context)) }
    val crash = text ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Letzter Absturz")
        // Auswählbar, damit man mit der Fernbedienung dorthin scrollen und es abfotografieren kann.
        FocusCard(onClick = {}, scale = 1f, container = Color.Transparent, focusedContainer = Palette.Card, modifier = Modifier.fillMaxWidth()) {
            Text(crash.lines().take(14).joinToString("\n"), fontSize = 11.sp, lineHeight = 15.sp, color = Palette.Muted,
                modifier = Modifier.fillMaxWidth().border(1.dp, Palette.Line, RoundedCornerShape(12.dp)).padding(14.dp))
        }
        SettingRow("Absturzbericht löschen") { io.github.jonathanxoxox.mayohome.system.CrashLog.clear(context); text = null }
    }
}

@Composable
private fun UpdateRow(vm: HomeViewModel) {
    val state by vm.update.collectAsState()
    val (title, value) = when (val u = state) {
        HomeViewModel.UpdateState.Idle -> "Nach Updates suchen" to null
        HomeViewModel.UpdateState.Checking -> "Suche …" to null
        HomeViewModel.UpdateState.UpToDate -> "Nach Updates suchen" to "aktuell"
        is HomeViewModel.UpdateState.Available -> "Jetzt aktualisieren" to "Version ${u.info.versionName}"
        is HomeViewModel.UpdateState.Downloading -> "Lade herunter" to "${(u.progress * 100).toInt()} %"
        is HomeViewModel.UpdateState.Installing -> "Installiere" to "Version ${u.info.versionName}"
        is HomeViewModel.UpdateState.Failed -> "Nochmal versuchen" to u.message
    }
    SettingRow(title, "Lädt die neueste APK von GitHub und installiert sie", value = value) {
        if (state is HomeViewModel.UpdateState.Available) vm.installUpdate() else vm.checkForUpdate(manual = true)
    }
}

private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
