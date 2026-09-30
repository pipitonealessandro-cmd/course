package it.melodia.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import it.melodia.innertube.AlbumRef
import it.melodia.innertube.ArtistRef
import it.melodia.innertube.SongItem
import it.melodia.playback.EXTRA_ALBUM_ID
import it.melodia.playback.EXTRA_ARTIST_ID
import it.melodia.playback.PlayerConnection
import it.melodia.ui.LocalActions
import it.melodia.ui.components.Thumb
import it.melodia.ui.theme.Green
import it.melodia.ui.theme.Surface
import it.melodia.ui.theme.SurfaceHigh
import kotlinx.coroutines.delay

fun MediaItem.toSongItem(): SongItem {
    val md = mediaMetadata
    val extras = md.extras
    return SongItem(
        videoId = mediaId,
        title = md.title?.toString() ?: "",
        artists = listOf(ArtistRef(md.artist?.toString() ?: "", extras?.getString(EXTRA_ARTIST_ID))),
        album = extras?.getString(EXTRA_ALBUM_ID)?.let { AlbumRef(md.albumTitle?.toString() ?: "", it) },
        thumbnail = md.artworkUri?.toString(),
    )
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** Polls the player position while composed. */
@Composable
private fun rememberProgress(player: PlayerConnection): Triple<Long, Long, Long> {
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var buf by remember { mutableLongStateOf(0L) }
    LaunchedEffect(player) {
        while (true) {
            pos = player.position
            dur = player.duration
            buf = player.bufferedPosition
            delay(500)
        }
    }
    return Triple(pos, dur, buf)
}

@Composable
fun MiniPlayer(player: PlayerConnection, onExpand: () -> Unit, modifier: Modifier = Modifier) {
    val item by player.nowPlaying.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val buffering by player.isBuffering.collectAsState()
    val current = item ?: return
    val (pos, dur, _) = rememberProgress(player)

    Column(
        modifier
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceHigh)
            .clickable(onClick = onExpand)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, drag -> if (drag < -20) onExpand() }
            },
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(current.mediaMetadata.artworkUri?.toString(), Modifier.size(44.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    current.mediaMetadata.title?.toString().orEmpty(),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    current.mediaMetadata.artist?.toString().orEmpty(),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (buffering) {
                CircularProgressIndicator(Modifier.size(24.dp).padding(2.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            IconButton(onClick = { player.togglePlay() }) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pausa", Modifier.size(30.dp))
            }
            IconButton(onClick = { player.next() }) { Icon(Icons.Default.SkipNext, "Successivo") }
        }
        LinearProgressIndicator(
            progress = { if (dur > 0) pos.toFloat() / dur else 0f },
            modifier = Modifier.fillMaxWidth().height(2.dp).padding(horizontal = 8.dp),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.2f),
        )
    }
}

