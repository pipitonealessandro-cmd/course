package it.melodia.playback

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import it.melodia.MelodiaApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Endless autoplay: when the queue is about to run out, appends songs from the YouTube Music
 * radio of the last song (same artist, similar artists, same genre).
 *
 * Lives in the playback service so it also works when playback is controlled from the
 * notification or Android Auto.
 */
class QueueExtender(
    private val player: Player,
    private val app: MelodiaApp,
    private val scope: CoroutineScope,
) : Player.Listener {

    private class Radio(val playlistId: String, var continuation: String?, val ids: MutableSet<String>)

    private var radio: Radio? = null
    private var job: Job? = null
    /** Seed that returned nothing: don't hammer the API with it again. */
    private var failedSeed: String? = null
    private var failedAt = 0L

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = check()

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) check()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) check()
    }

    override fun onRepeatModeChanged(repeatMode: Int) = check()

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = check()

    /** Number of songs left after the current one (capped), following shuffle order. */
    private fun remaining(): Int {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return 0
        var index = player.currentMediaItemIndex
        var count = 0
        while (count < 5) {
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
            if (index == C.INDEX_UNSET) break
            count++
        }
        return count
    }

    fun check() {
        if (!app.prefs.autoRadio.value) return
        if (player.repeatMode != Player.REPEAT_MODE_OFF) return
        if (player.mediaItemCount == 0) return
        if (job?.isActive == true) return
        if (remaining() > 2) return

        val seedItem = if (player.shuffleModeEnabled) player.currentMediaItem
        else player.getMediaItemAt(player.mediaItemCount - 1)
        val seedId = seedItem?.mediaId?.takeIf { it.isNotEmpty() } ?: return
        val current = radio?.takeIf { seedId in it.ids && it.continuation != null }
        if (current == null && failedSeed == seedId && System.currentTimeMillis() - failedAt < RETRY_MS) return

        job = scope.launch {
            try {
                val page = withContext(Dispatchers.IO) {
                    if (current != null) app.yt.nextContinuation(current.continuation!!, current.playlistId)
                    else app.yt.next(seedId, "RDAMVM$seedId")
                }
                val existing = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
                val fresh = page.songs.filter { it.videoId !in existing }.distinctBy { it.videoId }
                if (fresh.isEmpty() && page.continuation == null) {
                    failedSeed = seedId
                    failedAt = System.currentTimeMillis()
                    return@launch
                }
                val wasEnded = player.playbackState == Player.STATE_ENDED
                player.addMediaItems(fresh.map { it.toMediaItem() })
                val ids = current?.ids ?: mutableSetOf(seedId)
                ids += fresh.map { it.videoId }
                radio = Radio(current?.playlistId ?: page.playlistId ?: "RDAMVM$seedId", page.continuation, ids)
                if (wasEnded && player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            } catch (e: Exception) {
                Log.w("QueueExtender", "autoplay failed for $seedId", e)
                failedSeed = seedId
                failedAt = System.currentTimeMillis()
                app.player.message("Impossibile caricare brani simili")
            }
        }
    }

    private companion object {
        const val RETRY_MS = 60_000L
    }
}
