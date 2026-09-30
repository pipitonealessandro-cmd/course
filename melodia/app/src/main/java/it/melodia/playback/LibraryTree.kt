package it.melodia.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import it.melodia.MelodiaApp
import it.melodia.innertube.AlbumItem
import it.melodia.innertube.ArtistItem
import it.melodia.innertube.PlaylistItem
import it.melodia.innertube.SearchFilter
import it.melodia.innertube.Section
import it.melodia.innertube.SongItem
import it.melodia.innertube.YTItem
import it.melodia.innertube.YouTubeMusic
import java.util.concurrent.ConcurrentHashMap

/**
 * Browse tree shown by Android Auto (and any other media browser).
 *
 * Ids:
 *  - folders: "root", "home", "home/<i>", "liked", "playlists", "history", "pl:<browseId>", "ar:<browseId>"
 *  - songs: "s|<parentId>|<videoId>" so that tapping one plays the whole list from that song
 *  - mixes: "mix|<playlistId>|<params>"
 *
 * All methods are blocking: call them from a background thread.
 */
class LibraryTree(private val app: MelodiaApp) {

    private val yt get() = app.yt
    private val items = ConcurrentHashMap<String, MediaItem>()
    private val songsByParent = ConcurrentHashMap<String, List<SongItem>>()
    @Volatile private var homeSections: List<Section> = emptyList()

    fun root(): MediaItem = folder(ROOT, "Melodia", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)

    fun item(id: String): MediaItem? = items[id]

    fun children(parentId: String): List<MediaItem> = when {
        parentId == ROOT -> buildList {
            add(folder(HOME, "Home", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED))
            if (yt.isLoggedIn) {
                add(folder(LIKED, "Brani che ti piacciono", MediaMetadata.MEDIA_TYPE_PLAYLIST))
                add(folder(PLAYLISTS, "Playlist", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS))
                add(folder(HISTORY, "Ascoltati di recente", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED))
            }
        }
        parentId == HOME -> {
            homeSections = yt.home().sections.filter { it.title != null }
            homeSections.mapIndexed { i, s -> folder("$HOME/$i", s.title!!, MediaMetadata.MEDIA_TYPE_FOLDER_MIXED) }
        }
        parentId.startsWith("$HOME/") -> {
            if (homeSections.isEmpty()) homeSections = yt.home().sections.filter { it.title != null }
            val section = homeSections.getOrNull(parentId.substringAfter('/').toIntOrNull() ?: -1)
            section?.items?.let { toMediaItems(parentId, it) } ?: emptyList()
        }
        parentId == LIKED -> songs(LIKED, loadPlaylistSongs(YouTubeMusic.LIKED_SONGS_ID))
        parentId == PLAYLISTS -> toMediaItems(PLAYLISTS, yt.libraryPlaylists())
        parentId == HISTORY -> songs(HISTORY, yt.history())
        parentId.startsWith("pl:") -> songs(parentId, loadPlaylistSongs(parentId.removePrefix("pl:")))
        parentId.startsWith("ar:") -> {
            val artist = yt.artist(parentId.removePrefix("ar:"))
            toMediaItems(parentId, artist.sections.flatMap { it.items }.distinctBy { it.key })
        }
        else -> emptyList()
    }

    fun search(query: String): List<MediaItem> {
        val parent = "search:$query"
        val songs = yt.search(query, SearchFilter.SONGS).sections.flatMap { it.items }.filterIsInstance<SongItem>()
            .ifEmpty { yt.search(query).sections.flatMap { it.items }.filterIsInstance<SongItem>() }
        return songs(parent, songs)
    }

