package com.nuvio.app.features.redtrack

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object RedTrackSettingsStorage {
    private val store = DesktopStorage.store("nuvio_redtrack_settings")

    actual fun loadPayload(): String? =
        store.getString(ProfileScopedKey.of("redtrack_settings"))

    actual fun savePayload(payload: String) {
        store.putString(ProfileScopedKey.of("redtrack_settings"), payload)
    }
}
