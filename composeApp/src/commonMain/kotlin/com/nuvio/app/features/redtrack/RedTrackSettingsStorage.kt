package com.nuvio.app.features.redtrack

internal expect object RedTrackSettingsStorage {
    fun loadPayload(): String?
    fun savePayload(payload: String)
}
