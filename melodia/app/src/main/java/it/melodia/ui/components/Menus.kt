package it.melodia.ui.components

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.melodia.MelodiaApp
import it.melodia.innertube.AlbumItem
import it.melodia.innertube.ArtistItem
import it.melodia.innertube.PlaylistItem
import it.melodia.innertube.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import it.melodia.ui.AppActions
import it.melodia.ui.MenuRequest

@Composable
private fun MenuEntry(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(20.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ItemMenuSheet(request: MenuRequest, actions: AppActions, onDismiss: () -> Unit) {
    val app = MelodiaApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val item = request.item
    val player = actions.player
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun act(block: () -> Unit) {
        onDismiss()
        block()
    }

    fun share(url: String, title: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
            putExtra(Intent.EXTRA_SUBJECT, title)
        }
        context.startActivity(Intent.createChooser(send, "Condividi").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun savePlaylist(id: String, save: Boolean) = scope.launch {
        val ok = withContext(Dispatchers.IO) { runCatching { app.yt.setPlaylistSaved(id, save) }.isSuccess }
        player.message(if (!ok) "Operazione non riuscita" else if (save) "Salvato in Libreria" else "Rimosso dalla Libreria")
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Thumb(item.thumbnail, Modifier.size(56.dp), if (item is ArtistItem) CircleShape else RoundedCornerShape(4.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(item.subtitle(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            when (item) {
                is SongItem -> {
                    MenuEntry(Icons.Default.PlayArrow, "Riproduci ora") { act { player.playSong(item) } }
                    MenuEntry(Icons.AutoMirrored.Filled.PlaylistPlay, "Riproduci dopo") { act { player.playNext(item) } }
                    MenuEntry(Icons.AutoMirrored.Filled.QueueMusic, "Aggiungi alla coda") { act { player.addToQueue(item) } }
                    MenuEntry(Icons.Default.Radio, "Avvia radio del brano") { act { player.startRadio(item) } }
                    if (app.yt.isLoggedIn) {
                        MenuEntry(Icons.Default.Favorite, "Metti \"Mi piace\"") { act { player.setLike(item.videoId, true) } }
                        MenuEntry(Icons.Default.FavoriteBorder, "Togli \"Mi piace\"") { act { player.setLike(item.videoId, false) } }
                        MenuEntry(Icons.AutoMirrored.Filled.PlaylistAdd, "Aggiungi a una playlist") { act { actions.addToPlaylist(item) } }
                        val setVideoId = item.setVideoId
                        val pl = request.playlistId
                        if (pl != null && setVideoId != null) {
                            MenuEntry(Icons.Default.Delete, "Rimuovi da questa playlist") {
                                act {
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) {
                                            runCatching { app.yt.removeFromPlaylist(pl, item.videoId, setVideoId) }.isSuccess
                                        }
                                        player.message(if (ok) "Rimosso dalla playlist" else "Operazione non riuscita")
                                        if (ok) request.onRemoved?.invoke()
                                    }
                                }
                            }
                        }
                    }
                    item.artists.firstOrNull { it.id != null }?.let { artist ->
                        MenuEntry(Icons.Default.Person, "Vai all'artista") { act { actions.openArtist(artist.id!!) } }
                    }
                    item.album?.let { album ->
                        MenuEntry(Icons.Default.Album, "Vai all'album") { act { actions.openPlaylist(album.id) } }
                    }
                    MenuEntry(Icons.Default.Share, "Condividi") {
                        act { share("https://music.youtube.com/watch?v=${item.videoId}", item.title) }
                    }
                }
                is AlbumItem, is PlaylistItem -> {
                    val browseId = (item as? AlbumItem)?.browseId ?: (item as PlaylistItem).browseId
                    val playlistId = (item as? AlbumItem)?.playlistId ?: (item as? PlaylistItem)?.playlistId
                    if (browseId != null) {
                        MenuEntry(Icons.Default.PlayArrow, "Apri") { act { actions.open(item) } }
                    }
                    if (playlistId != null) {
                        MenuEntry(Icons.Default.Shuffle, "Riproduzione casuale") {
                            act { player.startPlaylistRadio(playlistId, "wAEB8gECKAE%3D") }
                        }
                        MenuEntry(Icons.Default.Radio, "Avvia radio") { act { player.startPlaylistRadio("RDAMPL$playlistId") } }
                        if (app.yt.isLoggedIn && !playlistId.startsWith("RD")) {
                            MenuEntry(Icons.Default.BookmarkAdd, "Salva in Libreria") { act { savePlaylist(playlistId, true) } }
                            MenuEntry(Icons.Default.BookmarkRemove, "Rimuovi dalla Libreria") { act { savePlaylist(playlistId, false) } }
                        }
                        MenuEntry(Icons.Default.Share, "Condividi") {
                            act { share("https://music.youtube.com/playlist?list=$playlistId", item.title) }
                        }
                    }
                }
                is ArtistItem -> {
                    MenuEntry(Icons.Default.Person, "Apri artista") { act { actions.open(item) } }
                    MenuEntry(Icons.Default.Share, "Condividi") {
                        act { share("https://music.youtube.com/channel/${item.browseId}", item.title) }
                    }
                }
            }
        }
    }
}

@Composable
fun AddToPlaylistDialog(song: SongItem, onDismiss: () -> Unit) {
    val app = MelodiaApp.instance
    val scope = rememberCoroutineScope()
    var playlists by remember { mutableStateOf<List<PlaylistItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching { app.yt.libraryPlaylists() }
        }.onSuccess { list ->
            // Only real playlists can be edited (not "Mi piace" or auto-generated ones).
            playlists = list.filter { it.playlistId != null && it.playlistId != "LM" && !it.playlistId!!.startsWith("RD") }
        }.onFailure { error = it.message }
    }

    fun addTo(playlistId: String, title: String) = scope.launch {
        val ok = withContext(Dispatchers.IO) { runCatching { app.yt.addToPlaylist(playlistId, song.videoId) }.isSuccess }
        app.player.message(if (ok) "Aggiunto a \"$title\"" else "Impossibile aggiungere alla playlist")
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (creating) "Nuova playlist" else "Aggiungi a playlist") },
        text = {
            if (creating) {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Nome") }, singleLine = true)
            } else {
                Column {
                    Row(
                        Modifier.fillMaxWidth().clickable { creating = true }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(12.dp))
                        Text("Nuova playlist")
                    }
                    when {
                        error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                        playlists == null -> LoadingView(Modifier.heightIn(max = 120.dp))
                        else -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                            items(playlists!!, key = { it.key }) { pl ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { addTo(pl.playlistId!!, pl.title) }.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Thumb(pl.thumbnail, Modifier.size(44.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(pl.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(enabled = newName.isNotBlank(), onClick = {
                    scope.launch {
                        val id = withContext(Dispatchers.IO) {
                            runCatching { app.yt.createPlaylist(newName.trim(), listOf(song.videoId)) }.getOrNull()
                        }
                        app.player.message(if (id != null) "Playlist \"${newName.trim()}\" creata" else "Impossibile creare la playlist")
                        onDismiss()
                    }
                }) { Text("Crea") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}
