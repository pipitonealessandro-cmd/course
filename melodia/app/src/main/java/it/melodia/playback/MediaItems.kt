package it.melodia.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import it.melodia.innertube.SongItem

const val EXTRA_ARTIST_ID = "artistId"
const val EXTRA_ALBUM_ID = "albumId"
const val EXTRA_SET_VIDEO_ID = "setVideoId"

/** Placeholder URI; the real stream URL is resolved lazily by the data source. */
fun streamUri(videoId: String): Uri = Uri.parse("https://music.youtube.com/watch?v=$videoId")

fun SongItem.toMediaItem(): MediaItem {
    val extras = Bundle().apply {
        artists.firstOrNull()?.id?.let { putString(EXTRA_ARTIST_ID, it) }
        album?.id?.let { putString(EXTRA_ALBUM_ID, it) }
        setVideoId?.let { putString(EXTRA_SET_VIDEO_ID, it) }
    }
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artistsText)
        .setAlbumTitle(album?.name)
        .setArtworkUri(thumbnail?.let { Uri.parse(it) })
        .setIsPlayable(true)
        .setIsBrowsable(false)
        .setExtras(extras)
        .build()
    return MediaItem.Builder()
        .setMediaId(videoId)
        .setUri(streamUri(videoId))
        .setCustomCacheKey(videoId)
        .setMediaMetadata(metadata)
        .build()
}

/** MediaController strips the local configuration (URI); rebuild it on the service side. */
fun MediaItem.withPlaybackUri(): MediaItem =
    if (localConfiguration != null) this
    else buildUpon().setUri(streamUri(mediaId)).setCustomCacheKey(mediaId).build()
