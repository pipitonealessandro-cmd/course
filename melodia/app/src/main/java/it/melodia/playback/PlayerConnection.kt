package it.melodia.playback

import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import it.melodia.MelodiaApp
import it.melodia.innertube.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI-side handle to the playback service (autoplay lives in QueueExtender). Exposes
 * the player state as flows for Compose.
 */
class PlayerConnection(private val app: MelodiaApp) {

    private val scope = app.scope
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val _nowPlaying = MutableStateFlow<MediaItem?>(null)
    val nowPlaying: StateFlow<MediaItem?> = _nowPlaying
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering
    private val _queue = MutableStateFlow<List<MediaItem>>(emptyList())
    val queue: StateFlow<List<MediaItem>> = _queue
    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex
    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle
    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode
    private val _liked = MutableStateFlow<Boolean?>(null)
    val liked: StateFlow<Boolean?> = _liked
    private val _sleepTimerEnd = MutableStateFlow<Long?>(null)
    val sleepTimerEnd: StateFlow<Long?> = _sleepTimerEnd
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    val position: Long get() = controller?.currentPosition ?: 0L
    val duration: Long get() = controller?.duration?.takeIf { it > 0 } ?: 0L
    val bufferedPosition: Long get() = controller?.bufferedPosition ?: 0L

