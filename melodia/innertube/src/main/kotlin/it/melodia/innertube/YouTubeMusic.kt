package it.melodia.innertube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Minimal, blocking client for the YouTube Music internal API ("InnerTube", WEB_REMIX client).
 * Call it from a background thread.
 */
class YouTubeMusic(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
    /** Normally music.youtube.com; youtubei.googleapis.com also works for anonymous calls. */
    var baseUrl: String = DEFAULT_BASE_URL,
) {
    /** Full cookie header copied from a logged-in music.youtube.com session. */
    @Volatile var cookie: String? = null
    @Volatile var visitorData: String? = null
    /** DATASYNC_ID of the selected account (needed for brand accounts). */
    @Volatile var dataSyncId: String? = null
    @Volatile var hl: String = "it"
    @Volatile var gl: String = "IT"

    val isLoggedIn: Boolean get() = sapisid() != null

    private val json = Json { ignoreUnknownKeys = true }

    // ------------------------------------------------------------------ transport

    private fun sapisid(): String? {
        val c = cookie ?: return null
        val map = c.split(";").mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i).trim() to it.substring(i + 1).trim()
        }.toMap()
        return map["SAPISID"] ?: map["__Secure-3PAPISID"]
    }

    private fun authorization(): String? {
        val sid = sapisid() ?: return null
        val ts = System.currentTimeMillis() / 1000
        val digest = MessageDigest.getInstance("SHA-1").digest("$ts $sid $ORIGIN".toByteArray())
        val hex = digest.joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${ts}_$hex"
    }

    private fun context(): JsonObject = buildJsonObject {
        putJsonObject("client") {
            put("clientName", "WEB_REMIX")
            put("clientVersion", CLIENT_VERSION)
            put("hl", hl)
            put("gl", gl)
            visitorData?.let { put("visitorData", it) }
        }
        // DATASYNC_ID is "<userId>||" for personal accounts and "<pageId>||<userId>" for brand accounts.
        val parts = dataSyncId?.split("||")
        if (isLoggedIn && parts != null && parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
            putJsonObject("user") { put("onBehalfOfUser", parts[0]) }
        }
    }

    internal fun post(endpoint: String, body: JsonObjectBuilder.() -> Unit): JsonElement {
        val payload = buildJsonObject {
            put("context", context())
            body()
        }
        val builder = Request.Builder()
            .url("$baseUrl$endpoint?prettyPrint=false")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .header("Content-Type", "application/json")
            .header("Origin", ORIGIN)
            .header("Referer", "$ORIGIN/")
            .header("User-Agent", USER_AGENT)
            .header("X-YouTube-Client-Name", "67")
            .header("X-YouTube-Client-Version", CLIENT_VERSION)
            .header("Accept-Language", "$hl-$gl,$hl;q=0.9")
        visitorData?.let { builder.header("X-Goog-Visitor-Id", it) }
        cookie?.let { c ->
            builder.header("Cookie", c)
            authorization()?.let {
                builder.header("Authorization", it)
                builder.header("X-Goog-AuthUser", "0")
            }
        }
        http.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw InnertubeException("HTTP ${resp.code} su $endpoint: ${text.take(300)}")
            }
            val parsed = try {
                json.parseToJsonElement(text)
            } catch (e: Exception) {
                throw InnertubeException("Risposta non valida da $endpoint", e)
            }
            parsed.at("responseContext", "visitorData").str?.let { if (visitorData == null) visitorData = it }
            return parsed
        }
    }

    // ------------------------------------------------------------------ browse

    fun home(): HomePage {
        val r = post("browse") { put("browseId", "FEmusic_home") }
        return HomePage(Parser.sections(r.at("contents")), Parser.continuation(r.at("contents")))
    }

    fun homeContinuation(token: String): HomePage {
        val r = post("browse") { put("continuation", token) }
        val root = r.at("continuationContents") ?: r.at("onResponseReceivedActions")
        return HomePage(Parser.sections(root), Parser.continuation(root))
    }

    fun search(query: String, filter: SearchFilter = SearchFilter.ALL): SearchPage {
        val r = post("search") {
            put("query", query)
            filter.params?.let { put("params", it) }
        }
        return SearchPage(Parser.sections(r.at("contents")), Parser.continuation(r.at("contents")))
    }

    fun searchContinuation(token: String): SearchPage {
        val r = post("search") { put("continuation", token) }
        val root = r.at("continuationContents") ?: r.at("onResponseReceivedCommands")
        return SearchPage(Parser.sections(root), Parser.continuation(root))
    }

    fun searchSuggestions(input: String): List<String> {
        val r = post("music/get_search_suggestions") { put("input", input) }
        return r.findAll("searchSuggestionRenderer").mapNotNull { it["suggestion"].text() }.distinct()
    }

    fun artist(browseId: String): ArtistPage = Parser.artistPage(post("browse") { put("browseId", browseId) })

    /** Albums (MPREb…), playlists (VL…) or plain playlist ids. */
    fun playlist(id: String): PlaylistPage {
        val browseId = when {
            id.startsWith("MPREb") || id.startsWith("VL") || id.startsWith("FE") || id.startsWith("MPSP") -> id
            else -> "VL$id"
        }
        return Parser.playlistPage(post("browse") { put("browseId", browseId) }, browseId)
    }

    fun playlistContinuation(token: String): ContinuationPage<SongItem> {
        val r = post("browse") { put("continuation", token) }
        val root = r.at("continuationContents") ?: r.at("onResponseReceivedActions")
        return ContinuationPage(Parser.allItems(root).filterIsInstance<SongItem>(), Parser.continuation(root))
    }

    /** Generic browse page, used for "more" buttons (e.g. an artist's full album list). */
    fun browseSections(browseId: String, params: String? = null): Pair<String?, List<Section>> {
        val r = post("browse") {
            put("browseId", browseId)
            params?.let { put("params", it) }
        }
        val title = Parser.header(r)?.get("title").text()
        return title to Parser.sections(r.at("contents"))
    }

    // ------------------------------------------------------------------ queue / radio

    /**
     * Returns the "up next" queue. With [playlistId] = "RDAMVM" + videoId you get an endless radio
     * based on the song.
     */
    fun next(videoId: String?, playlistId: String? = null, params: String? = null, index: Int? = null): QueuePage {
        val r = post("next") {
            videoId?.let { put("videoId", it) }
            playlistId?.let { put("playlistId", it) }
            params?.let { put("params", it) }
            index?.let { put("index", it) }
            put("isAudioOnly", true)
            put("enablePersistentPlaylistPanel", true)
            put("tunerSettingValue", "AUTOMIX_SETTING_NORMAL")
        }
        val panel = r.findFirst("playlistPanelRenderer")
        val songs = Parser.allItems(panel).filterIsInstance<SongItem>()
        return QueuePage(songs, Parser.continuation(panel), panel.at("playlistId").str ?: playlistId)
    }

    fun nextContinuation(token: String, playlistId: String?): QueuePage {
        val r = post("next") {
            put("continuation", token)
            playlistId?.let { put("playlistId", it) }
            put("isAudioOnly", true)
            put("enablePersistentPlaylistPanel", true)
        }
        val root = r.at("continuationContents") ?: r.at("contents")
        return QueuePage(Parser.allItems(root).filterIsInstance<SongItem>(), Parser.continuation(root), playlistId)
    }

    /** True if liked, false if not, null if unknown (e.g. logged out). */
    fun likeStatus(videoId: String): Boolean? {
        val r = post("next") {
            put("videoId", videoId)
            put("isAudioOnly", true)
        }
        return when (r.findFirst("likeButtonRenderer").at("likeStatus").str) {
            "LIKE" -> true
            "INDIFFERENT", "DISLIKE" -> false
            else -> null
        }
    }

    // ------------------------------------------------------------------ library (login required)

    fun libraryPlaylists(): List<PlaylistItem> = libraryItems("FEmusic_liked_playlists").filterIsInstance<PlaylistItem>()

    fun libraryAlbums(): List<AlbumItem> = libraryItems("FEmusic_liked_albums").filterIsInstance<AlbumItem>()

    fun libraryArtists(): List<ArtistItem> = libraryItems("FEmusic_library_corpus_track_artists").filterIsInstance<ArtistItem>()

    fun history(): List<SongItem> = libraryItems("FEmusic_history").filterIsInstance<SongItem>()

    private fun libraryItems(browseId: String, maxPages: Int = 10): List<YTItem> {
        requireLogin()
        val r = post("browse") { put("browseId", browseId) }
        val out = Parser.allItems(r.at("contents")).toMutableList()
        var token = Parser.continuation(r.at("contents"))
        var pages = 0
        while (token != null && pages++ < maxPages) {
            val c = post("browse") { put("continuation", token) }
            val root = c.at("continuationContents") ?: c.at("onResponseReceivedActions")
            out += Parser.allItems(root)
            token = Parser.continuation(root)
        }
        return out.distinctBy { it.key }
    }

    fun setLike(videoId: String, liked: Boolean) {
        requireLogin()
        post(if (liked) "like/like" else "like/removelike") {
            putJsonObject("target") { put("videoId", videoId) }
        }
    }

    /** Saves / removes an album or playlist from the library. */
    fun setPlaylistSaved(playlistId: String, saved: Boolean) {
        requireLogin()
        post(if (saved) "like/like" else "like/removelike") {
            putJsonObject("target") { put("playlistId", playlistId) }
        }
    }

    fun addToPlaylist(playlistId: String, videoId: String) {
        requireLogin()
        post("browse/edit_playlist") {
            put("playlistId", playlistId.removePrefix("VL"))
            putJsonArray("actions") {
                add(buildJsonObject {
                    put("action", "ACTION_ADD_VIDEO")
                    put("addedVideoId", videoId)
                    put("dedupeOption", "DEDUPE_OPTION_SKIP")
                })
            }
        }
    }

    fun removeFromPlaylist(playlistId: String, videoId: String, setVideoId: String) {
        requireLogin()
        post("browse/edit_playlist") {
            put("playlistId", playlistId.removePrefix("VL"))
            putJsonArray("actions") {
                add(buildJsonObject {
                    put("action", "ACTION_REMOVE_VIDEO")
                    put("removedVideoId", videoId)
                    put("setVideoId", setVideoId)
                })
            }
        }
    }

    /** Creates a private playlist and returns its id. */
    fun createPlaylist(title: String, videoIds: List<String> = emptyList()): String? {
        requireLogin()
        val r = post("playlist/create") {
            put("title", title)
            put("description", "")
            put("privacyStatus", "PRIVATE")
            if (videoIds.isNotEmpty()) putJsonArray("videoIds") { videoIds.forEach { add(it) } }
        }
        return r.at("playlistId").str
    }

    fun accountInfo(): AccountInfo? {
        if (!isLoggedIn) return null
        val r = post("account/account_menu") {}
        val h = r.findFirst("activeAccountHeaderRenderer") ?: return null
        val name = h.at("accountName").text() ?: return null
        return AccountInfo(
            name = name,
            email = h.at("email").text() ?: h.at("channelHandle").text(),
            photo = Parser.thumbnail(h.at("accountPhoto")),
        )
    }

    private fun requireLogin() {
        if (!isLoggedIn) throw InnertubeException("Accedi con il tuo account YouTube per usare questa funzione")
    }

    // ------------------------------------------------------------------ streams (fallback)

    /**
     * Audio streams from the ANDROID_VR client, whose URLs are not ciphered. The app uses
     * NewPipeExtractor first and this only as a fallback.
     */
    fun audioStreams(videoId: String): List<AudioStreamInfo> {
        val payload = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "ANDROID_VR")
                    put("clientVersion", VR_VERSION)
                    put("deviceMake", "Oculus")
                    put("deviceModel", "Quest 3")
                    put("androidSdkVersion", 32)
                    put("osName", "Android")
                    put("osVersion", "12L")
                    put("hl", hl)
                    put("gl", gl)
                    visitorData?.let { put("visitorData", it) }
                }
            }
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }
        val req = Request.Builder()
            .url("${VR_BASE_URL}player?prettyPrint=false")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .header("User-Agent", VR_USER_AGENT)
            .header("X-YouTube-Client-Name", "28")
            .header("X-YouTube-Client-Version", VR_VERSION)
            .apply { visitorData?.let { header("X-Goog-Visitor-Id", it) } }
            .build()
        val r = http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw InnertubeException("HTTP ${resp.code} (player)")
            json.parseToJsonElement(resp.body?.string().orEmpty())
        }
        val status = r.at("playabilityStatus", "status").str
        if (status != "OK") {
            throw InnertubeException(r.at("playabilityStatus", "reason").str ?: "Brano non riproducibile ($status)")
        }
        return r.at("streamingData", "adaptiveFormats").arr.orEmpty().mapNotNull { f ->
            val mime = f.at("mimeType").str ?: return@mapNotNull null
            val url = f.at("url").str ?: return@mapNotNull null
            if (!mime.startsWith("audio/")) return@mapNotNull null
            AudioStreamInfo(
                url = url,
                mimeType = mime,
                bitrate = f.at("bitrate").str?.toIntOrNull() ?: 0,
                contentLength = f.at("contentLength").str?.toLongOrNull(),
            )
        }.sortedByDescending { it.bitrate }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://music.youtube.com/youtubei/v1/"
        const val ORIGIN = "https://music.youtube.com"
        const val CLIENT_VERSION = "1.20260916.01.00"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36"
        private const val VR_BASE_URL = "https://youtubei.googleapis.com/youtubei/v1/"
        private const val VR_VERSION = "1.61.48"
        private const val VR_USER_AGENT =
            "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
        private val JSON_MEDIA = "application/json".toMediaType()

        const val LIKED_SONGS_ID = "VLLM"
    }
}
