package io.github.jonathanxoxox.mayohome.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import io.github.jonathanxoxox.mayohome.data.AppEntry
import io.github.jonathanxoxox.mayohome.data.HeroItem
import io.github.jonathanxoxox.mayohome.data.MayoChannel
import io.github.jonathanxoxox.mayohome.data.MayoSnapshot
import io.github.jonathanxoxox.mayohome.system.Launcher
import io.github.jonathanxoxox.mayohome.data.Highlights
import io.github.jonathanxoxox.mayohome.data.Settings
import io.github.jonathanxoxox.mayohome.data.StartRow
import io.github.jonathanxoxox.mayohome.data.TvSnapshot
import kotlinx.coroutines.delay

@Composable
fun LiveScreen(
    vm: HomeViewModel,
    settings: Settings,
    heroFocus: FocusRequester,
    onHeroImage: (String?) -> Unit,
    onOpenSettings: () -> Unit,
    onRequestTvPermission: () -> Unit,
) {
    val context = LocalContext.current
    val tv by vm.tv.collectAsState()
    val epg by vm.epg.collectAsState()
    val now by vm.now.collectAsState()
    val apps by vm.apps.collectAsState()
    val mayo by vm.mayo.collectAsState()
    val heroes = remember(settings, tv, epg, now) { Highlights.build(settings, now, tv, epg, vm::channelName) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "hero") {
            HeroCard(
                heroes = heroes,
                rotateSeconds = settings.heroRotateSeconds,
                focusRequester = heroFocus,
                onImage = onHeroImage,
                onOpen = { vm.openHero(context, it.action) },
                onEmpty = onOpenSettings,
            )
        }
        if (tv.permissionMissing) {
            item(key = "permission") { PermissionHint(onRequestTvPermission) }
        }
        for (rowKey in settings.rows) {
            if (rowKey in settings.hiddenRows) continue
            when (StartRow.of(rowKey)) {
                StartRow.CATEGORY -> item(key = "row-category") { CategoryRow(vm, settings, mayo, epg, now, onOpenSettings) }
                StartRow.RECENT -> {
                    if (mayo.recent.isNotEmpty()) item(key = "row-recent") {
                        ShelfRow("Zuletzt gesehen", mayo.recent, key = { "r" + it.id }) { ch -> MayoChannelTile(vm, ch, epg, now) }
                    } else {
                        // Ältere Mayo-TV-Version: die Kacheln aus dem Startbildschirm-Kanal.
                        val recent = tv.mayoRecent()
                        if (recent.isNotEmpty()) item(key = "row-recent") {
                            ShelfRow("Zuletzt gesehen", recent, key = { it.id }, note = "Mayo TV") { p ->
                                PosterTile(image = p.posterUri, title = "", subtitle = "", onClick = { vm.openProgram(context, p) }, fallback = p.title)
                            }
                        }
                    }
                }
                StartRow.CHANNELS -> item(key = "row-channels") { ChannelsRow(vm, settings, tv, epg, now) }
                StartRow.CONTINUE -> if (tv.watchNext.isNotEmpty()) item(key = "row-continue") {
                    ShelfRow("Weiter schauen", tv.watchNext.take(20), key = { it.id }) { p ->
                        PosterTile(
                            image = p.posterUri, title = p.title, subtitle = p.subtitle.ifBlank { appLabel(apps, p.packageName) },
                            progress = p.progress, onClick = { vm.openProgram(context, p) },
                        )
                    }
                }
                null -> Unit
            }
        }
    }
}

/** Die in den Einstellungen gewählte Kategorie aus Mayo TV, mit dem Bild der laufenden Sendung. */
@Composable
private fun CategoryRow(
    vm: HomeViewModel,
    settings: Settings,
    mayo: MayoSnapshot,
    epg: Map<String, List<io.github.jonathanxoxox.mayohome.data.EpgProgram>>,
    now: Long,
    onOpenSettings: () -> Unit,
) {
    val hint = when {
        !mayo.available -> "Mayo TV ist zu alt für diese Reihe. Bitte Mayo TV aktualisieren."
        settings.liveCategoryId < 0 -> "Wähle in den Einstellungen unter „Live TV“ eine Kategorie aus deiner Playlist."
        mayo.category.isEmpty() -> "Die Kategorie „${settings.liveCategoryName}“ ist leer oder nicht mehr in der Playlist."
        else -> null
    }
    if (hint != null) {
        Row(Modifier.padding(horizontal = 48.dp)) {
            FocusCard(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text(hint, fontSize = 13.sp, color = Palette.Muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp))
            }
        }
        return
    }
    ShelfRow(settings.liveCategoryName.ifBlank { "Kategorie" }, mayo.category, key = { "c" + it.id }, note = "Mayo TV") { ch ->
        MayoChannelTile(vm, ch, epg, now)
    }
}

