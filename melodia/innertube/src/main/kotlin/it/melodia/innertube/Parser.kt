package it.melodia.innertube

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Tolerant parser for YouTube Music (WEB_REMIX) responses.
 *
 * YouTube frequently reshuffles the containers around items, so instead of hard-coding full
 * paths we look for the well-known *item* renderers wherever they appear and read the few
 * fields we need from them.
 */
internal object Parser {

    private val SHELF_KEYS = setOf(
        "musicCarouselShelfRenderer",
        "musicShelfRenderer",
        "musicPlaylistShelfRenderer",
        "gridRenderer",
        "musicCardShelfRenderer",
        "itemSectionRenderer",
        "musicDescriptionShelfRenderer",
    )

    private val ITEM_KEYS = setOf(
        "musicResponsiveListItemRenderer",
        "musicTwoRowItemRenderer",
        "playlistPanelVideoRenderer",
        "musicMultiRowListItemRenderer",
    )

    private val TYPE_LABELS = setOf(
        "brano", "brani", "song", "songs", "video", "album", "singolo", "single", "ep",
        "playlist", "artista", "artist", "episodio", "episode", "podcast", "profilo", "profile",
        "canzone", "mix", "puntata", "puntate", "stazione", "station",
    )

    private val DURATION = Regex("""^\d{1,2}(:\d{2}){1,2}$""")
    private val YEAR = Regex("""^\d{4}$""")

    // ---------------------------------------------------------------- endpoints

    fun pageType(endpoint: JsonElement?): String? =
        endpoint.at("browseEndpoint", "browseEndpointContextSupportedConfigs", "browseEndpointContextMusicConfig", "pageType").str

    private fun browseId(endpoint: JsonElement?): String? = endpoint.at("browseEndpoint", "browseId").str

    private fun isArtistPage(type: String?) = type == "MUSIC_PAGE_TYPE_ARTIST" ||
        type == "MUSIC_PAGE_TYPE_USER_CHANNEL" || type == "MUSIC_PAGE_TYPE_LIBRARY_ARTIST"

    private fun isAlbumPage(type: String?) = type == "MUSIC_PAGE_TYPE_ALBUM" || type == "MUSIC_PAGE_TYPE_AUDIOBOOK"

    // ---------------------------------------------------------------- thumbnails

    fun thumbnail(element: JsonElement?): String? {
        val list = element.findFirst("thumbnails").arr
        val url = list?.lastOrNull().at("url").str
            ?: element.findFirst("sources").arr?.lastOrNull().at("url").str
            ?: return null
        return normalizeThumb(url)
    }

    fun normalizeThumb(url: String): String {
        val u = if (url.startsWith("//")) "https:$url" else url
        return upscale(u, 544)
    }

    /** Rewrites googleusercontent size parameters to request a bigger image. */
    fun upscale(url: String, size: Int): String {
        if (!url.contains("googleusercontent.com") && !url.contains("ggpht.com")) return url
        val eq = url.lastIndexOf('=')
        if (eq < 0) return url
        val params = url.substring(eq + 1)
        return when {
            params.matches(Regex("""w\d+-h\d+.*""")) ->
                url.substring(0, eq + 1) + params.replace(Regex("""^w\d+-h\d+"""), "w$size-h$size")
            params.matches(Regex("""s\d+.*""")) ->
                url.substring(0, eq + 1) + params.replace(Regex("""^s\d+"""), "s$size")
            else -> url
        }
    }

    // ---------------------------------------------------------------- items

    fun parseItemWrapper(wrapper: JsonElement?): YTItem? {
        val o = wrapper.obj ?: return null
        o["musicResponsiveListItemRenderer"]?.let { return parseResponsive(it.obj!!) }
        o["musicTwoRowItemRenderer"]?.let { return parseTwoRow(it.obj!!) }
        o["playlistPanelVideoRenderer"]?.let { return parsePanelVideo(it.obj!!) }
        o["playlistPanelVideoWrapperRenderer"]?.let {
            return parseItemWrapper(it.at("primaryRenderer"))
        }
        o["musicMultiRowListItemRenderer"]?.let { return parseMultiRow(it.obj!!) }
        return null
    }

    private data class Meta(
        val artists: List<ArtistRef>,
        val album: AlbumRef?,
        val duration: Int?,
        val year: String?,
        val firstPlain: String?,
    )

