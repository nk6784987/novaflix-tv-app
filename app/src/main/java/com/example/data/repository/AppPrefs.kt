package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Real, persisted user settings. Every value here is actually used somewhere:
 *  - autoPlayNext      -> PlayerViewModel (next episode starts by itself)
 *  - preferredQuality  -> PlayerViewModel (which admin source is picked first)
 *  - defaultSpeed      -> PlayerScreen   (start speed)
 *  - subtitleScale     -> PlayerScreen   (subtitle text size)
 *  - lastAudioLang / lastSubtitleLang -> PlayerScreen (remembers your dub / subtitles)
 *  - totalWatchMs      -> Profile stats (real watched time)
 */
class AppPrefs private constructor(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("novaflix_prefs", Context.MODE_PRIVATE)

    enum class QualityMode(val label: String) {
        ADMIN_DEFAULT("Default"),
        HIGHEST("Highest"),
        DATA_SAVER("Data saver")
    }

    data class State(
        val autoPlayNext: Boolean,
        val preferredQuality: QualityMode,
        val defaultSpeed: Float,
        val subtitleScale: Float,
        val totalWatchMs: Long
    )

    private val _state = MutableStateFlow(read())
    val state: StateFlow<State> = _state.asStateFlow()

    private fun read() = State(
        autoPlayNext = sp.getBoolean("auto_play_next", true),
        preferredQuality = runCatching { QualityMode.valueOf(sp.getString("quality_mode", "ADMIN_DEFAULT")!!) }
            .getOrDefault(QualityMode.ADMIN_DEFAULT),
        defaultSpeed = sp.getFloat("default_speed", 1f),
        subtitleScale = sp.getFloat("subtitle_scale", 1f),
        totalWatchMs = sp.getLong("total_watch_ms", 0L)
    )

    private fun refresh() { _state.value = read() }

    var autoPlayNext: Boolean
        get() = _state.value.autoPlayNext
        set(v) { sp.edit().putBoolean("auto_play_next", v).apply(); refresh() }

    var preferredQuality: QualityMode
        get() = _state.value.preferredQuality
        set(v) { sp.edit().putString("quality_mode", v.name).apply(); refresh() }

    var defaultSpeed: Float
        get() = _state.value.defaultSpeed
        set(v) { sp.edit().putFloat("default_speed", v).apply(); refresh() }

    var subtitleScale: Float
        get() = _state.value.subtitleScale
        set(v) { sp.edit().putFloat("subtitle_scale", v).apply(); refresh() }

    /** Remembered language names ("Hindi"); empty = none. */
    var lastAudioLang: String
        get() = sp.getString("last_audio_lang", "") ?: ""
        set(v) { sp.edit().putString("last_audio_lang", v).apply() }

    /** "" = subtitles were off, otherwise the language name. */
    var lastSubtitleLang: String
        get() = sp.getString("last_sub_lang", "") ?: ""
        set(v) { sp.edit().putString("last_sub_lang", v).apply() }

    var selectedServer: String?
        get() = sp.getString("selected_server", null)
        set(v) { sp.edit().putString("selected_server", v).apply() }

    var hasChosenServer: Boolean
        get() = sp.getBoolean("has_chosen_server", false)
        set(v) { sp.edit().putBoolean("has_chosen_server", v).apply() }

    var customServerUrl: String?
        get() = sp.getString("custom_server_url", "https://eliteplex-api.vercel.app")
        set(v) { sp.edit().putString("custom_server_url", v).apply() }

    var movieBoxApiKey: String?
        get() = sp.getString("moviebox_api_key", "")
        set(v) { sp.edit().putString("moviebox_api_key", v).apply() }

    /** Testing aid: lay the app out for TV + remote even on a phone (needs an app restart). */
    var forceTvMode: Boolean
        get() = sp.getBoolean("force_tv_mode", false)
        set(v) { sp.edit().putBoolean("force_tv_mode", v).apply() }

    fun setTotalWatchMs(v: Long) { sp.edit().putLong("total_watch_ms", v.coerceAtLeast(0L)).apply(); refresh() }

    fun addWatchMs(delta: Long) { if (delta > 0) setTotalWatchMs(sp.getLong("total_watch_ms", 0L) + delta) }

    fun resetUserData() {
        sp.edit().putLong("total_watch_ms", 0L).putString("last_audio_lang", "").putString("last_sub_lang", "").apply()
        refresh()
    }

    companion object {
        @Volatile private var instance: AppPrefs? = null
        fun get(context: Context): AppPrefs =
            instance ?: synchronized(this) { instance ?: AppPrefs(context).also { instance = it } }
    }
}
