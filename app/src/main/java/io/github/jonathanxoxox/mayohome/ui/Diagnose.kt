package io.github.jonathanxoxox.mayohome.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.github.jonathanxoxox.mayohome.BuildConfig
import io.github.jonathanxoxox.mayohome.data.Highlights
import io.github.jonathanxoxox.mayohome.data.Settings
import io.github.jonathanxoxox.mayohome.data.TvChannelsRepository
import io.github.jonathanxoxox.mayohome.data.TvSnapshot
import io.github.jonathanxoxox.mayohome.system.Launcher

/**
 * Zeigt, was Mayo Home gerade findet. Gedacht zum Abfotografieren, wenn etwas nicht erscheint:
 * daran sieht man, ob es an Berechtigungen, am Programm oder an den Kanälen der Apps liegt.
 */
@Composable
fun DiagnoseSection(vm: HomeViewModel, settings: Settings) {
    val context = LocalContext.current
    val tv by vm.tv.collectAsState()
    val epg by vm.epg.collectAsState()
    val now by vm.now.collectAsState()
    val apps by vm.apps.collectAsState()
    val heroes = remember(settings, tv, epg, now) { Highlights.build(settings, now, tv, epg, vm::channelName) }
    fun yes(b: Boolean) = if (b) "ja" else "nein"
    fun appName(pkg: String) = apps.firstOrNull { it.packageName == pkg }?.label ?: pkg

    val system = listOf(
        "Version" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        "Standard-Startbildschirm" to yes(Launcher.isDefaultHome(context)),
        "Home-Wächter an" to yes(Launcher.isGuardEnabled(context)),
        "Start aus dem Hintergrund" to yes(Launcher.canDrawOverlays(context)),
        "TV-Programmdaten erlaubt" to yes(context.checkSelfPermission(TvChannelsRepository.READ_EPG) == android.content.pm.PackageManager.PERMISSION_GRANTED),
        "Apps gefunden" to "${apps.size}",
    )
    val channels = buildList {
        add("Kanäle der Apps" to "${tv.channels.size}" + if (tv.permissionMissing) " (keine Berechtigung)" else "")
        tv.channels.forEach { add("  ${appName(it.packageName)} · ${it.name}" to "${it.programs.size} Einträge") }
        add("Weiter schauen (System)" to "${tv.watchNext.size}")
        add("Mayo TV · Für dich" to "${tv.mayo(TvSnapshot.MAYO_FOR_YOU).size}")
        add("Mayo TV · Favoriten" to "${tv.mayo(TvSnapshot.MAYO_FAVORITES).size}")
        add("Mayo TV · Zuletzt gesehen" to "${tv.mayoRecent().size}")
    }
    val programmes = epg.values.flatten()
    val program = buildList {
        add("Gewählte Sender" to "${settings.channels.size}")
        add("Sender mit Programm" to "${epg.count { it.value.isNotEmpty() }}")
        add("Sendungen geladen" to "${programmes.size}")
        add("davon mit Bild" to "${programmes.count { it.imageUrl != null }}")
        add("Läuft gerade" to "${programmes.count { it.runningAt(now) }}")
        vm.epgError?.let { add("Fehler" to it) }
    }
    val highlights = heroes.map { "${it.score}" to "${it.kicker} · ${it.title}" }.ifEmpty { listOf("—" to "keine Highlights") }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Block("System", system)
        Block("Kanäle", channels)
        Block("TV-Programm (MagentaTV)", program)
        Block("Highlights im Poster (Punkte · Eintrag)", highlights)
    }
}

@Composable
private fun Block(title: String, rows: List<Pair<String, String>>) {
    Column(
        Modifier.fillMaxWidth().border(1.dp, Palette.Line, RoundedCornerShape(12.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text, modifier = Modifier.padding(bottom = 4.dp))
        rows.forEach { (k, v) ->
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
                Text(k, fontSize = 12.sp, color = Palette.Muted, modifier = Modifier.weight(1f))
                Text(v, fontSize = 12.sp, color = Palette.Text, modifier = Modifier.weight(1.4f))
            }
        }
    }
}