    private fun meta(runs: List<JsonObject>): Meta {
        val artists = mutableListOf<ArtistRef>()
        var album: AlbumRef? = null
        var duration: Int? = null
        var year: String? = null
        var firstPlain: String? = null
        for (r in runs) {
            val t = r["text"].str?.trim() ?: continue
            if (t.isEmpty() || t == "•" || t == "&" || t == ",") continue
            val nav = r["navigationEndpoint"]
            val type = pageType(nav)
            when {
                isArtistPage(type) -> artists.add(ArtistRef(t, browseId(nav)))
                isAlbumPage(type) -> browseId(nav)?.let { album = AlbumRef(t, it) }
                DURATION.matches(t) -> duration = parseDuration(t)
                YEAR.matches(t) -> year = t
                nav == null && firstPlain == null && !isLabel(t) && !isStat(t) -> firstPlain = t
            }
        }
        return Meta(artists, album, duration, year, firstPlain)
    }

    private fun isLabel(t: String) = t.lowercase() in TYPE_LABELS

    private fun isStat(t: String): Boolean {
        val l = t.lowercase()
        return listOf("visualizzazion", "views", "riproduzion", "plays", "ascoltatori", "listeners",
            "iscritti", "subscribers", "mi piace", "likes", "brani", "songs", "tracks").any { l.contains(it) }
    }

    private fun artistsOrFallback(m: Meta): List<ArtistRef> =
        m.artists.ifEmpty { m.firstPlain?.let { plain -> splitArtists(plain).map { ArtistRef(it, null) } } ?: emptyList() }

    private fun splitArtists(text: String): List<String> =
        text.split(" & ", ", ", " e ", " and ").map { it.trim() }.filter { it.isNotEmpty() }

    fun parseDuration(t: String?): Int? {
        if (t == null || !DURATION.matches(t.trim())) return null
        return t.trim().split(":").fold(0) { acc, p -> acc * 60 + (p.toIntOrNull() ?: 0) }
    }

    private fun videoType(endpoint: JsonElement?): String? =
        endpoint.at("watchEndpoint", "watchEndpointMusicSupportedConfigs", "watchEndpointMusicConfig", "musicVideoType").str

    private fun isVideoType(type: String?) = type != null && type != "MUSIC_VIDEO_TYPE_ATV"

    fun parseResponsive(r: JsonObject): YTItem? {
        val columns = r["flexColumns"].arr?.map {
            it.at("musicResponsiveListItemFlexColumnRenderer", "text") ?: it.findFirst("text")
        } ?: emptyList()
        val title = columns.getOrNull(0).text() ?: return null
        val titleRun = columns.getOrNull(0).runs().firstOrNull()
        val nav = r["navigationEndpoint"]
        val navType = pageType(nav)
        val thumb = thumbnail(r["thumbnail"])

        val playEndpoint = r.at("overlay", "musicItemThumbnailOverlayRenderer", "content", "musicPlayButtonRenderer", "playNavigationEndpoint")
        val watchNav = titleRun?.get("navigationEndpoint")?.takeIf { it.at("watchEndpoint") != null }
            ?: nav?.takeIf { it.at("watchEndpoint") != null }
            ?: playEndpoint?.takeIf { it.at("watchEndpoint") != null }
        val videoId = r.at("playlistItemData", "videoId").str ?: watchNav.at("watchEndpoint", "videoId").str

        val otherRuns = columns.drop(1).flatMap { it.runs() } +
            (r["fixedColumns"].arr?.flatMap { c ->
                (c.at("musicResponsiveListItemFixedColumnRenderer", "text") ?: c.findFirst("text")).runs()
            } ?: emptyList())
        val m = meta(otherRuns)

        when {
            isAlbumPage(navType) -> return AlbumItem(
                browseId = browseId(nav)!!,
                title = title,
                artists = artistsOrFallback(m),
                year = m.year,
                thumbnail = thumb,
                playlistId = playEndpoint.at("watchPlaylistEndpoint", "playlistId").str,
            )
            isArtistPage(navType) -> return ArtistItem(
                browseId = browseId(nav)!!,
                title = title,
                thumbnail = thumb,
                subtitle = columns.getOrNull(1).text(),
            )
            navType == "MUSIC_PAGE_TYPE_PLAYLIST" -> {
                val id = browseId(nav)!!
                return PlaylistItem(
                    browseId = id,
                    playlistId = id.removePrefix("VL"),
                    title = title,
                    author = m.artists.firstOrNull()?.name ?: m.firstPlain,
                    thumbnail = thumb,
                )
            }
        }
        if (videoId == null) return null
        // Episodes of podcasts etc. are still playable; keep them as songs.
        val label = otherRuns.firstOrNull()?.get("text").str?.lowercase()
        return SongItem(
            videoId = videoId,
            title = title,
            artists = artistsOrFallback(m),
            album = m.album,
            durationSec = m.duration,
            thumbnail = thumb,
            isVideo = isVideoType(videoType(watchNav)) || label == "video",
            playlistId = watchNav.at("watchEndpoint", "playlistId").str,
            setVideoId = r.at("playlistItemData", "playlistSetVideoId").str,
        )
    }

