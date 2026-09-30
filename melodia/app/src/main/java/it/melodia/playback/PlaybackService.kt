package it.melodia.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.withContext
import it.melodia.MainActivity
import it.melodia.MelodiaApp
import it.melodia.R
import java.io.File

/**
 * Foreground media service: keeps playing with the screen off and exposes lock-screen /
 * notification / Bluetooth controls through a MediaSession, plus a browse tree for Android Auto.
 */
class PlaybackService : MediaLibraryService() {

    private var session: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer
    private val retried = mutableSetOf<String>()
    private val scope: CoroutineScope = MainScope()
    private lateinit var tree: LibraryTree

    override fun onCreate() {
        super.onCreate()
        val app = application as MelodiaApp

        val httpFactory = OkHttpDataSource.Factory(app.http)
            .setUserAgent(NewPipeDownloader.USER_AGENT)
        val resolvingFactory = ResolvingDataSource.Factory(httpFactory) { spec: DataSpec ->
            val uri = spec.uri
            val videoId = uri.getQueryParameter("v")
            if (uri.host == "music.youtube.com" && videoId != null) {
                spec.withUri(Uri.parse(app.resolver.resolve(videoId)))
            } else spec
        }
        val cacheFactory = CacheDataSource.Factory()
            .setCache(AudioCache.get(this))
            .setUpstreamDataSourceFactory(resolvingFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) = handleError(error)

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) player.currentMediaItem?.mediaId?.let { retried.remove(it) }
            }
        })

        val activityIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        player.addListener(QueueExtender(player, app, scope))
        tree = LibraryTree(app)
        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(activityIntent)
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_notification)
            }
        )
    }

    /** Stream URLs expire: on the first error re-resolve the song, on the second skip it. */
    private fun handleError(error: PlaybackException) {
        val id = player.currentMediaItem?.mediaId ?: return
        Log.w("PlaybackService", "Errore su $id", error)
        val app = application as MelodiaApp
        app.resolver.invalidate(id)
        if (retried.add(id)) {
            player.prepare()
            player.play()
        } else if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.prepare()
            player.play()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    private fun <T> io(block: () -> T): ListenableFuture<T> = scope.future(Dispatchers.IO) { block() }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> = io { LibraryResult.ofItem(tree.root(), params) }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = io {
            try {
                LibraryResult.ofItemList(paginate(tree.children(parentId), page, pageSize), params)
            } catch (e: Exception) {
                Log.w("PlaybackService", "children $parentId", e)
                LibraryResult.ofError(LibraryResult.RESULT_ERROR_IO)
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = io {
            tree.item(mediaId)?.let { LibraryResult.ofItem(it, null) }
                ?: LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = scope.future {
            val count = withContext(Dispatchers.IO) { runCatching { tree.search(query).size }.getOrDefault(0) }
            session.notifySearchResultChanged(browser, query, count, params)
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = io {
            try {
                LibraryResult.ofItemList(paginate(tree.search(query), page, pageSize), params)
            } catch (e: Exception) {
                LibraryResult.ofError(LibraryResult.RESULT_ERROR_IO)
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = io {
            tree.resolve(mediaItems, 0, 0L).mediaItems.toMutableList()
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = io {
            tree.resolve(mediaItems, startIndex, startPositionMs)
        }
    }

    private fun paginate(list: List<MediaItem>, page: Int, pageSize: Int): List<MediaItem> {
        if (pageSize <= 0 || pageSize == Int.MAX_VALUE) return list
        val from = (page.toLong() * pageSize).coerceAtMost(list.size.toLong()).toInt()
        return list.subList(from, (from + pageSize).coerceAtMost(list.size))
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep playing when the app is swiped away, unless nothing is playing.
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}

/** Disk cache of recently played audio (also saves mobile data on repeat listens). */
object AudioCache {
    private const val MAX_BYTES = 1024L * 1024 * 1024
    @Volatile private var cache: SimpleCache? = null

    fun get(context: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(
            File(context.applicationContext.cacheDir, "audio"),
            LeastRecentlyUsedCacheEvictor(MAX_BYTES),
            StandaloneDatabaseProvider(context.applicationContext),
        ).also { cache = it }
    }

    fun sizeBytes(context: Context): Long = get(context).cacheSpace

    fun clear(context: Context) {
        val c = get(context)
        c.keys.toList().forEach { runCatching { c.removeResource(it) } }
    }
}