    private var radioGeneration = 0
    private var sleepJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync()

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            refreshLike()
        }

        override fun onPlayerError(error: PlaybackException) {
            val msg = error.cause?.message ?: error.message ?: "Errore di riproduzione"
            _messages.tryEmit(msg)
        }
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val f = MediaController.Builder(app, token).buildAsync()
        future = f
        f.addListener({
            runCatching { f.get() }.onSuccess { c ->
                controller = c
                c.addListener(listener)
                sync()
                refreshLike()
            }
        }, ContextCompat.getMainExecutor(app))
    }

    private suspend fun awaitController(): MediaController {
        connect()
        return controller ?: future!!.await()
    }

    private fun sync() {
        val c = controller ?: return
        _nowPlaying.value = c.currentMediaItem
        _isPlaying.value = c.isPlaying
        _isBuffering.value = c.playbackState == Player.STATE_BUFFERING
        _currentIndex.value = c.currentMediaItemIndex
        _shuffle.value = c.shuffleModeEnabled
        _repeatMode.value = c.repeatMode
        val count = c.mediaItemCount
        if (count != _queue.value.size || (0 until count).any { c.getMediaItemAt(it).mediaId != _queue.value[it].mediaId }) {
            _queue.value = (0 until count).map { c.getMediaItemAt(it) }
        }
    }

    private fun launchWithController(block: suspend (MediaController) -> Unit) {
        scope.launch {
            try {
                block(awaitController())
            } catch (e: Exception) {
                _messages.tryEmit(e.message ?: "Errore")
            }
        }
    }

    // ------------------------------------------------------------------ starting playback

    /** Plays a list (album, playlist…) starting at [startIndex]. */
    fun playQueue(songs: List<SongItem>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        launchWithController { c ->
            radioGeneration++
            c.shuffleModeEnabled = shuffle
            val start = if (shuffle && startIndex == 0) songs.indices.random() else startIndex
            c.setMediaItems(songs.map { it.toMediaItem() }, start, 0L)
            c.prepare()
            c.play()
        }
    }

    /** Plays one song; with autoplay enabled the service then queues similar songs (QueueExtender). */
    fun playSong(song: SongItem) = playQueue(listOf(song))

    /** Explicit "song radio": plays the song followed by similar songs. */
    fun startRadio(song: SongItem) {
        launchWithController { c ->
            val gen = ++radioGeneration
            val page = withContext(Dispatchers.IO) { app.yt.next(song.videoId, "RDAMVM${song.videoId}") }
            if (gen != radioGeneration) return@launchWithController
            val rest = page.songs.filter { it.videoId != song.videoId }
            c.shuffleModeEnabled = false
            c.setMediaItems((listOf(song) + rest).map { it.toMediaItem() }, 0, 0L)
            c.prepare()
            c.play()
        }
    }

    /** Starts a mix / radio playlist (e.g. "Riproduzione casuale" on an artist or a home mix). */
    fun startPlaylistRadio(playlistId: String, params: String? = null, videoId: String? = null) {
        launchWithController { c ->
            val gen = ++radioGeneration
            val page = withContext(Dispatchers.IO) { app.yt.next(videoId, playlistId, params) }
            if (gen != radioGeneration) return@launchWithController
            if (page.songs.isEmpty()) {
                _messages.tryEmit("Nessun brano trovato")
                return@launchWithController
            }
            c.shuffleModeEnabled = false
            c.setMediaItems(page.songs.map { it.toMediaItem() }, 0, 0L)
            c.prepare()
            c.play()
        }
    }

    fun playNext(song: SongItem) = launchWithController { c ->
        if (c.mediaItemCount == 0) {
            playQueue(listOf(song))
        } else {
            c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
            _messages.tryEmit("Riprodurrò \"${song.title}\" come prossimo brano")
        }
    }

    fun addToQueue(song: SongItem) = launchWithController { c ->
        if (c.mediaItemCount == 0) {
            playQueue(listOf(song))
        } else {
            c.addMediaItem(song.toMediaItem())
            _messages.tryEmit("Aggiunto alla coda")
        }
    }

    // ------------------------------------------------------------------ transport controls

    fun togglePlay() = controller?.let { if (it.isPlaying) it.pause() else { if (it.playbackState == Player.STATE_IDLE) it.prepare(); it.play() } }
    fun next() = controller?.seekToNextMediaItem()
    fun previous() = controller?.let { if (it.currentPosition > 3000) it.seekTo(0) else it.seekToPreviousMediaItem() }
    fun seekTo(ms: Long) = controller?.seekTo(ms)
    fun skipTo(index: Int) = controller?.let { it.seekTo(index, 0L); it.play() }
    fun removeAt(index: Int) = controller?.removeMediaItem(index)
    fun move(from: Int, to: Int) = controller?.moveMediaItem(from, to)
    fun toggleShuffle() = controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = controller?.let {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    // ------------------------------------------------------------------ likes & timer

    private fun refreshLike() {
        val id = controller?.currentMediaItem?.mediaId
        _liked.value = null
        if (id == null || !app.yt.isLoggedIn) return
        scope.launch {
            val status = withContext(Dispatchers.IO) { runCatching { app.yt.likeStatus(id) }.getOrNull() }
            if (controller?.currentMediaItem?.mediaId == id) _liked.value = status
        }
    }

    fun toggleLike() {
        val id = controller?.currentMediaItem?.mediaId ?: return
        if (!app.yt.isLoggedIn) {
            _messages.tryEmit("Accedi con YouTube per usare i \"Mi piace\"")
            return
        }
        val newValue = _liked.value != true
        _liked.value = newValue
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { app.yt.setLike(id, newValue) }.isSuccess }
            if (!ok) {
                _liked.value = !newValue
                _messages.tryEmit("Impossibile aggiornare i \"Mi piace\"")
            } else {
                _messages.tryEmit(if (newValue) "Aggiunto ai brani che ti piacciono" else "Rimosso dai brani che ti piacciono")
            }
        }
    }

    fun setLike(videoId: String, liked: Boolean) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { app.yt.setLike(videoId, liked) }.isSuccess }
            _messages.tryEmit(
                when {
                    !ok -> "Operazione non riuscita"
                    liked -> "Aggiunto ai brani che ti piacciono"
                    else -> "Rimosso dai brani che ti piacciono"
                }
            )
            if (ok && controller?.currentMediaItem?.mediaId == videoId) _liked.value = liked
        }
    }

    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null) {
            _sleepTimerEnd.value = null
            return
        }
        val end = System.currentTimeMillis() + minutes * 60_000L
        _sleepTimerEnd.value = end
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            controller?.pause()
            _sleepTimerEnd.value = null
            _messages.tryEmit("Timer scaduto: riproduzione in pausa")
        }
    }

    fun message(text: String) {
        _messages.tryEmit(text)
    }
}
