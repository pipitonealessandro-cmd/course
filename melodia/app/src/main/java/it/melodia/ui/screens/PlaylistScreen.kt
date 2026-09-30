package it.melodia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import it.melodia.MelodiaApp
import it.melodia.innertube.AlbumItem
import it.melodia.innertube.PlaylistItem
import it.melodia.innertube.PlaylistPage
import it.melodia.innertube.SongItem
import it.melodia.ui.LocalActions
import it.melodia.ui.components.LoadState
import it.melodia.ui.components.dataOrNull
import it.melodia.ui.components.LoadStateView
import it.melodia.ui.components.LoaderViewModel
import it.melodia.ui.components.SectionView
import it.melodia.ui.components.SongRow
import it.melodia.ui.components.Thumb
import it.melodia.ui.theme.SurfaceHigh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistViewModel(val id: String) : LoaderViewModel<PlaylistPage>({ MelodiaApp.instance.yt.playlist(id) }) {
    private val yt = MelodiaApp.instance.yt
    val loadingMore = MutableStateFlow(false)

    fun loadMore() {
        val current = state.value.dataOrNull() ?: return
        val token = current.continuation ?: return
        if (loadingMore.value) return
        loadingMore.value = true
        viewModelScope.launch {
            try {
                val more = withContext(Dispatchers.IO) { yt.playlistContinuation(token) }
                val songs = more.items.map { if (it.thumbnail == null) it.copy(thumbnail = current.thumbnail) else it }
                _state.value = LoadState.Ready(current.copy(songs = (current.songs + songs).distinctBy { it.setVideoId ?: it.videoId }, continuation = more.continuation))
            } catch (_: Exception) {
                _state.value = LoadState.Ready(current.copy(continuation = null))
            } finally {
                loadingMore.value = false
            }
        }
    }

    /** Loads every remaining page (up to a limit) and then runs [then] with the full list. */
    fun withAllSongs(then: (List<SongItem>) -> Unit) {
        viewModelScope.launch {
            var page = state.value.dataOrNull() ?: return@launch
            var pages = 0
            while (page.continuation != null && pages++ < 30) {
                val more = withContext(Dispatchers.IO) { runCatching { yt.playlistContinuation(page.continuation!!) }.getOrNull() } ?: break
                page = page.copy(songs = (page.songs + more.items).distinctBy { it.setVideoId ?: it.videoId }, continuation = more.continuation)
            }
            _state.value = LoadState.Ready(page)
            then(page.songs)
        }
    }

    fun removeSong(song: SongItem) {
        val current = state.value.dataOrNull() ?: return
        _state.value = LoadState.Ready(current.copy(songs = current.songs.filter { it.setVideoId != song.setVideoId || it.videoId != song.videoId }))
    }
}

@Composable
fun PlaylistScreen(id: String, contentPadding: PaddingValues) {
    val vm: PlaylistViewModel = viewModel(key = "playlist-$id") { PlaylistViewModel(id) }
    val state by vm.state.collectAsState()
    val loadingMore by vm.loadingMore.collectAsState()
    val actions = LocalActions.current
    val nowPlaying by actions.player.nowPlaying.collectAsState()

    Box(Modifier.fillMaxSize()) {
        LoadStateView(state, onRetry = { vm.reload() }) { page ->
            LazyColumn(contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 24.dp)) {
                item { PlaylistHeader(page, contentPadding, vm) }
                itemsIndexed(page.songs, key = { i, s -> "$i-${s.videoId}" }) { index, song ->
                    SongRow(
                        song,
                        onClick = { vm.withAllSongs { all -> actions.player.playQueue(all, all.indexOfFirst { it.videoId == song.videoId && it.setVideoId == song.setVideoId }.coerceAtLeast(0)) } },
                        onMore = { actions.showMenu(song, page.playlistId, onRemoved = { vm.removeSong(song) }) },
                        showThumb = !page.isAlbum,
                        index = index + 1,
                        isCurrent = nowPlaying?.mediaId == song.videoId,
                    )
                    if (index >= page.songs.size - 5 && page.continuation != null) {
                        LaunchedEffect(page.songs.size) { vm.loadMore() }
                    }
                }
                if (loadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(28.dp))
                        }
                    }
                }
                if (page.songs.isEmpty()) {
                    item {
                        Text(
                            "Questa playlist è vuota",
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                page.related.forEach { section ->
                    item {
                        SectionView(
                            section,
                            onItemClick = { item, _ -> actions.open(item) },
                            onItemMore = { actions.showMenu(it) },
                            onMore = null,
                        )
                    }
                }
            }
        }
        IconButton(
            onClick = { actions.nav.popBackStack() },
            modifier = Modifier.padding(top = contentPadding.calculateTopPadding()).padding(4.dp),
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") }
    }
}

@Composable
private fun PlaylistHeader(page: PlaylistPage, contentPadding: PaddingValues, vm: PlaylistViewModel) {
    val actions = LocalActions.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(SurfaceHigh, Color.Transparent)))
            .padding(top = contentPadding.calculateTopPadding() + 48.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Thumb(page.thumbnail, Modifier.size(220.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            page.title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        page.subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
        page.secondSubtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = {
                val menuItem = if (page.isAlbum) AlbumItem(vm.id, page.title, emptyList(), null, page.thumbnail, page.playlistId)
                else PlaylistItem(null, page.playlistId, page.title, page.subtitle, page.thumbnail)
                actions.showMenu(menuItem)
            }) { Icon(Icons.Default.MoreVert, "Altro") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.withAllSongs { actions.player.playQueue(it, shuffle = true) } }) {
                Icon(Icons.Default.Shuffle, "Casuale", Modifier.size(28.dp))
            }
            FilledIconButton(
                onClick = { vm.withAllSongs { actions.player.playQueue(it) } },
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) { Icon(Icons.Default.PlayArrow, "Riproduci", Modifier.size(32.dp)) }
        }
    }
}
