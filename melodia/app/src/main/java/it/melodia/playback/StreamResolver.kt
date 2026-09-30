package it.melodia.playback

import android.util.Log
import it.melodia.data.AudioQuality
import it.melodia.data.Prefs
import it.melodia.innertube.YouTubeMusic
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Turns a YouTube video id into a direct, ad-free audio URL.
 * Uses NewPipeExtractor first and the InnerTube ANDROID_VR client as a fallback.
 */
class StreamResolver(private val yt: YouTubeMusic, private val prefs: Prefs) {

    private data class Cached(val url: String, val expiresAt: Long)

    private val cache = ConcurrentHashMap<String, Cached>()

    fun invalidate(videoId: String) {
        cache.remove(videoId)
    }

    /** Blocking; call on a background thread. */
    @Throws(IOException::class)
    fun resolve(videoId: String): String {
        cache[videoId]?.let { if (it.expiresAt > System.currentTimeMillis()) return it.url }
        val lowQuality = prefs.quality.value == AudioQuality.LOW
        val errors = mutableListOf<Throwable>()

        val url = runCatching { fromNewPipe(videoId, lowQuality) }.onFailure { errors += it }.getOrNull()
            ?: runCatching { fromInnertube(videoId, lowQuality) }.onFailure { errors += it }.getOrNull()

        if (url == null) {
            errors.forEach { Log.w(TAG, "resolve $videoId failed", it) }
            val reason = errors.firstOrNull()?.message ?: "sconosciuto"
            throw IOException("Impossibile riprodurre il brano: $reason", errors.firstOrNull())
        }
        cache[videoId] = Cached(url, System.currentTimeMillis() + URL_TTL_MS)
        return url
    }

    private fun fromNewPipe(videoId: String, lowQuality: Boolean): String {
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        val streams = info.audioStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.isNotBlank() }
            .filter { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
            .ifEmpty { info.audioStreams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP } }
        val sorted = streams.sortedBy { it.averageBitrate }
        val chosen = (if (lowQuality) sorted.firstOrNull() else sorted.lastOrNull())
            ?: throw IOException("Nessun flusso audio trovato")
        return chosen.content
    }

    private fun fromInnertube(videoId: String, lowQuality: Boolean): String {
        val streams = yt.audioStreams(videoId)
        val chosen = (if (lowQuality) streams.lastOrNull() else streams.firstOrNull())
            ?: throw IOException("Nessun flusso audio trovato")
        return chosen.url
    }

    companion object {
        private const val TAG = "StreamResolver"
        private const val URL_TTL_MS = 3 * 60 * 60 * 1000L
    }
}
