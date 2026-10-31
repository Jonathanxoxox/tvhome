package io.github.jonathanxoxox.mayohome.data

import java.util.Calendar
import java.util.Locale

/** Was ein Eintrag im Poster beim Drücken von OK tut. */
sealed interface HeroAction {
    /** Einen Link einer App öffnen (aus einem App-Kanal oder "Weiter schauen"). */
    data class Link(val uri: String) : HeroAction
    /** Einen Sender in Mayo TV öffnen. */
    data class Channel(val channelName: String) : HeroAction
}

/** Ein Eintrag für das große Poster auf der Startseite. */
data class HeroItem(
    val key: String,
    val kicker: String,
    val title: String,
    val meta: String,
    val description: String,
    val imageUrl: String?,
    val live: Boolean,
    val progress: Float?,
    val actionLabel: String,
    val action: HeroAction,
    val score: Int,
    /** True, wenn das Bild selbst schon eine fertige Kachel oder ein Hochkant-Poster ist: dann rechts als Karte zeigen. */
    val imageIsTile: Boolean = false,
)

/**
 * Wählt die Highlights für das Poster. Reihenfolge, wenn [Settings.footballFirst] an ist:
 * Spiele deiner Teams (aus Mayo TV oder im Programm) > anderer Fußball > Sport > Filme > Serien > Rest,
 * laufende Sendungen vor kommenden. Pro Sender höchstens ein Eintrag.
 */
object Highlights {

    fun build(
        settings: Settings,
        now: Long,
        tv: TvSnapshot,
        epg: Map<String, List<EpgProgram>>,
        channelName: (String) -> String,
    ): List<HeroItem> {
        val items = ArrayList<HeroItem>()
        val teams = settings.favoriteTeams.map { it.trim().lowercase(Locale.GERMAN) }.filter { it.length >= 3 }

        if (settings.heroUseMayo) {
            for (p in tv.mayo(TvSnapshot.MAYO_FOR_YOU)) {
                val link = p.intentUri ?: continue
                val base = if (settings.footballFirst) 1000 else 400
                items += HeroItem(
                    key = "mayo:${p.id}",
                    kicker = if (p.live) "Live · Mayo TV" else "Fußball · Mayo TV",
                    title = p.title,
                    meta = p.description,
                    description = "",
                    imageUrl = p.posterUri,
                    live = p.live,
                    progress = p.progress,
                    actionLabel = "Ansehen",
                    action = HeroAction.Link(link),
                    score = base + if (p.live) 150 else 0,
                    imageIsTile = true,
                )
            }
        }

        if (settings.heroUseEpg) {
            val until = now + settings.heroLookaheadHours * 3_600_000L
            for ((channelId, programs) in epg) {
                val name = channelName(channelId)
                val best = programs
                    .filter { it.end > now && it.start <= until && it.end - it.start >= 15 * 60_000L }
                    .mapNotNull { p -> scoreEpg(p, now, settings, teams)?.let { p to it } }
                    .maxByOrNull { it.second } ?: continue
                val (p, score) = best
                val running = p.runningAt(now)
                items += HeroItem(
                    key = "epg:$channelId:${p.start}",
                    kicker = if (running) "Live · $name" else "${startLabel(p.start, now)} · $name",
                    title = p.title,
                    meta = listOf(p.genres.split(',').firstOrNull()?.trim().orEmpty(), p.subtitle, clock(p.start) + " – " + clock(p.end))
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    description = p.description,
                    imageUrl = p.imageUrl,
                    live = running,
                    progress = if (running) p.progressAt(now) else null,
                    actionLabel = if (running) "Einschalten" else "Sender öffnen",
                    action = HeroAction.Channel(name),
                    score = score,
                )
            }
        }

        if (settings.heroUseWatchNext) {
            tv.watchNext.take(3).forEachIndexed { index, p ->
                val link = p.intentUri ?: return@forEachIndexed
                items += HeroItem(
                    key = "next:${p.id}",
                    kicker = "Weiter schauen",
                    title = p.title,
                    meta = p.subtitle,
                    description = p.description,
                    imageUrl = p.posterUri,
                    live = false,
                    progress = p.progress,
                    actionLabel = "Fortsetzen",
                    action = HeroAction.Link(link),
                    score = 330 - index * 10,
                    imageIsTile = p.aspect < 1.2f,
                )
            }
        }

        return items.sortedByDescending { it.score }.distinctBy { it.title.lowercase() }.take(settings.heroMax.coerceAtLeast(1))
    }

    /** Punkte für eine Sendung, oder null, wenn ihre Art in den Einstellungen abgewählt ist. */
    private fun scoreEpg(p: EpgProgram, now: Long, s: Settings, teams: List<String>): Int? {
        val kind = kindOf(p)
        if (kind == null || kind.key !in s.highlightKinds) return null
        val title = p.title.lowercase(Locale.GERMAN)
        val teamHit = teams.any { title.contains(it) }
        var score = when (kind) {
            HighlightKind.FOOTBALL -> if (s.footballFirst) 500 else 240
            HighlightKind.SPORT -> 300
            HighlightKind.FILM -> 260
            HighlightKind.SERIES -> 180
            HighlightKind.SHOW -> 120
            HighlightKind.DOCU -> 100
        }
        if (teamHit && kind == HighlightKind.FOOTBALL) score += if (s.footballFirst) 900 else 300
        val running = p.runningAt(now)
        score += if (running) 100 else (100 - ((p.start - now) / 120_000L).toInt()).coerceAtLeast(0)
        if (kind == HighlightKind.FILM && isPrimeTime(p.start)) score += 80
        // Wiederholungen und Zusammenfassungen sind selten das Highlight.
        if (title.contains("highlights") || title.contains("wiederholung") || title.contains("re-live")) score -= 120
        score += if (p.imageUrl != null) 50 else -200
        return score
    }

    fun kindOf(p: EpgProgram): HighlightKind? {
        val g = p.genres.lowercase(Locale.GERMAN)
        val t = p.title.lowercase(Locale.GERMAN)
        val football = listOf("fußball", "fussball", "bundesliga", "champions league", "europa league", "premier league",
            "dfb-pokal", "laliga", "serie a", "conference league", "länderspiel")
        return when {
            football.any { g.contains(it) || t.contains(it) } -> HighlightKind.FOOTBALL
            g.contains("sport") -> HighlightKind.SPORT
            g.contains("spielfilm") || g.contains("film") && !g.contains("filmmagazin") -> HighlightKind.FILM
            g.contains("serie") || p.subtitle.contains("Folge") -> HighlightKind.SERIES
            g.contains("show") || g.contains("unterhaltung") -> HighlightKind.SHOW
            g.contains("doku") || g.contains("reportage") -> HighlightKind.DOCU
            else -> null
        }
    }

    private fun isPrimeTime(start: Long): Boolean {
        val c = Calendar.getInstance().apply { timeInMillis = start }
        val minutes = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return minutes in (20 * 60)..(20 * 60 + 30)
    }

    fun clock(t: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = t }
        return String.format(Locale.GERMAN, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    private fun startLabel(start: Long, now: Long): String {
        val minutes = (start - now) / 60_000L
        return if (minutes < 60) "In $minutes Min." else "Heute ${clock(start)}"
    }
}
