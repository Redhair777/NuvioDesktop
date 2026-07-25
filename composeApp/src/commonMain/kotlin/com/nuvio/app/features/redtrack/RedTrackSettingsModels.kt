package com.nuvio.app.features.redtrack

data class RedTrackSettingsState(
    val baseUrl: String? = null,
    val apiKey: String? = null,
) {
    val isConfigured: Boolean
        get() = !baseUrl.isNullOrBlank() && !apiKey.isNullOrBlank()
}
