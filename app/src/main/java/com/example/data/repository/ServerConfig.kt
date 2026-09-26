package com.example.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ServerConfig {
    enum class ServerType(val id: String, val displayName: String, val subtitle: String) {
        MERA_SERVER(
            id = "mera_server",
            displayName = "Primary Server (Admin Uploads)",
            subtitle = "Direct HD uploads from your admin panel and cloud database"
        ),
        MOVIEBOX(
            id = "moviebox",
            displayName = "MovieBox Server (ElitePlex)",
            subtitle = "MovieBox catalog with HD movies, web series, and international titles"
        ),
        CUSTOM(
            id = "custom",
            displayName = "Custom Server",
            subtitle = "Connect to your custom API endpoint URL"
        );

        companion object {
            fun fromId(id: String?): ServerType {
                if (id == null) return MERA_SERVER
                return entries.firstOrNull {
                    it.id.equals(id, ignoreCase = true) ||
                    it.name.equals(id, ignoreCase = true) ||
                    (id.contains("elite", ignoreCase = true) && it == MOVIEBOX) ||
                    (id.contains("firebase", ignoreCase = true) && it == MERA_SERVER)
                } ?: MERA_SERVER
            }
        }
    }

    const val DEFAULT_MOVIEBOX_URL = "https://eliteplex-api.vercel.app"

    private val _currentServer = MutableStateFlow(ServerType.MERA_SERVER)
    val currentServer: StateFlow<ServerType> = _currentServer.asStateFlow()

    private val _customBaseUrl = MutableStateFlow(DEFAULT_MOVIEBOX_URL)
    val customBaseUrl: StateFlow<String> = _customBaseUrl.asStateFlow()

    private val _movieBoxApiKey = MutableStateFlow("")
    val movieBoxApiKey: StateFlow<String> = _movieBoxApiKey.asStateFlow()

    private val _hasChosenServer = MutableStateFlow(false)
    val hasChosenServer: StateFlow<Boolean> = _hasChosenServer.asStateFlow()

    fun init(prefs: AppPrefs) {
        val savedServerId = prefs.selectedServer
        val hasChosen = prefs.hasChosenServer
        val savedUrl = prefs.customServerUrl
        val savedApiKey = prefs.movieBoxApiKey

        _hasChosenServer.value = hasChosen
        if (!savedUrl.isNullOrBlank()) {
            _customBaseUrl.value = savedUrl
        }
        if (!savedApiKey.isNullOrBlank()) {
            _movieBoxApiKey.value = savedApiKey
        }
        if (!savedServerId.isNullOrBlank()) {
            _currentServer.value = ServerType.fromId(savedServerId)
        }
    }

    fun setServer(
        server: ServerType,
        customUrl: String? = null,
        apiKey: String? = null,
        prefs: AppPrefs? = null
    ) {
        _currentServer.value = server
        _hasChosenServer.value = true
        if (!customUrl.isNullOrBlank()) {
            _customBaseUrl.value = customUrl.trim().removeSuffix("/")
        }
        if (apiKey != null) {
            _movieBoxApiKey.value = apiKey.trim()
        }
        prefs?.let {
            it.selectedServer = server.id
            it.hasChosenServer = true
            if (!customUrl.isNullOrBlank()) {
                it.customServerUrl = customUrl.trim().removeSuffix("/")
            }
            if (apiKey != null) {
                it.movieBoxApiKey = apiKey.trim()
            }
        }
    }

    fun getActiveApiUrl(): String {
        return when (_currentServer.value) {
            ServerType.CUSTOM -> _customBaseUrl.value.ifBlank { DEFAULT_MOVIEBOX_URL }
            ServerType.MOVIEBOX -> DEFAULT_MOVIEBOX_URL
            ServerType.MERA_SERVER -> DEFAULT_MOVIEBOX_URL
        }
    }

    fun isMovieBox(): Boolean = _currentServer.value == ServerType.MOVIEBOX || _currentServer.value == ServerType.CUSTOM
    fun isMeraServer(): Boolean = _currentServer.value == ServerType.MERA_SERVER
    fun isElitePlex(): Boolean = isMovieBox()
}

