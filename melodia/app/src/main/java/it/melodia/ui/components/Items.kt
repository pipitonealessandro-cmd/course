package it.melodia.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import it.melodia.innertube.AlbumItem
import it.melodia.innertube.ArtistItem
import it.melodia.innertube.PlaylistItem
import it.melodia.innertube.Section
import it.melodia.innertube.SongItem
import it.melodia.innertube.YTItem
import it.melodia.ui.theme.Green
import it.melodia.ui.theme.SectionTitle
import it.melodia.ui.theme.SurfaceHigh

@Composable
fun Thumb(url: String?, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(4.dp)) {
    Box(modifier.clip(shape).background(SurfaceHigh), contentAlignment = Alignment.Center) {
        if (url != null) {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = { NoteIcon() },
            )
        } else NoteIcon()
    }
}

@Composable
private fun NoteIcon() {
    Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

fun YTItem.subtitle(): String = when (this) {
    is SongItem -> listOfNotNull(
        if (isVideo) "Video" else "Brano",
        artistsText.ifBlank { null },
        album?.name,
    ).joinToString(" • ")
    is AlbumItem -> listOfNotNull("Album", artists.joinToString(", ") { it.name }.ifBlank { null }, year).joinToString(" • ")
    is ArtistItem -> subtitle ?: "Artista"
    is PlaylistItem -> listOfNotNull("Playlist", author).joinToString(" • ")
}

fun formatDuration(sec: Int?): String? {
    if (sec == null) return null
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: SongItem,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    showThumb: Boolean = true,
    index: Int? = null,
    isCurrent: Boolean = false,
) {
    Row(
        modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onMore)
            .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showThumb) {
            Thumb(song.thumbnail, Modifier.size(52.dp))
            Spacer(Modifier.width(12.dp))
        } else if (index != null) {
            Text(
                "$index",
                modifier = Modifier.width(32.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) Green else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                listOfNotNull(song.artistsText.ifBlank { null }, formatDuration(song.durationSec)).joinToString(" • "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onMore) {
            Icon(Icons.Default.MoreVert, "Altro", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemRow(item: YTItem, onClick: () -> Unit, onMore: (() -> Unit)?, modifier: Modifier = Modifier) {
    if (item is SongItem && onMore != null) {
        SongRow(item, onClick, onMore, modifier)
        return
    }
    Row(
        modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onMore)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(item.thumbnail, Modifier.size(56.dp), if (item is ArtistItem) CircleShape else RoundedCornerShape(4.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(
                item.subtitle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onMore != null) {
            IconButton(onClick = onMore) {
                Icon(Icons.Default.MoreVert, "Altro", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemCard(item: YTItem, onClick: () -> Unit, onLongClick: (() -> Unit)?, size: Dp = 150.dp) {
    val isArtist = item is ArtistItem
    val wide = item is SongItem && item.isVideo
    val width = if (wide) size * 16 / 9 else size
    Column(
        Modifier
            .width(width)
            .clip(RoundedCornerShape(6.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(bottom = 4.dp),
        horizontalAlignment = if (isArtist) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Thumb(
            item.thumbnail,
            Modifier.width(width).height(size),
            if (isArtist) CircleShape else RoundedCornerShape(6.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            item.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            when (item) {
                is SongItem -> item.artistsText
                is AlbumItem -> listOfNotNull(item.year, item.artists.joinToString(", ") { it.name }).joinToString(" • ")
                is ArtistItem -> "Artista"
                is PlaylistItem -> item.author ?: "Playlist"
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A titled shelf: songs as a vertical list, everything else as a horizontal carousel. */
@Composable
fun SectionView(
    section: Section,
    onItemClick: (YTItem, List<YTItem>) -> Unit,
    onItemMore: (YTItem) -> Unit,
    onMore: (() -> Unit)?,
) {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        if (section.title != null) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(section.title!!, style = SectionTitle, modifier = Modifier.weight(1f), maxLines = 2)
                if (onMore != null) TextButton(onClick = onMore) { Text("Altro") }
            }
        }
        val allSongs = section.items.all { it is SongItem && !it.isVideo }
        if (allSongs) {
            section.items.take(8).forEach { item ->
                SongRow(item as SongItem, onClick = { onItemClick(item, section.items) }, onMore = { onItemMore(item) })
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(section.items, key = { it.key }) { item ->
                    ItemCard(item, onClick = { onItemClick(item, section.items) }, onLongClick = { onItemMore(item) })
                }
            }
        }
    }
}
