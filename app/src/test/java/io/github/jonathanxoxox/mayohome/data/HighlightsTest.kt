package io.github.jonathanxoxox.mayohome.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightsTest {
    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L

    private fun program(channel: String, title: String, genres: String, startOffset: Long, length: Long = 2 * hour, image: String? = "https://img/$title.jpg") =
        EpgProgram(channel, title, "", "", genres, now + startOffset, now + startOffset + length, image)

    @Test
    fun favouriteTeamBeatsFilmAndOtherFootball() {
        val epg = mapOf(
            "film" to listOf(program("film", "Ein Film", "Spielfilm, Drama", -30 * 60_000L)),
            "bl2" to listOf(program("bl2", "Freiburg – Mainz", "Fußball", -20 * 60_000L)),
            "bl1" to listOf(program("bl1", "Bayern München – Dortmund", "Fußball", -40 * 60_000L)),
        )
        val s = Settings(favoriteTeams = listOf("Bayern"))
        val heroes = Highlights.build(s, now, TvSnapshot(), epg) { it }
        assertEquals(listOf("Bayern München – Dortmund", "Freiburg – Mainz", "Ein Film"), heroes.map { it.title })
        assertTrue(heroes.first().live)
    }

    @Test
    fun deselectedKindsAndPastProgrammesAreLeftOut() {
        val epg = mapOf(
            "a" to listOf(program("a", "Vorbei", "Spielfilm", -5 * hour)),
            "b" to listOf(program("b", "Eine Show", "Show", 10 * 60_000L)),
            "c" to listOf(program("c", "Später Film", "Spielfilm", 30 * 60_000L)),
        )
        val s = Settings(highlightKinds = setOf("film"))
        val heroes = Highlights.build(s, now, TvSnapshot(), epg) { it }
        assertEquals(listOf("Später Film"), heroes.map { it.title })
    }

    @Test
    fun withoutFootballFirstAFilmCanLead() {
        val epg = mapOf(
            "film" to listOf(program("film", "Ein Film", "Spielfilm", -10 * 60_000L)),
            "bl" to listOf(program("bl", "Freiburg – Mainz", "Fußball", -10 * 60_000L)),
        )
        val heroes = Highlights.build(Settings(footballFirst = false), now, TvSnapshot(), epg) { it }
        assertEquals("Ein Film", heroes.first().title)
    }

    @Test
    fun settingsSurviveJsonRoundTrip() {
        val s = Settings(favoriteTeams = listOf("Bayern", "Schalke"), hiddenApps = setOf("a.b"), appLastUsed = mapOf("x.y" to 5L), uiScale = 110)
        assertEquals(s, Settings.parse(s.toJson().toString()))
    }
}
