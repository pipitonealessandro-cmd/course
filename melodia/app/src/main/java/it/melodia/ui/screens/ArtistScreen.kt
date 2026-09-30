package it.melodia.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import it.melodia.MelodiaApp
import it.melodia.innertube.ArtistPage
import it.melodia.innertube.SongItem
import it.melodia.ui.LocalActions
import it.melodia.ui.components.LoadStateView
import it.melodia.ui.components.LoaderViewModel
import it.melodia.ui.components.SectionView
import it.melodia.ui.components.Thumb
import it.melodia.ui.theme.Background

class ArtistViewModel(id: String) : LoaderViewModel<ArtistPage>({ MelodiaApp.instance.yt.artist(id) })

@Composable
fun ArtistScreen(id: String, contentPadding: PaddingValues) {
    val vm: ArtistViewModel = viewModel(key = "artist-$id") { ArtistViewModel(id) }
    val state by vm.state.collectAsState()
    val actions = LocalActions.current

    Box(Modifier.fillMaxSize()) {
        LoadStateView(state, onRetry = { vm.reload() }) { page ->
            LazyColumn(contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 24.dp)) {
                item {
                    Box(Modifier.fillMaxWidth().aspectRatio(1.3f)) {
                        Thumb(
                            page.thumbnail,
                            Modifier.fillMaxSize().drawWithContent {
                                drawContent()
                                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Background), startY = size.height * 0.4f))
                            },
                            RectangleShape,
                        )
                        Text(
                            page.title,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                        )
                    }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        page.radioPlaylistId?.let { radio ->
                            OutlinedButton(onClick = { actions.player.startPlaylistRadio(radio) }) {
                                Icon(Icons.Default.Radio, null, Modifier.size(18.dp))
                                Text("  Radio")
                            }
                        }
                        Box(Modifier.weight(1f))
                        val shuffle = page.shufflePlaylistId
                        FilledIconButton(
                            onClick = {
                                if (shuffle != null) actions.player.startPlaylistRadio(shuffle, "wAEB8gECKAE%3D")
                                else page.sections.firstOrNull()?.items?.filterIsInstance<SongItem>()?.let { actions.player.playQueue(it, shuffle = true) }
                            },
                            modifier = Modifier.size(56.dp),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                        ) { Icon(Icons.Default.PlayArrow, "Riproduci", Modifier.size(32.dp)) }
                    }
                }
                page.sections.forEach { section ->
                    item {
                        SectionView(
                            section,
                            onItemClick = { item, all ->
                                val songs = all.filterIsInstance<SongItem>()
                                actions.open(item, songs.takeIf { item is SongItem && songs.size == all.size })
                            },
                            onItemMore = { actions.showMenu(it) },
                            onMore = section.moreBrowseId?.let { id -> { actions.openMore(id, section.moreParams) } },
                        )
                    }
                }
                page.description?.let { desc ->
                    item {
                        var expanded by remember { mutableStateOf(false) }
                        Text(
                            "Informazioni",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(16.dp, 24.dp, 16.dp, 8.dp),
                        )
                        Text(
                            desc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (expanded) Int.MAX_VALUE else 4,
                            modifier = Modifier.padding(horizontal = 16.dp).clickable { expanded = !expanded },
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