    fun parseTwoRow(r: JsonObject): YTItem? {
        val title = r["title"].text() ?: return null
        val nav = r["navigationEndpoint"]
        val navType = pageType(nav)
        val thumb = thumbnail(r["thumbnailRenderer"])
        val m = meta(r["subtitle"].runs())
        val subtitle = r["subtitle"].text()

        nav.at("watchEndpoint", "videoId").str?.let { videoId ->
            return SongItem(
                videoId = videoId,
                title = title,
                artists = artistsOrFallback(m),
                album = m.album,
                durationSec = m.duration,
                thumbnail = thumb,
                isVideo = isVideoType(videoType(nav)),
                playlistId = nav.at("watchEndpoint", "playlistId").str,
            )
        }
        nav.at("watchPlaylistEndpoint")?.let { wpe ->
            val pid = wpe.at("playlistId").str ?: return null
            return PlaylistItem(
                browseId = null,
                playlistId = pid,
                title = title,
                author = subtitle,
                thumbnail = thumb,
                watchParams = wpe.at("params").str,
            )
        }
        val id = browseId(nav) ?: return null
        return when {
            isAlbumPage(navType) -> AlbumItem(
                browseId = id,
                title = title,
                artists = artistsOrFallback(m),
                year = m.year,
                thumbnail = thumb,
                playlistId = r.at("thumbnailOverlay").findFirst("watchPlaylistEndpoint").at("playlistId").str,
            )
            isArtistPage(navType) -> ArtistItem(id, title, thumb, subtitle)
            navType == "MUSIC_PAGE_TYPE_PLAYLIST" -> PlaylistItem(
                browseId = id,
                playlistId = id.removePrefix("VL"),
                title = title,
                author = m.artists.firstOrNull()?.name ?: subtitle,
                thumbnail = thumb,
            )
            navType == "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE" -> PlaylistItem(
                browseId = id,
                playlistId = null,
                title = title,
                author = subtitle,
                thumbnail = thumb,
            )
            else -> null
        }
    }

    fun parsePanelVideo(r: JsonObject): SongItem? {
        val videoId = r["videoId"].str ?: r.at("navigationEndpoint", "watchEndpoint", "videoId").str ?: return null
        val title = r["title"].text() ?: return null
        val m = meta(r["longBylineText"].runs())
        val artists = m.artists.ifEmpty {
            val short = r["shortBylineText"].text()
            listOfNotNull(short ?: m.firstPlain).flatMap { splitArtists(it) }.map { ArtistRef(it, null) }
        }
        return SongItem(
            videoId = videoId,
            title = title,
            artists = artists,
            album = m.album,
            durationSec = parseDuration(r["lengthText"].text()),
            thumbnail = thumbnail(r["thumbnail"]),
            isVideo = isVideoType(videoType(r["navigationEndpoint"])),
            playlistId = r.at("navigationEndpoint", "watchEndpoint", "playlistId").str,
            setVideoId = r["playlistSetVideoId"].str,
        )
    }

    private fun parseMultiRow(r: JsonObject): SongItem? {
        val nav = r["onTap"] ?: r["navigationEndpoint"]
        val videoId = nav.at("watchEndpoint", "videoId").str ?: return null
        val title = r["title"].text() ?: return null
        val m = meta(r["subtitle"].runs())
        return SongItem(videoId, title, artistsOrFallback(m), m.album, m.duration, thumbnail(r["thumbnail"]))
    }