    /**
     * Turns what a controller asked to play into real, playable items: expands a song tapped in a
     * list to the whole list, loads mixes and handles voice searches ("play X on Melodia").
     */
    fun resolve(requested: List<MediaItem>, startIndex: Int, startPositionMs: Long): MediaItemsWithStartPosition {
        if (requested.size == 1) {
            val one = requested[0]
            val id = one.mediaId
            when {
                id.startsWith("s|") -> {
                    val parent = id.substringAfter("s|").substringBeforeLast('|')
                    val videoId = id.substringAfterLast('|')
                    val list = songsByParent[parent]
                    val idx = list?.indexOfFirst { it.videoId == videoId } ?: -1
                    if (list != null && idx >= 0) {
                        return MediaItemsWithStartPosition(list.map { it.toMediaItem() }, idx, startPositionMs)
                    }
                    return MediaItemsWithStartPosition(listOf(plain(one)), 0, startPositionMs)
                }
                id.startsWith("mix|") -> {
                    val parts = id.split('|')
                    val page = yt.next(null, parts.getOrNull(1), parts.getOrNull(2)?.ifEmpty { null })
                    return MediaItemsWithStartPosition(page.songs.map { it.toMediaItem() }, 0, 0L)
                }
                id.startsWith("pl:") -> {
                    val songs = loadPlaylistSongs(id.removePrefix("pl:"))
                    return MediaItemsWithStartPosition(songs.map { it.toMediaItem() }, 0, 0L)
                }
                id.isEmpty() -> {
                    val query = one.requestMetadata.searchQuery
                    if (!query.isNullOrBlank()) {
                        val first = yt.search(query, SearchFilter.SONGS).sections.flatMap { it.items }
                            .filterIsInstance<SongItem>().firstOrNull()
                            ?: yt.search(query).sections.flatMap { it.items }.filterIsInstance<SongItem>().firstOrNull()
                        // The autoplay radio fills the queue with similar songs afterwards.
                        return MediaItemsWithStartPosition(listOfNotNull(first?.toMediaItem()), 0, 0L)
                    }
                }
            }
        }
        return MediaItemsWithStartPosition(requested.map { plain(it) }, startIndex, startPositionMs)
    }

    /** Maps browse ids back to plain video ids with a stream URI. */
    fun plain(item: MediaItem): MediaItem {
        val id = item.mediaId
        if (id.startsWith("s|")) {
            val videoId = id.substringAfterLast('|')
            return item.buildUpon().setMediaId(videoId).setUri(streamUri(videoId)).setCustomCacheKey(videoId).build()
        }
        return item.withPlaybackUri()
    }

    // ------------------------------------------------------------------ helpers

    private fun loadPlaylistSongs(id: String): List<SongItem> {
        val page = yt.playlist(id)
        val songs = page.songs.toMutableList()
        var token = page.continuation
        var pages = 0
        while (token != null && pages++ < 5) {
            val more = runCatching { yt.playlistContinuation(token!!) }.getOrNull() ?: break
            songs += more.items.map { if (it.thumbnail == null) it.copy(thumbnail = page.thumbnail) else it }
            token = more.continuation
        }
        return songs
    }

    private fun songs(parent: String, list: List<SongItem>): List<MediaItem> {
        songsByParent[parent] = list
        return list.map { song(parent, it) }
    }

    private fun toMediaItems(parent: String, list: List<YTItem>): List<MediaItem> {
        songsByParent[parent] = list.filterIsInstance<SongItem>()
        return list.mapNotNull { item ->
            when (item) {
                is SongItem -> song(parent, item)
                is AlbumItem -> folder("pl:${item.browseId}", item.title, MediaMetadata.MEDIA_TYPE_ALBUM,
                    item.artists.joinToString(", ") { it.name }, item.thumbnail, playable = true)
                is ArtistItem -> folder("ar:${item.browseId}", item.title, MediaMetadata.MEDIA_TYPE_ARTIST, null, item.thumbnail)
                is PlaylistItem -> when {
                    item.browseId != null -> folder("pl:${item.browseId}", item.title, MediaMetadata.MEDIA_TYPE_PLAYLIST,
                        item.author, item.thumbnail, playable = true)
                    item.playlistId != null -> playable("mix|${item.playlistId}|${item.watchParams.orEmpty()}",
                        item.title, item.author, item.thumbnail, MediaMetadata.MEDIA_TYPE_PLAYLIST)
                    else -> null
                }
            }
        }
    }

    private fun song(parent: String, s: SongItem): MediaItem =
        playable("s|$parent|${s.videoId}", s.title, s.artistsText, s.thumbnail, MediaMetadata.MEDIA_TYPE_MUSIC)

    private fun playable(id: String, title: String, subtitle: String?, thumb: String?, type: Int): MediaItem {
        val md = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(subtitle)
            .setArtworkUri(ArtworkProvider.uriFor(thumb))
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(type)
            .build()
        return MediaItem.Builder().setMediaId(id).setMediaMetadata(md).build().also { items[id] = it }
    }

    private fun folder(
        id: String,
        title: String,
        type: Int,
        subtitle: String? = null,
        thumb: String? = null,
        playable: Boolean = false,
    ): MediaItem {
        val md = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(subtitle)
            .setArtworkUri(ArtworkProvider.uriFor(thumb))
            .setIsPlayable(playable)
            .setIsBrowsable(true)
            .setMediaType(type)
            .build()
        return MediaItem.Builder().setMediaId(id).setMediaMetadata(md).build().also { items[id] = it }
    }

    companion object {
        const val ROOT = "root"
        const val HOME = "home"
        const val LIKED = "liked"
        const val PLAYLISTS = "playlists"
        const val HISTORY = "history"
    }
}
