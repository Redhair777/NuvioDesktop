package com.nuvio.app.features.redtrack

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object RedTrackSettingsRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _uiState = MutableStateFlow(RedTrackSettingsState())
    val uiState: StateFlow<RedTrackSettingsState> = _uiState.asStateFlow()

    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        loadFromDisk()
    }

    fun onProfileChanged() {
        loadFromDisk()
    }

    fun clearLocalState() {
        hasLoaded = false
        _uiState.value = RedTrackSettingsState()
    }

    fun updateBaseUrl(url: String) {
        ensureLoaded()
        val nextState = _uiState.value.copy(baseUrl = url.trim().takeIf { it.isNotBlank() })
        persist(nextState)
        _uiState.value = nextState
    }

    fun updateApiKey(key: String) {
        ensureLoaded()
        val nextState = _uiState.value.copy(apiKey = key.trim().takeIf { it.isNotBlank() })
        persist(nextState)
        _uiState.value = nextState
    }

    private fun loadFromDisk() {
        hasLoaded = true

        val payload = RedTrackSettingsStorage.loadPayload().orEmpty().trim()
        if (payload.isEmpty()) {
            _uiState.value = RedTrackSettingsState()
            return
        }

        val stored = runCatching {
            json.decodeFromString<StoredRedTrackSettings>(payload)
        }.getOrNull()

        _uiState.value = if (stored != null) {
            RedTrackSettingsState(
                baseUrl = stored.baseUrl,
                apiKey = stored.apiKey,
            )
        } else {
            RedTrackSettingsState()
        }
    }

    private fun persist(state: RedTrackSettingsState = _uiState.value) {
        RedTrackSettingsStorage.savePayload(
            json.encodeToString(
                StoredRedTrackSettings(
                    baseUrl = state.baseUrl,
                    apiKey = state.apiKey,
                ),
            ),
        )
    }
}

@Serializable
private data class StoredRedTrackSettings(
    val baseUrl: String? = null,
    val apiKey: String? = null,
)