    /** Top result card in search. */
    private fun parseCard(r: JsonObject): YTItem? {
        val titleRun = r["title"].runs().firstOrNull() ?: return null
        val title = titleRun["text"].str ?: return null
        val nav = titleRun["navigationEndpoint"] ?: r["onTap"]
        val thumb = thumbnail(r["thumbnail"])
        val m = meta(r["subtitle"].runs())
        nav.at("watchEndpoint", "videoId").str?.let {
            return SongItem(it, title, artistsOrFallback(m), m.album, m.duration, thumb, isVideoType(videoType(nav)))
        }
        val id = browseId(nav) ?: return null
        val type = pageType(nav)
        return when {
            isAlbumPage(type) -> AlbumItem(id, title, artistsOrFallback(m), m.year, thumb)
            isArtistPage(type) -> ArtistItem(id, title, thumb, r["subtitle"].text())
            type == "MUSIC_PAGE_TYPE_PLAYLIST" -> PlaylistItem(id, id.removePrefix("VL"), title, m.firstPlain, thumb)
            else -> null
        }
    }

    // ---------------------------------------------------------------- sections

    private fun shelfTitle(key: String, shelf: JsonObject): String? = when (key) {
        "musicCarouselShelfRenderer" -> shelf.at("header", "musicCarouselShelfBasicHeaderRenderer", "title").text()
            ?: shelf["header"].findFirst("title").text()
        "gridRenderer" -> shelf.at("header", "gridHeaderRenderer", "title").text()
        "musicCardShelfRenderer" -> shelf.at("header", "musicCardShelfHeaderBasicRenderer", "title").text()
        else -> shelf["title"].text() ?: shelf["header"].findFirst("title").text()
    }

    private fun moreEndpoint(key: String, shelf: JsonObject): JsonElement? = when (key) {
        "musicCarouselShelfRenderer" -> shelf.at("header", "musicCarouselShelfBasicHeaderRenderer", "moreContentButton", "buttonRenderer", "navigationEndpoint")
            ?: shelf.at("header", "musicCarouselShelfBasicHeaderRenderer", "title").runs().firstOrNull()?.get("navigationEndpoint")
        "musicShelfRenderer" -> shelf.at("bottomEndpoint") ?: shelf["title"].runs().firstOrNull()?.get("navigationEndpoint")
        else -> null
    }

    fun sections(root: JsonElement?): List<Section> {
        val out = mutableListOf<Section>()
        collectSections(root, out)
        // Merge consecutive untitled sections (search results come one item per section).
        val merged = mutableListOf<Section>()
        for (s in out) {
            val last = merged.lastOrNull()
            if (s.title == null && last != null && last.title == null) {
                merged[merged.size - 1] = last.copy(items = last.items + s.items)
            } else merged.add(s)
        }
        return merged.filter { it.items.isNotEmpty() }
    }

    private fun collectSections(e: JsonElement?, out: MutableList<Section>) {
        when (e) {
            is JsonObject -> for ((k, v) in e) {
                if (k in SHELF_KEYS && v is JsonObject) {
                    handleShelf(k, v, out)
                } else if (k != "header" && k != "frameworkUpdates") {
                    collectSections(v, out)
                }
            }
            is JsonArray -> e.forEach { collectSections(it, out) }
            else -> {}
        }
    }

    private fun handleShelf(key: String, shelf: JsonObject, out: MutableList<Section>) {
        val contents = (shelf["contents"] ?: shelf["items"]).arr ?: JsonArray(emptyList())
        if (key == "itemSectionRenderer" && contents.none { c -> c.obj?.keys?.any { it in ITEM_KEYS } == true }) {
            collectSections(contents, out)
            return
        }
        val items = mutableListOf<YTItem>()
        if (key == "musicCardShelfRenderer") parseCard(shelf)?.let { items.add(it) }
        contents.mapNotNullTo(items) { parseItemWrapper(it) }
        val more = moreEndpoint(key, shelf)
        val title = if (key == "itemSectionRenderer") null else shelfTitle(key, shelf)
        out.add(Section(title, items.distinctBy { it.key }, browseId(more), more.at("browseEndpoint", "params").str))
    }

    fun continuation(root: JsonElement?): String? {
        root.findFirst("nextContinuationData").at("continuation").str?.let { return it }
        root.findFirst("nextRadioContinuationData").at("continuation").str?.let { return it }
        for (c in root.findAll("continuationItemRenderer")) {
            c.findFirst("continuationCommand").at("token").str?.let { return it }
        }
        return null
    }

