package io.github.jonathanxoxox.mayohome.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import io.github.jonathanxoxox.mayohome.data.Settings

object Palette {
    val Text = Color(0xFFF5F7F7)
    val Muted = Color(0xFFA3AFB2)
    val Card = Color(0xFF1A2124)
    val CardHigh = Color(0xFF242C30)
    val Line = Color(0x33F5F7F7)
    val Live = Color(0xFFFF5A5F)
    val Pages = mapOf(
        "black" to Color(0xFF0D1113),
        "petrol" to Color(0xFF0B262A),
        "night" to Color(0xFF0E1322),
        "hero" to Color(0xFF0D1113),
    )
    val Accents = linkedMapOf(
        "white" to ("Weiß" to Color(0xFFF5F7F7)),
        "mint" to ("Mint" to Color(0xFF5FE0D2)),
        "blue" to ("Blau" to Color(0xFF7FB2FF)),
        "orange" to ("Orange" to Color(0xFFFFB86B)),
        "pink" to ("Rosa" to Color(0xFFFF8FB1)),
        "green" to ("Grün" to Color(0xFF7BE3A4)),
    )
}

/** Das Aussehen, das sich aus den Einstellungen ergibt; jede Kachel liest es über [LocalLook]. */
data class Look(
    val page: Color,
    val accent: Color,
    val corner: Dp,
    val focusScale: Float,
    val animations: Boolean,
    val heroBackdrop: Boolean,
) {
    companion object {
        fun from(s: Settings) = Look(
            page = Palette.Pages[s.background] ?: Palette.Pages.getValue("black"),
            accent = Palette.Accents[s.accent]?.second ?: Palette.Text,
            corner = when (s.corners) { "small" -> 6.dp; "large" -> 18.dp; else -> 12.dp },
            focusScale = when (s.focusScale) { "off" -> 1f; "small" -> 1.04f; "large" -> 1.12f; else -> 1.08f },
            animations = s.animations,
            heroBackdrop = s.background == "hero",
        )
    }
}

val LocalLook = staticCompositionLocalOf { Look.from(Settings()) }

@Composable
fun MayoTheme(settings: Settings, content: @Composable () -> Unit) {
    val look = Look.from(settings)
    val base = LocalDensity.current
    val scale = settings.uiScale / 100f
    CompositionLocalProvider(
        LocalLook provides look,
        LocalDensity provides Density(base.density * scale, base.fontScale),
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = look.accent,
                onPrimary = Color(0xFF0D1113),
                background = look.page,
                surface = Palette.Card,
                onSurface = Palette.Text,
                onBackground = Palette.Text,
            ),
            content = content,
        )
    }
}