/** Sender aus Mayo TV: Bild der laufenden Sendung (aus dem MagentaTV-Programm), sonst das Senderlogo. */
@Composable
private fun MayoChannelTile(
    vm: HomeViewModel,
    ch: MayoChannel,
    epg: Map<String, List<io.github.jonathanxoxox.mayohome.data.EpgProgram>>,
    now: Long,
) {
    val context = LocalContext.current
    val catalog = remember(ch.name) { vm.catalogIdFor(ch.name) }
    val program = catalog?.let { id -> epg[id].orEmpty().firstOrNull { it.runningAt(now) } }
    val title = ch.nowTitle ?: program?.title
    val end = if (ch.nowEnd > 0) ch.nowEnd else program?.end
    val width = 180.dp
    Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FocusCard(onClick = { Launcher.start(context, ch.intent()) }, modifier = Modifier.width(width).height(width * 9f / 16f)) {
            if (program?.imageUrl != null) {
                AsyncImage(model = program.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else if (ch.logo != null) {
                AsyncImage(model = ch.logo, contentDescription = ch.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(22.dp))
            } else {
                Text(ch.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, color = Palette.Text,
                    modifier = Modifier.align(Alignment.Center).padding(12.dp))
            }
            val progress = ch.progressAt(now) ?: program?.progressAt(now)
            if (progress != null && progress > 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x40F5F7F7))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(LocalLook.current.accent))
                }
            }
        }
        Text(ch.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.Text)
        val sub = listOfNotNull(title, end?.takeIf { it > 0 }?.let { "bis " + Highlights.clock(it) }).joinToString(" · ")
        if (sub.isNotBlank()) Text(sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.Muted)
    }
}

private fun appLabel(apps: List<AppEntry>, pkg: String) = apps.firstOrNull { it.packageName == pkg }?.label.orEmpty()

@Composable
private fun ChannelsRow(vm: HomeViewModel, settings: Settings, tv: TvSnapshot, epg: Map<String, List<io.github.jonathanxoxox.mayohome.data.EpgProgram>>, now: Long) {
    val context = LocalContext.current
    if (epg.values.any { it.isNotEmpty() } || tv.mayo(TvSnapshot.MAYO_FAVORITES).isEmpty()) {
        // Sender aus dem Katalog, mit dem Bild der laufenden Sendung.
        val entries = settings.channels.map { id -> id to epg[id].orEmpty().firstOrNull { it.runningAt(now) } }
        if (entries.isEmpty()) return
        ShelfRow("Sender", entries, key = { it.first }) { (id, program) ->
            val name = vm.channelName(id)
            PosterTile(
                image = program?.imageUrl,
                title = name,
                subtitle = program?.let { "${it.title} · bis ${Highlights.clock(it.end)}" } ?: "",
                progress = program?.progressAt(now),
                fallback = name,
                width = 168.dp,
                onClick = { vm.openChannel(context, name) },
            )
        }
    } else {
        // Ohne Programmdaten: die Lieblingssender, die Mayo TV selbst als Kanal anbietet.
        ShelfRow("Sender", tv.mayo(TvSnapshot.MAYO_FAVORITES), key = { it.id }, note = "aus Mayo TV") { p ->
            PosterTile(image = p.posterUri, title = "", subtitle = "", width = 168.dp, onClick = { vm.openProgram(context, p) }, fallback = p.title)
        }
    }
}

