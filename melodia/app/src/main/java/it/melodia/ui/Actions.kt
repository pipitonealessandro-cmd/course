package it.melodia.ui

import android.net.Uri
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController
import it.melodia.innertube.AlbumItem
import it.melodia.innertube.ArtistItem
import it.melodia.innertube.PlaylistItem
import it.melodia.innertube.SongItem
import it.melodia.innertube.YTItem
import it.melodia.playback.PlayerConnection

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val LOGIN = "login"
    const val PLAYLIST = "playlist/{id}"
    const val ARTIST = "artist/{id}"
    const val BROWSE = "browse/{id}?params={params}"
    const val LIBRARY_LIST = "librarylist/{kind}"

    fun playlist(id: String) = "playlist/${Uri.encode(id)}"
    fun artist(id: String) = "artist/${Uri.encode(id)}"
    fun browse(id: String, params: String?) = "browse/${Uri.encode(id)}" + (params?.let { "?params=${Uri.encode(it)}" } ?: "")
    fun libraryList(kind: String) = "librarylist/$kind"
}

/** Menu request: the item plus, optionally, the playlist it was shown in (to allow removal). */
data class MenuRequest(val item: YTItem, val playlistId: String? = null, val onRemoved: (() -> Unit)? = null)

class AppActions(
    val nav: NavHostController,
    val player: PlayerConnection,
    private val onShowMenu: (MenuRequest) -> Unit,
    private val onAddToPlaylist: (SongItem) -> Unit,
    private val onOpenPlayer: () -> Unit,
) {
    /** Opens or plays an item. When [queue] is given, a song is played within that list. */
    fun open(item: YTItem, queue: List<SongItem>? = null) {
        when (item) {
            is SongItem -> {
                val idx = queue?.indexOfFirst { it.videoId == item.videoId } ?: -1
                if (queue != null && queue.size > 1 && idx >= 0) player.playQueue(queue, idx)
                else player.playSong(item)
            }
            is AlbumItem -> nav.navigate(Routes.playlist(item.browseId))
            is ArtistItem -> nav.navigate(Routes.artist(item.browseId))
            is PlaylistItem -> when {
                item.browseId != null -> nav.navigate(Routes.playlist(item.browseId!!))
                item.playlistId != null -> player.startPlaylistRadio(item.playlistId!!, item.watchParams)
            }
        }
    }

    fun openArtist(id: String) = nav.navigate(Routes.artist(id))
    fun openPlaylist(id: String) = nav.navigate(Routes.playlist(id))

    fun openMore(browseId: String, params: String?) {
        if (browseId.startsWith("VL") || browseId.startsWith("MPREb")) nav.navigate(Routes.playlist(browseId))
        else nav.navigate(Routes.browse(browseId, params))
    }

    fun showMenu(item: YTItem, playlistId: String? = null, onRemoved: (() -> Unit)? = null) =
        onShowMenu(MenuRequest(item, playlistId, onRemoved))

    fun addToPlaylist(song: SongItem) = onAddToPlaylist(song)
    fun openPlayer() = onOpenPlayer()
}

val LocalActions = staticCompositionLocalOf<AppActions> { error("AppActions non disponibili") }