    fun allItems(root: JsonElement?): List<YTItem> {
        val out = mutableListOf<YTItem>()
        fun walk(e: JsonElement?) {
            when (e) {
                is JsonObject -> for ((k, v) in e) {
                    if (k in ITEM_KEYS || k == "playlistPanelVideoWrapperRenderer") {
                        parseItemWrapper(JsonObject(mapOf(k to v)))?.let { out.add(it) }
                    } else walk(v)
                }
                is JsonArray -> e.forEach { walk(it) }
                else -> {}
            }
        }
        walk(root)
        return out.distinctBy { it.key }
    }

    // ---------------------------------------------------------------- pages

    private val HEADER_KEYS = listOf(
        "musicResponsiveHeaderRenderer",
        "musicImmersiveHeaderRenderer",
        "musicVisualHeaderRenderer",
        "musicDetailHeaderRenderer",
        "musicHeaderRenderer",
    )

    fun header(root: JsonElement?): JsonObject? {
        for (k in HEADER_KEYS) root.findFirst(k).obj?.let { return it }
        return null
    }

    fun artistPage(root: JsonElement?): ArtistPage {
        val h = header(root)
        val shuffle = h?.get("playButton").findFirst("watchPlaylistEndpoint").at("playlistId").str
            ?: h?.get("playButton").findFirst("watchEndpoint").at("playlistId").str
        val radio = h?.get("startRadioButton").findFirst("watchPlaylistEndpoint").at("playlistId").str
            ?: h?.get("startRadioButton").findFirst("watchEndpoint").at("playlistId").str
        return ArtistPage(
            title = h?.get("title").text() ?: "",
            thumbnail = thumbnail(h?.get("thumbnail") ?: h?.get("foregroundThumbnail")),
            description = h?.get("description").text(),
            sections = sections(root.at("contents")),
            shufflePlaylistId = shuffle,
            radioPlaylistId = radio,
        )
    }

    fun playlistPage(root: JsonElement?, browseId: String): PlaylistPage {
        val h = header(root)
        val isAlbum = browseId.startsWith("MPREb")
        val contents = root.at("contents")
        // The playlist tracks live in musicPlaylistShelfRenderer / musicShelfRenderer; the rest is "related".
        val trackShelf = contents.findFirst("musicPlaylistShelfRenderer") ?: contents.findFirst("musicShelfRenderer")
        val songs = allItems(trackShelf).filterIsInstance<SongItem>()
        val thumb = thumbnail(h?.get("thumbnail"))
        val strapline = h?.get("straplineTextOne")
        val facepile = h?.get("facepile")
        val headerArtists = meta(strapline.runs()).artists.ifEmpty {
            val name = strapline.text() ?: facepile.findFirst("text").text()
            val id = facepile.findFirst("browseEndpoint").at("browseId").str
            name?.let { listOf(ArtistRef(it, id)) } ?: emptyList()
        }
        val fixedSongs = songs.map { s ->
            s.copy(
                thumbnail = if (isAlbum || s.thumbnail == null) thumb ?: s.thumbnail else s.thumbnail,
                artists = s.artists.ifEmpty { headerArtists },
            )
        }
        val playlistId = trackShelf.at("playlistId").str
            ?: h.findFirst("watchEndpoint").at("playlistId").str
            ?: h.findFirst("watchPlaylistEndpoint").at("playlistId").str
            ?: if (browseId.startsWith("VL")) browseId.removePrefix("VL") else null
        val related = sections(contents).filter { sec -> sec.items.none { it is SongItem && fixedSongs.any { f -> f.key == it.key } } }
        val subtitle = listOfNotNull(
            strapline.text() ?: facepile.findFirst("text").text(),
            h?.get("subtitle").text(),
        ).joinToString(" • ").ifEmpty { null }
        return PlaylistPage(
            title = h?.get("title").text() ?: "",
            subtitle = subtitle,
            secondSubtitle = h?.get("secondSubtitle").text(),
            thumbnail = thumb,
            playlistId = playlistId,
            songs = fixedSongs,
            continuation = continuation(trackShelf),
            isAlbum = isAlbum,
            related = related,
        )
    }
}
