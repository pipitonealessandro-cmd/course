package it.melodia

import android.app.Application
import it.melodia.data.Account
import it.melodia.data.Prefs
import it.melodia.innertube.YouTubeMusic
import it.melodia.playback.NewPipeDownloader
import it.melodia.playback.PlayerConnection
import it.melodia.playback.StreamResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.concurrent.TimeUnit

class MelodiaApp : Application() {

    lateinit var prefs: Prefs
        private set
    lateinit var yt: YouTubeMusic
        private set
    lateinit var resolver: StreamResolver
        private set
    lateinit var player: PlayerConnection
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        yt = YouTubeMusic(http).apply {
            cookie = prefs.cookie
            visitorData = prefs.visitorData
            dataSyncId = prefs.dataSyncId
        }
        NewPipe.init(NewPipeDownloader(http), Localization("it", "IT"), ContentCountry("IT"))
        resolver = StreamResolver(yt, prefs)
        player = PlayerConnection(this)
        refreshAccount()
    }

    fun login(cookie: String, visitorData: String?, dataSyncId: String?) {
        prefs.cookie = cookie
        prefs.visitorData = visitorData
        prefs.dataSyncId = dataSyncId
        yt.cookie = cookie
        if (!visitorData.isNullOrBlank()) yt.visitorData = visitorData
        yt.dataSyncId = dataSyncId
        refreshAccount()
    }

    fun logout() {
        prefs.cookie = null
        prefs.dataSyncId = null
        prefs.setAccount(null)
        yt.cookie = null
        yt.dataSyncId = null
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
    }

    private fun refreshAccount() {
        if (!yt.isLoggedIn) return
        scope.launch(Dispatchers.IO) {
            runCatching { yt.accountInfo() }.getOrNull()?.let {
                prefs.setAccount(Account(it.name, it.email, it.photo))
            } ?: run {
                if (prefs.account.value == null) prefs.setAccount(Account("Account YouTube", null, null))
            }
        }
    }

    companion object {
        lateinit var instance: MelodiaApp
            private set
    }
}