@Composable
private fun HeroCard(
    heroes: List<HeroItem>,
    rotateSeconds: Int,
    focusRequester: FocusRequester,
    onImage: (String?) -> Unit,
    onOpen: (HeroItem) -> Unit,
    onEmpty: () -> Unit,
) {
    var index by remember { mutableIntStateOf(0) }
    val count = heroes.size
    val current = heroes.getOrNull(if (count == 0) 0 else index % count)
    LaunchedEffect(current?.imageUrl) { onImage(current?.imageUrl) }
    LaunchedEffect(index, count, rotateSeconds) {
        if (count > 1 && rotateSeconds > 0) {
            delay(rotateSeconds * 1_000L)
            index = (index + 1) % count
        }
    }
    val look = LocalLook.current
    FocusCard(
        onClick = { if (current != null) onOpen(current) else onEmpty() },
        corner = 20.dp,
        scale = 1.015f,
        modifier = Modifier
            .padding(horizontal = 48.dp)
            .fillMaxWidth()
            .height(260.dp)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || count < 2) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionRight -> { index = (index + 1) % count; true }
                    Key.DirectionLeft -> { index = (index - 1 + count) % count; true }
                    else -> false
                }
            },
    ) {
        AnimatedContent(
            targetState = current,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            contentKey = { it?.key },
            label = "hero",
        ) { item ->
            if (item == null) EmptyHero() else HeroContent(item, look.page)
        }
        if (count > 1) {
            Row(Modifier.align(Alignment.BottomEnd).padding(18.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(count) { i ->
                    val active = i == index % count
                    Box(Modifier.size(width = if (active) 16.dp else 5.dp, height = 5.dp).background(if (active) Palette.Text else Color(0x66F5F7F7), CircleShape))
                }
            }
        }
    }
}

@Composable
private fun HeroContent(item: HeroItem, page: Color) {
    Box(Modifier.fillMaxSize()) {
        if (item.imageUrl != null && !item.imageIsTile) {
            AsyncImage(model = item.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(heroScrim(Color(0xFF111619))))
        }
        if (item.imageUrl != null && item.imageIsTile) {
            // Fertige Kachel (z. B. ein Spiel aus Mayo TV) oder Hochkant-Poster: unverändert rechts zeigen.
            AsyncImage(
                model = item.imageUrl, contentDescription = null, contentScale = ContentScale.Fit, alignment = Alignment.CenterEnd,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.5f).padding(20.dp).clip(RoundedCornerShape(12.dp)),
            )
        }
        Column(
            Modifier.align(Alignment.CenterStart).padding(start = 32.dp, end = 32.dp).widthIn(max = 390.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (item.live) Box(Modifier.size(7.dp).background(Palette.Live, CircleShape))
                Text(item.kicker.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFDCE3E5), letterSpacing = 0.6.sp)
            }
            Text(item.title, fontSize = 30.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Palette.Text)
            if (item.meta.isNotBlank()) Text(item.meta, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(0xFFD5DCDE))
            if (item.description.isNotBlank()) {
                Text(item.description, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Palette.Muted)
            }
            if (item.progress != null && item.progress > 0f) {
                Box(Modifier.padding(top = 2.dp).width(180.dp).height(3.dp).background(Color(0x40F5F7F7), RoundedCornerShape(2.dp))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(item.progress).background(LocalLook.current.accent, RoundedCornerShape(2.dp)))
                }
            }
            Row(
                Modifier.padding(top = 8.dp).background(Palette.Text, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item.actionLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = page)
            }
        }
    }
}

@Composable
private fun EmptyHero() {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
        Text("Willkommen bei Mayo Home", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
        Text(
            "Hier erscheinen Spiele deiner Teams, Highlights deiner Sender und was du weiter schauen kannst. " +
                "OK öffnet die Einstellungen, dort wählst du Sender und Teams.",
            fontSize = 13.sp, lineHeight = 19.sp, color = Palette.Muted, modifier = Modifier.padding(top = 8.dp).widthIn(max = 520.dp),
        )
    }
}

@Composable
private fun PermissionHint(onRequest: () -> Unit) {
    Row(Modifier.padding(horizontal = 48.dp)) {
        FocusCard(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Kanäle deiner Apps freigeben", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
                Text(
                    "Damit „Weiter schauen“ und die Kanäle von Nuvio, Mayo TV und Co. hier erscheinen, braucht Mayo Home die Berechtigung für TV-Programmdaten. OK fragt danach.",
                    fontSize = 12.sp, lineHeight = 17.sp, color = Palette.Muted,
                )
            }
        }
    }
}