@Composable
fun FullPlayer(player: PlayerConnection, onCollapse: () -> Unit) {
    val actions = LocalActions.current
    val item by player.nowPlaying.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val buffering by player.isBuffering.collectAsState()
    val shuffle by player.shuffle.collectAsState()
    val repeat by player.repeatMode.collectAsState()
    val liked by player.liked.collectAsState()
    val sleepEnd by player.sleepTimerEnd.collectAsState()
    val (pos, dur, _) = rememberProgress(player)
    var seeking by remember { mutableStateOf<Float?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    var showTimer by remember { mutableStateOf(false) }
    val current = item

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF3A3A3A), Color(0xFF121212), Color(0xFF121212))))
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, drag -> if (drag > 30) onCollapse() }
            },
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCollapse) { Icon(Icons.Default.KeyboardArrowDown, "Chiudi", Modifier.size(32.dp)) }
                Text(
                    "In riproduzione",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = { current?.let { actions.showMenu(it.toSongItem()) } }) {
                    Icon(Icons.Default.MoreVert, "Altro")
                }
            }
            Spacer(Modifier.weight(0.5f))
            Thumb(
                current?.mediaMetadata?.artworkUri?.toString(),
                Modifier.fillMaxWidth().aspectRatio(1f),
                RoundedCornerShape(8.dp),
            )
            Spacer(Modifier.weight(0.5f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        current?.mediaMetadata?.title?.toString().orEmpty(),
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        current?.mediaMetadata?.artist?.toString().orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable {
                            current?.mediaMetadata?.extras?.getString(EXTRA_ARTIST_ID)?.let {
                                onCollapse()
                                actions.openArtist(it)
                            }
                        },
                    )
                }
                IconButton(onClick = { player.toggleLike() }) {
                    Icon(
                        if (liked == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        "Mi piace",
                        tint = if (liked == true) Green else Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            val sliderValue = seeking ?: if (dur > 0) pos.toFloat() / dur else 0f
            Slider(
                value = sliderValue.coerceIn(0f, 1f),
                onValueChange = { seeking = it },
                onValueChangeFinished = {
                    seeking?.let { player.seekTo((it * dur).toLong()) }
                    seeking = null
                },
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.25f)),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(fmt(seeking?.let { (it * dur).toLong() } ?: pos), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(if (dur > 0) fmt(dur) else "--:--", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { player.toggleShuffle() }) {
                    Icon(Icons.Default.Shuffle, "Casuale", tint = if (shuffle) Green else Color.White)
                }
                IconButton(onClick = { player.previous() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipPrevious, "Precedente", Modifier.size(40.dp))
                }
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(Color.White).clickable { player.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (buffering) CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(36.dp))
                    else Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pausa", tint = Color.Black, modifier = Modifier.size(42.dp))
                }
                IconButton(onClick = { player.next() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipNext, "Successivo", Modifier.size(40.dp))
                }
                IconButton(onClick = { player.cycleRepeat() }) {
                    Icon(
                        if (repeat == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        "Ripeti",
                        tint = if (repeat != Player.REPEAT_MODE_OFF) Green else Color.White,
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showTimer = true }) {
                    Icon(Icons.Default.Bedtime, "Timer", tint = if (sleepEnd != null) Green else Color.White)
                }
                sleepEnd?.let { end ->
                    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                    LaunchedEffect(end) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
                    Text(fmt(end - now), style = MaterialTheme.typography.bodySmall, color = Green)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showQueue = true }) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "Coda") }
            }
        }
    }

    if (showQueue) QueueSheet(player) { showQueue = false }
    if (showTimer) SleepTimerDialog(player) { showTimer = false }
}

@Composable
private fun QueueSheet(player: PlayerConnection, onDismiss: () -> Unit) {
    val queue by player.queue.collectAsState()
    val index by player.currentIndex.collectAsState()
    val state = rememberLazyListState(initialFirstVisibleItemIndex = index.coerceAtLeast(0))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Surface) {
        Text("Coda di riproduzione", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 8.dp))
        LazyColumn(state = state, modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            itemsIndexed(queue, key = { i, m -> "$i-${m.mediaId}" }) { i, mi ->
                val isCurrent = i == index
                Row(
                    Modifier.fillMaxWidth().clickable { player.skipTo(i) }.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Thumb(mi.mediaMetadata.artworkUri?.toString(), Modifier.size(44.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            mi.mediaMetadata.title?.toString().orEmpty(),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (isCurrent) Green else Color.White,
                        )
                        Text(
                            mi.mediaMetadata.artist?.toString().orEmpty(),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!isCurrent) {
                        IconButton(onClick = { if (i > 0) player.move(i, i - 1) }) { Icon(Icons.Default.KeyboardArrowUp, "Su") }
                        IconButton(onClick = { player.removeAt(i) }) { Icon(Icons.Default.Close, "Rimuovi") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepTimerDialog(player: PlayerConnection, onDismiss: () -> Unit) {
    val active = player.sleepTimerEnd.collectAsState().value != null
    var minutes by remember { mutableFloatStateOf(30f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Timer di spegnimento") },
        text = {
            Column {
                Text("Metti in pausa tra ${minutes.toInt()} minuti")
                Slider(value = minutes, onValueChange = { minutes = it }, valueRange = 5f..120f, steps = 22)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                player.setSleepTimer(minutes.toInt())
                onDismiss()
            }) { Text("Avvia") }
        },
        dismissButton = {
            TextButton(onClick = {
                if (active) player.setSleepTimer(null)
                onDismiss()
            }) { Text(if (active) "Disattiva timer" else "Annulla") }
        },
    )
}
