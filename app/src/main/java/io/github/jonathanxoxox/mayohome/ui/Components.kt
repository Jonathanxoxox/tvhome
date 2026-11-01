package io.github.jonathanxoxox.mayohome.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Eine fokussierbare Fläche im Stil von Mayo Home: hebt sich beim Fokus und bekommt einen weißen Rand. */
@Composable
fun FocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    corner: Dp = LocalLook.current.corner,
    container: Color = Palette.Card,
    focusedContainer: Color = Palette.CardHigh,
    scale: Float = LocalLook.current.focusScale,
    onFocus: (Boolean) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.onFocusChanged { onFocus(it.isFocused) },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            contentColor = Palette.Text,
            focusedContainerColor = focusedContainer,
            focusedContentColor = Palette.Text,
            pressedContainerColor = focusedContainer,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = if (LocalLook.current.animations) scale else 1f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.5.dp, Palette.Text), shape = shape),
        ),
        content = content,
    )
}

/** Eine Reihe mit Überschrift und waagerecht scrollenden Einträgen. */
@Composable
fun <T> ShelfRow(
    title: String,
    items: List<T>,
    key: (T) -> Any,
    note: String? = null,
    controls: RowControls? = null,
    item: @Composable (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.padding(start = 48.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
            if (note != null) Text(note, fontSize = 11.sp, color = Palette.Muted)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (controls != null) RowHandle(controls)
            LazyRow(
                contentPadding = PaddingValues(start = if (controls != null) 6.dp else 48.dp, end = 48.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(items, key = key) { item(it) }
            }
        }
    }
}

/** Was die Pfeile links neben einer Reihe tun (◀ vom ersten Eintrag aus). */
data class RowControls(
    val onUp: (() -> Unit)?,
    val onDown: (() -> Unit)?,
    val onHide: (() -> Unit)?,
    val more: List<MenuEntry> = emptyList(),
)

data class MenuEntry(val label: String, val action: () -> Unit)

/**
 * Pfeile links neben einer Reihe: ▲ ▼ verschieben, ✕ blendet aus, ⋯ öffnet weitere Aktionen. Kaum
 * sichtbar, bis man mit ◀ vom ersten Eintrag hineinwandert.
 */
@Composable
private fun RowHandle(c: RowControls) {
    var active by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    Column(
        Modifier.padding(start = 8.dp).width(34.dp).onFocusChanged { active = it.hasFocus }
            .alpha(if (active) 1f else 0f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Unsichtbar, bis man mit ◀ vom ersten Eintrag hineingeht; sie liegen im linken Rand.
        val alpha = 1f
        c.onUp?.let { HandleButton(Glyph.UP, alpha, onClick = it) }
        c.onDown?.let { HandleButton(Glyph.DOWN, alpha, onClick = it) }
        c.onHide?.let { HandleButton(Glyph.CLOSE, alpha, onClick = it) }
        if (c.more.isNotEmpty()) HandleButton(Glyph.MORE, alpha) { menu = true }
    }
    if (menu) MenuDialog(title = "Reihe", entries = c.more, onDismiss = { menu = false })
}

enum class Glyph { UP, DOWN, LEFT, RIGHT, CLOSE, MORE, PLUS }

@Composable
fun HandleButton(glyph: Glyph, alpha: Float = 1f, size: Dp = 30.dp, onClick: () -> Unit) {
    FocusCard(onClick = onClick, corner = 50.dp, scale = 1.12f, container = Color(0x26F5F7F7), modifier = Modifier.size(size)) {
        GlyphIcon(glyph, Palette.Text.copy(alpha = alpha), Modifier.align(Alignment.Center).size(size * 0.45f))
    }
}

/** Kleine Linien-Symbole, selbst gezeichnet, damit sie auf jeder Box gleich aussehen. */
@Composable
fun GlyphIcon(glyph: Glyph, color: Color, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.16f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        fun line(vararg pts: Pair<Float, Float>) {
            val path = androidx.compose.ui.graphics.Path()
            pts.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(x * w, y * h) else path.lineTo(x * w, y * h) }
            drawPath(path, color, style = stroke)
        }
        when (glyph) {
            Glyph.UP -> line(0.15f to 0.68f, 0.5f to 0.32f, 0.85f to 0.68f)
            Glyph.DOWN -> line(0.15f to 0.32f, 0.5f to 0.68f, 0.85f to 0.32f)
            Glyph.LEFT -> line(0.68f to 0.15f, 0.32f to 0.5f, 0.68f to 0.85f)
            Glyph.RIGHT -> line(0.32f to 0.15f, 0.68f to 0.5f, 0.32f to 0.85f)
            Glyph.CLOSE -> { line(0.2f to 0.2f, 0.8f to 0.8f); line(0.8f to 0.2f, 0.2f to 0.8f) }
            Glyph.PLUS -> { line(0.5f to 0.12f, 0.5f to 0.88f); line(0.12f to 0.5f, 0.88f to 0.5f) }
            Glyph.MORE -> listOf(0.18f, 0.5f, 0.82f).forEach { x ->
                drawCircle(color, radius = w * 0.1f, center = androidx.compose.ui.geometry.Offset(x * w, h / 2))
            }
        }
    }
}

