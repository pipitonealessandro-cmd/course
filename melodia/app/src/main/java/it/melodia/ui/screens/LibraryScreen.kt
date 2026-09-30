package it.melodia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import it.melodia.MelodiaApp
import it.melodia.innertube.Section
import it.melodia.innertube.SongItem
import it.melodia.innertube.YTItem
import it.melodia.innertube.YouTubeMusic
import it.melodia.ui.LocalActions
import it.melodia.ui.Routes
import it.melodia.ui.components.ItemRow
import it.melodia.ui.components.dataOrNull
import it.melodia.ui.components.LoadStateView
import it.melodia.ui.components.LoaderViewModel
import it.melodia.ui.components.SectionView
import it.melodia.ui.theme.Green

@Composable
fun LibraryScreen(contentPadding: PaddingValues) {
    val actions = LocalActions.current
    val account by MelodiaApp.instance.prefs.account.collectAsState()

    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        Text("La tua libreria", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 16.dp))
        if (account == null) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Accedi per vedere la tua musica", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Playlist, brani che ti piacciono, album e artisti del tuo account YouTube.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { actions.nav.navigate(Routes.LOGIN) }) { Text("Accedi con YouTube") }
            }
            return
        }
        LibraryEntry(
            Icons.Default.Favorite,
            "Brani che ti piacciono",
            "Playlist",
            gradient = true,
        ) { actions.openPlaylist(YouTubeMusic.LIKED_SONGS_ID) }
        LibraryEntry(Icons.AutoMirrored.Filled.QueueMusic, "Playlist", "Le tue playlist e quelle salvate") {
            actions.nav.navigate(Routes.libraryList("playlists"))
        }
        LibraryEntry(Icons.Default.Album, "Album", "Album salvati") { actions.nav.navigate(Routes.libraryList("albums")) }
        LibraryEntry(Icons.Default.Person, "Artisti", "Artisti che segui o ascolti") {
            actions.nav.navigate(Routes.libraryList("artists"))
        }
        LibraryEntry(Icons.Default.History, "Cronologia", "Ascoltati di recente") { actions.nav.navigate(Routes.libraryList("history")) }
    }
}

@Composable
private fun LibraryEntry(icon: ImageVector, title: String, subtitle: String, gradient: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (gradient) Brush.linearGradient(listOf(Color(0xFF4A2BD8), Color(0xFFB9D9D0)))
                    else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant))
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (gradient) Color.White else Green, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

class LibraryListViewModel(kind: String) : LoaderViewModel<List<YTItem>>({
    val yt = MelodiaApp.instance.yt
    when (kind) {
        "playlists" -> yt.libraryPlaylists()
        "albums" -> yt.libraryAlbums()
        "artists" -> yt.libraryArtists()
        "history" -> yt.history()
        else -> emptyList()
    }
})

@Composable
fun LibraryListScreen(kind: String, contentPadding: PaddingValues) {
    val vm: LibraryListViewModel = viewModel(key = "lib-$kind") { LibraryListViewModel(kind) }
    val state by vm.state.collectAsState()
    val actions = LocalActions.current
    val title = when (kind) {
        "playlists" -> "Playlist"
        "albums" -> "Album"
        "artists" -> "Artisti"
        "history" -> "Cronologia"
        else -> ""
    }
    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        TopBar(title) { actions.nav.popBackStack() }
        LoadStateView(state, onRetry = { vm.reload() }) { items ->
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Niente da mostrare", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val songs = items.filterIsInstance<SongItem>()
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(items, key = { it.key }) { item ->
                        ItemRow(item, onClick = { actions.open(item, songs.takeIf { it.isNotEmpty() }) }, onMore = { actions.showMenu(item) })
                    }
                }
            }
        }
    }
}

@Composable
fun TopBar(title: String?, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") }
        Text(title.orEmpty(), style = MaterialTheme.typography.titleLarge, maxLines = 1)
    }
}

class BrowseViewModel(id: String, params: String?) :
    LoaderViewModel<Pair<String?, List<Section>>>({ MelodiaApp.instance.yt.browseSections(id, params) })

@Composable
fun BrowseScreen(id: String, params: String?, contentPadding: PaddingValues) {
    val vm: BrowseViewModel = viewModel(key = "browse-$id-$params") { BrowseViewModel(id, params) }
    val state by vm.state.collectAsState()
    val actions = LocalActions.current
    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        val title = state.dataOrNull()?.first
        TopBar(title) { actions.nav.popBackStack() }
        LoadStateView(state, onRetry = { vm.reload() }) { (_, sections) ->
            val single = sections.singleOrNull()
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                if (single != null) {
                    // A single grid/list ("more" pages): show it as a vertical list.
                    val songs = single.items.filterIsInstance<SongItem>()
                    items(single.items, key = { it.key }) { item ->
                        ItemRow(item, onClick = { actions.open(item, songs.takeIf { it.size > 1 }) }, onMore = { actions.showMenu(item) })
                    }
                } else {
                    items(sections.size) { i ->
                        SectionView(
                            sections[i],
                            onItemClick = { item, _ -> actions.open(item) },
                            onItemMore = { actions.showMenu(it) },
                            onMore = sections[i].moreBrowseId?.let { mid -> { actions.openMore(mid, sections[i].moreParams) } },
                        )
                    }
                }
            }
        }
    }
}
