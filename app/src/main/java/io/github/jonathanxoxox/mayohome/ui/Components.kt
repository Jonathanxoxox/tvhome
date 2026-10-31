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
    item: @Composable (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.padding(start = 48.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
            if (note != null) Text(note, fontSize = 11.sp, color = Palette.Muted)
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(items, key = key) { item(it) }
        }
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
