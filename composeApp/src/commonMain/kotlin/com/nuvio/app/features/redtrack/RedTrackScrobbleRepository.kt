package com.nuvio.app.features.redtrack

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpRequestRaw
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val SCRROBBLE_TIMEOUT_MS = 5_000L

internal sealed interface RedTrackScrobbleItem {
    val itemKey: String

    data class Movie(
        val title: String?,
        val year: Int?,
        val ids: RedTrackExternalIds,
    ) : RedTrackScrobbleItem {
        override val itemKey: String =
            "movie:${ids.imdb ?: ids.tmdb?.toString() ?: ids.id ?: title.orEmpty()}:${year ?: 0}"
    }

    data class Episode(
        val showTitle: String?,
        val showYear: Int?,
        val showIds: RedTrackExternalIds,
        val season: Int,
        val number: Int,
        val episodeTitle: String?,
    ) : RedTrackScrobbleItem {
        override val itemKey: String =
            "episode:${showIds.imdb ?: showIds.tmdb?.toString() ?: showIds.id ?: showTitle.orEmpty()}:$season:$number"
    }
}

data class RedTrackExternalIds(
    val id: String? = null,
    val imdb: String? = null,
    val tmdb: Int? = null,
)

internal object RedTrackScrobbleRepository {
    private val log = Logger.withTag("RedTrackScrobble")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    suspend fun scrobbleStart(profileId: Int, item: RedTrackScrobbleItem, progressPercent: Float) {
        sendScrobble(action = "start", item = item, progressPercent = progressPercent)
    }

    suspend fun scrobbleStop(profileId: Int, item: RedTrackScrobbleItem, progressPercent: Float) {
        sendScrobble(action = "stop", item = item, progressPercent = progressPercent)
    }

    private suspend fun sendScrobble(
        action: String,
        item: RedTrackScrobbleItem,
        progressPercent: Float,
    ) {
        val settings = RedTrackSettingsRepository.uiState.value
        if (!settings.isConfigured) {
            log.d { "red-track not configured, skipping scrobble" }
            return
        }

        val baseUrl = settings.baseUrl!!.trimEnd('/')
        val apiKey = settings.apiKey!!
        val url = "$baseUrl/api/v1/scrobble/$action"
        val clampedProgress = progressPercent.coerceIn(0f, 100f)
        val requestBody = json.encodeToString(buildRequestBody(item, clampedProgress))
        val headers = mapOf(
            "Authorization" to "Bearer $apiKey",
            "Content-Type" to "application/json",
        )

        val result = runCatching {
            withTimeout(SCRROBBLE_TIMEOUT_MS) {
                httpRequestRaw(
                    method = "POST",
                    url = url,
                    body = requestBody,
                    headers = headers,
                )
            }
        }

        val response = result.onFailure { error ->
            if (error is CancellationException) throw error
            log.w { "red-track scrobble $action failed: ${error.message ?: error.javaClass.simpleName}" }
        }.getOrNull() ?: return

        if (response.status in 200..299) {
            val title = when (item) {
                is RedTrackScrobbleItem.Movie -> item.title ?: "unknown"
                is RedTrackScrobbleItem.Episode -> item.episodeTitle ?: item.showTitle ?: "unknown"
            }
            log.i { "red-track scrobble $action confirmed: HTTP ${response.status} ($title)" }
        } else if (response.status == 422) {
            log.w { "red-track scrobble $action returned 422 (unresolved imdb_id)" }
        } else {
            log.w { "red-track scrobble $action failed: HTTP ${response.status} ${response.statusText}" }
        }
    }

    @Serializable
    private data class ScrobbleRequestBody(
        @SerialName("imdb_id") val imdbId: String? = null,
        @SerialName("media_type") val mediaType: String,
        @SerialName("season_number") val seasonNumber: Int? = null,
        @SerialName("episode_number") val episodeNumber: Int? = null,
        @SerialName("progress_percent") val progressPercent: Float,
    )

    private fun buildRequestBody(
        item: RedTrackScrobbleItem,
        clampedProgress: Float,
    ): ScrobbleRequestBody = when (item) {
        is RedTrackScrobbleItem.Movie -> ScrobbleRequestBody(
            imdbId = item.ids.imdb,
            mediaType = "movie",
            progressPercent = clampedProgress,
        )
        is RedTrackScrobbleItem.Episode -> ScrobbleRequestBody(
            imdbId = item.showIds.imdb,
            mediaType = "tv",
            seasonNumber = item.season,
            episodeNumber = item.number,
            progressPercent = clampedProgress,
        )
    }

    suspend fun buildItem(
        contentType: String,
        parentMetaId: String,
        videoId: String?,
        title: String?,
        seasonNumber: Int?,
        episodeNumber: Int?,
        episodeTitle: String?,
    ): RedTrackScrobbleItem? {
        val normalizedType = contentType.trim().lowercase()
        val ids = parseRedTrackContentIds(parentMetaId, videoId)

        return if (
            normalizedType in listOf("series", "tv", "show", "tvshow") &&
            seasonNumber != null &&
            episodeNumber != null
        ) {
            RedTrackScrobbleItem.Episode(
                showTitle = title,
                showYear = null,
                showIds = ids,
                season = seasonNumber,
                number = episodeNumber,
                episodeTitle = episodeTitle,
            )
        } else {
            RedTrackScrobbleItem.Movie(
                title = title,
                year = null,
                ids = ids,
            )
        }
    }

    private fun parseRedTrackContentIds(parentMetaId: String, videoId: String?): RedTrackExternalIds {
        val candidates = listOfNotNull(parentMetaId, videoId)
        for (candidate in candidates) {
            val raw = candidate.trim()
            if (raw.startsWith("tt")) {
                return RedTrackExternalIds(imdb = raw.substringBefore(':'))
            }
            if (raw.startsWith("tmdb:", ignoreCase = true)) {
                return RedTrackExternalIds(tmdb = raw.substringAfter(':').toIntOrNull())
            }
            if (raw.startsWith("trakt:", ignoreCase = true)) {
                return RedTrackExternalIds(id = raw.substringAfter(':'))
            }
        }
        return RedTrackExternalIds(id = parentMetaId)
    }
}
