package io.github.jonathanxoxox.mayohome.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChannelNamesTest {
    @Test
    fun playlistNamesMatchTheCatalog() {
        val catalog = ChannelNames.key("Sky Sport Bundesliga 1")
        assertEquals(catalog, ChannelNames.key("DE: Sky Sport Bundesliga 1 HD"))
        assertEquals(catalog, ChannelNames.key("|DE| SKY SPORT BUNDESLIGA 1 FHD"))
        assertEquals(catalog, ChannelNames.key("[DE] Sky Sport Bundesliga 1 UHD"))
        assertEquals(ChannelNames.key("Das Erste"), ChannelNames.key("DE - Das Erste HD"))
        assertEquals(ChannelNames.key("ProSieben"), ChannelNames.key("ProSieben HD"))
    }

    @Test
    fun differentChannelsStayDifferent() {
        assertNotEquals(ChannelNames.key("Sky Sport Bundesliga 1"), ChannelNames.key("Sky Sport Bundesliga 10"))
        assertNotEquals(ChannelNames.key("ZDF"), ChannelNames.key("ZDFneo"))
    }
}
