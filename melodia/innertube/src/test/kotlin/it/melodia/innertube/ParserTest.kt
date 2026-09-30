package it.melodia.innertube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ParserTest {
    private fun fixture(name: String): JsonElement =
        Json.parseToJsonElement(javaClass.getResource("/fixtures/$name.json")!!.readText())

    @Test
    fun search() {
        val sections = Parser.sections(fixture("search").at("contents"))
        val items = sections.flatMap { it.items }
        println(items.joinToString("\n"))
        val top = items.first()
        assertTrue(top is SongItem && top.videoId == "rdorzAplV3M" && top.artists.first().name == "Vasco Rossi", "$top")
        assertTrue(items.any { it is ArtistItem && it.title == "Vasco Rossi" })
        assertTrue(items.filterIsInstance<SongItem>().size > 5)
    }

    @Test
    fun videosSearch() {
        val items = Parser.sections(fixture("videos").at("contents")).flatMap { it.items }
        val songs = items.filterIsInstance<SongItem>()
        assertTrue(songs.size > 10)
        assertTrue(songs.all { it.artists.isNotEmpty() }, songs.filter { it.artists.isEmpty() }.toString())
        assertTrue(songs.count { it.durationSec != null } > 5)
    }

    @Test
    fun artist() {
        val page = Parser.artistPage(fixture("artist"))
        println(page.sections.map { "${it.title}: ${it.items.size} more=${it.moreBrowseId}" })
        assertEquals("Vasco Rossi", page.title)
        assertNotNull(page.thumbnail)
        val songs = page.sections.first().items
        assertTrue(songs.first() is SongItem)
        assertTrue(page.sections.size >= 3)
    }

    @Test
    fun queue() {
        val panel = fixture("next").findFirst("playlistPanelRenderer")
        val songs = Parser.allItems(panel).filterIsInstance<SongItem>()
        println(songs.take(5).joinToString("\n"))
        assertTrue(songs.size >= 40)
        assertTrue(songs.all { it.artists.isNotEmpty() && it.title.isNotBlank() })
        assertNotNull(Parser.continuation(panel))
    }

    @Test
    fun albumPlaylist() {
        val page = Parser.playlistPage(fixture("olak"), "VLOLAK5uy_khEpWbiqtCJrrnr-uAkvbFdl41TW4HTuM")
        println("${page.title} | ${page.subtitle} | ${page.secondSubtitle} | ${page.playlistId} | ${page.songs.size}")
        println(page.songs.take(3).joinToString("\n"))
        assertEquals(26, page.songs.size)
        assertEquals("OLAK5uy_khEpWbiqtCJrrnr-uAkvbFdl41TW4HTuM", page.playlistId)
        assertTrue(page.songs.first().durationSec == 222)
    }

    @Test
    fun playlist() {
        val page = Parser.playlistPage(fixture("playlist"), "VLPL4945F5C55BF3EC97")
        assertEquals(57, page.songs.size)
        assertTrue(page.songs.all { it.thumbnail != null })
        // The section-list continuation only loads related shelves, not tracks.
        assertEquals(null, page.continuation)
    }

    @Test
    fun home() {
        val home = Parser.sections(fixture("home").at("contents"))
        assertTrue(home.isNotEmpty() && home.first().title != null)
        val cont = Parser.sections(fixture("cont").at("continuationContents"))
        assertTrue(cont.isNotEmpty())
    }

    @Test
    fun thumbnails() {
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w544-h544-l90-rj",
            Parser.upscale("https://lh3.googleusercontent.com/abc=w120-h120-l90-rj", 544),
        )
        assertEquals("https://yt3.ggpht.com/x=s544-c-k", Parser.upscale("https://yt3.ggpht.com/x=s48-c-k", 544))
    }
}