/** Ein schlichtes Menü als Dialog, mit der Fernbedienung bedienbar; OK führt aus und schließt. */
@Composable
fun MenuDialog(title: String, entries: List<MenuEntry>, onDismiss: () -> Unit) {
    val first = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(340.dp).heightIn(max = 460.dp)
                .background(Color(0xFF161C1F), RoundedCornerShape(18.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.Text, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(entries.size) { i ->
                    val e = entries[i]
                    FocusCard(
                        onClick = { e.action(); onDismiss() },
                        scale = 1.02f,
                        container = Color.Transparent,
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                            .let { if (i == 0) it.then(Modifier.focusRequester(first)) else it },
                    ) {
                        Text(e.label, fontSize = 13.sp, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 12.dp))
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { first.requestFocus() }
    }
}

/** Kachel mit Bild (oder Text, wenn es kein Bild gibt), Fortschritt und zwei Zeilen darunter. */
@Composable
fun PosterTile(
    image: String?,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    width: Dp = 180.dp,
    aspect: Float = 16f / 9f,
    progress: Float? = null,
    live: Boolean = false,
    badge: String? = null,
    fallback: String = title,
    onLongClick: (() -> Unit)? = null,
    /** Nur beim ausgewählten Eintrag den Titel zeigen, ohne zweite Zeile (ruhiger Look auf Start). */
    titleOnFocus: Boolean = false,
    onFocus: (Boolean) -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FocusCard(
            onClick = onClick, onLongClick = onLongClick,
            onFocus = { focused = it; onFocus(it) },
            modifier = Modifier.width(width).height(width / aspect),
        ) {
            if (image != null) {
                AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                    Text(fallback, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Palette.Text)
                }
            }
            if (live || badge != null) {
                Row(
                    Modifier.align(Alignment.TopStart).padding(8.dp)
                        .background(Color(0xCC0D1113), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (live) Box(Modifier.size(6.dp).background(Palette.Live, CircleShape))
                    Text(badge ?: "Live", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
                }
            }
            if (progress != null && progress > 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x40F5F7F7))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(LocalLook.current.accent))
                }
            }
        }
        if (titleOnFocus) {
            // Platz immer freihalten, damit die Reihe beim Wandern nicht springt.
            Text(title.ifBlank { " " }, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (focused) Palette.Text else Color.Transparent)
            return@Column
        }
        if (title.isNotBlank()) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.Text)
        }
        if (subtitle.isNotBlank()) {
            Text(subtitle, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.Muted)
        }
    }
}

/** Uhr oben rechts; tickt jede Sekunde, zeigt aber nur Stunden und Minuten. */
@Composable
fun Clock(clock24: Boolean, showDate: Boolean) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val time = SimpleDateFormat(if (clock24) "HH:mm" else "h:mm a", Locale.GERMAN).format(Date(now))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showDate) Text(SimpleDateFormat("EEE, d. MMM", Locale.GERMAN).format(Date(now)), fontSize = 13.sp, color = Palette.Muted)
        Text(time, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Palette.Text)
    }
}

/** Kleiner Ein/Aus-Schalter, nur zur Anzeige (die ganze Zeile ist der Knopf). */
@Composable
fun SwitchPill(on: Boolean) {
    val accent = LocalLook.current.accent
    Box(
        Modifier.size(width = 36.dp, height = 20.dp).clip(RoundedCornerShape(10.dp))
            .background(if (on) accent else Color(0x40F5F7F7)).padding(2.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(16.dp).background(if (on) Color(0xFF0D1113) else Palette.Text, CircleShape))
    }
}

/** Verlauf, der ein Bild nach links und unten in die Seite übergehen lässt. */
fun heroScrim(page: Color) = Brush.horizontalGradient(
    0f to page.copy(alpha = 0.95f),
    0.4f to page.copy(alpha = 0.65f),
    0.7f to page.copy(alpha = 0f),
)

/** Merkt sich, ob irgendein Kind gerade den Fokus hat. */
@Composable
fun rememberFocusFlag() = remember { mutableStateOf(false) }
