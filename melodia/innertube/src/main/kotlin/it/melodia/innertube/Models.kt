package it.melodia.innertube

data class ArtistRef(val name: String, val id: String?)

data class AlbumRef(val name: String, val id: String)

sealed interface YTItem {
    val key: String
    val title: String
    val thumbnail: String?
}

data class SongItem(
    val videoId: String,
    override val title: String,
    val artists: List<ArtistRef>,
    val album: AlbumRef? = null,
    val durationSec: Int? = null,
    override val thumbnail: String? = null,
    val isVideo: Boolean = false,
    /** Playlist context (e.g. album) the song was found in, useful to start a queue. */
    val playlistId: String? = null,
    val setVideoId: String? = null,
) : YTItem {
    override val key get() = "song:$videoId"
    val artistsText get() = artists.joinToString(", ") { it.name }
}

data class AlbumItem(
    val browseId: String,
    override val title: String,
    val artists: List<ArtistRef>,
    val year: String? = null,
    override val thumbnail: String? = null,
    val playlistId: String? = null,
) : YTItem {
    override val key get() = "album:$browseId"
}

data class ArtistItem(
    val browseId: String,
    override val title: String,
    override val thumbnail: String? = null,
    val subtitle: String? = null,
) : YTItem {
    override val key get() = "artist:$browseId"
}

data class PlaylistItem(
    /** Browse id, usually "VL" + playlistId. Null for radio/mix entries that can only be played. */
    val browseId: String?,
    val playlistId: String?,
    override val title: String,
    val author: String? = null,
    override val thumbnail: String? = null,
    /** For mixes / radios: the watch endpoint to start playback. */
    val watchVideoId: String? = null,
    val watchParams: String? = null,
) : YTItem {
    override val key get() = "playlist:${browseId ?: playlistId}"
}

data class Section(
    val title: String?,
    val items: List<YTItem>,
    /** Browse id of the "more" button, if any. */
    val moreBrowseId: String? = null,
    val moreParams: String? = null,
)

data class HomePage(
    val sections: List<Section>,
    val continuation: String?,
)

data class SearchPage(
    val sections: List<Section>,
    val continuation: String?,
)

data class ArtistPage(
    val title: String,
    val thumbnail: String?,
    val description: String?,
    val sections: List<Section>,
    val shufflePlaylistId: String?,
    val radioPlaylistId: String?,
)

/** Album or playlist page. */
data class PlaylistPage(
    val title: String,
    val subtitle: String?,
    val secondSubtitle: String?,
    val thumbnail: String?,
    val playlistId: String?,
    val songs: List<SongItem>,
    val continuation: String?,
    val isAlbum: Boolean,
    val related: List<Section> = emptyList(),
)

data class ContinuationPage<T>(
    val items: List<T>,
    val continuation: String?,
)

data class QueuePage(
    val songs: List<SongItem>,
    val continuation: String?,
    val playlistId: String?,
)

data class AccountInfo(
    val name: String,
    val email: String?,
    val photo: String?,
)

data class AudioStreamInfo(
    val url: String,
    val mimeType: String,
    val bitrate: Int,
    val contentLength: Long?,
)

enum class SearchFilter(val params: String?) {
    ALL(null),
    SONGS("EgWKAQIIAWoMEA4QChADEAQQCRAF"),
    VIDEOS("EgWKAQIQAWoMEA4QChADEAQQCRAF"),
    ALBUMS("EgWKAQIYAWoMEA4QChADEAQQCRAF"),
    ARTISTS("EgWKAQIgAWoMEA4QChADEAQQCRAF"),
    PLAYLISTS("EgeKAQQoAEABagwQDhAKEAMQBBAJEAU%3D"),
}

class InnertubeException(message: String, cause: Throwable? = null) : Exception(message, cause)
