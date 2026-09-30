package it.melodia.innertube

import kotlin.test.Test
import kotlin.test.assertTrue

/** Hits the real API. Run with: gradle -p innertube test -PliveTests=true */
class LiveTest {
    private val live = System.getProperty("liveTests") == "true"
    private val yt = YouTubeMusic().apply {
        System.getProperty("innertubeBaseUrl")?.takeIf { it.isNotBlank() }?.let { baseUrl = it }
    }

    @Test
    fun searchAndQueue() {
        if (!live) return
        val page = yt.search("vasco rossi albachiara")
        val songs = page.sections.flatMap { it.items }.filterIsInstance<SongItem>()
        println(songs.take(5).joinToString("\n"))
        assertTrue(songs.isNotEmpty())
        val q = yt.next(songs.first().videoId, "RDAMVM" + songs.first().videoId)
        println("queue ${q.songs.size} cont=${q.continuation != null}")
        assertTrue(q.songs.size > 10)
        val more = yt.nextContinuation(q.continuation!!, q.playlistId)
        println("queue continuation ${more.songs.size}")
        assertTrue(more.songs.isNotEmpty())
    }

    @Test
    fun suggestionsAndArtist() {
        if (!live) return
        val s = yt.searchSuggestions("vasco")
        println(s)
        assertTrue(s.isNotEmpty())
        val artist = yt.artist("UCmUrlA_x25EXhvlsRHmC3bw")
        println(artist.title + " " + artist.sections.map { it.title })
        assertTrue(artist.title.isNotBlank())
        val more = artist.sections.firstOrNull { it.moreBrowseId != null && !it.moreBrowseId!!.startsWith("VL") }
        if (more != null) {
            val (title, sections) = yt.browseSections(more.moreBrowseId!!, more.moreParams)
            println("more: $title ${sections.sumOf { it.items.size }}")
        }
    }

    @Test
    fun bigPlaylistContinuation() {
        if (!live) return
        // A long public playlist (> 100 tracks) to exercise track continuations.
        val page = yt.playlist("PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI")
        println("${page.title} ${page.songs.size} cont=${page.continuation != null}")
        assertTrue(page.songs.isNotEmpty())
        page.continuation?.let {
            val more = yt.playlistContinuation(it)
            println("more ${more.items.size}")
            assertTrue(more.items.isNotEmpty())
        }
    }
}
