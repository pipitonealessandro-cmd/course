package it.melodia.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AudioQuality(val label: String) {
    HIGH("Alta"),
    LOW("Risparmio dati"),
}

data class Account(val name: String, val email: String?, val photo: String?)

/** Small wrapper around SharedPreferences with observable state for the UI. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("melodia", Context.MODE_PRIVATE)

    var cookie: String?
        get() = sp.getString("cookie", null)
        set(v) = sp.edit().putString("cookie", v).apply()

    var visitorData: String?
        get() = sp.getString("visitorData", null)
        set(v) = sp.edit().putString("visitorData", v).apply()

    var dataSyncId: String?
        get() = sp.getString("dataSyncId", null)
        set(v) = sp.edit().putString("dataSyncId", v).apply()

    private val _account = MutableStateFlow(readAccount())
    val account: StateFlow<Account?> = _account.asStateFlow()

    private fun readAccount(): Account? {
        val name = sp.getString("accountName", null) ?: return null
        return Account(name, sp.getString("accountEmail", null), sp.getString("accountPhoto", null))
    }

    fun setAccount(a: Account?) {
        sp.edit()
            .putString("accountName", a?.name)
            .putString("accountEmail", a?.email)
            .putString("accountPhoto", a?.photo)
            .apply()
        _account.value = a
    }

    private val _quality = MutableStateFlow(
        runCatching { AudioQuality.valueOf(sp.getString("quality", null) ?: "HIGH") }.getOrDefault(AudioQuality.HIGH)
    )
    val quality: StateFlow<AudioQuality> = _quality.asStateFlow()

    fun setQuality(q: AudioQuality) {
        sp.edit().putString("quality", q.name).apply()
        _quality.value = q
    }

    private val _autoRadio = MutableStateFlow(sp.getBoolean("autoRadio", true))
    /** When a single song is played, keep going with similar songs (like Spotify's autoplay). */
    val autoRadio: StateFlow<Boolean> = _autoRadio.asStateFlow()

    fun setAutoRadio(v: Boolean) {
        sp.edit().putBoolean("autoRadio", v).apply()
        _autoRadio.value = v
    }

    var recentSearches: List<String>
        get() = sp.getString("recentSearches", "")!!.split('\n').filter { it.isNotBlank() }
        set(v) = sp.edit().putString("recentSearches", v.take(15).joinToString("\n")).apply()
}
