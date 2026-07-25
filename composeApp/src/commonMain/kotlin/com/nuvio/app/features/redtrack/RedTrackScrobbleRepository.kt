package com.nuvio.app.features.redtrack

import co.touchlab.kermit.Logger

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

    suspend fun scrobbleStart(profileId: Int, item: RedTrackScrobbleItem, progressPercent: Float) {
        logScrobble("start", profileId, item, progressPercent)
    }

    suspend fun scrobbleStop(profileId: Int, item: RedTrackScrobbleItem, progressPercent: Float) {
        logScrobble("stop", profileId, item, progressPercent)
    }

    private fun logScrobble(action: String, profileId: Int, item: RedTrackScrobbleItem, progressPercent: Float) {
        val settings = RedTrackSettingsRepository.uiState.value
        if (!settings.isConfigured) {
            log.d { "red-track not configured, skipping scrobble" }
            return
        }

        val baseUrl = settings.baseUrl ?: "<missing>"
        val description = buildString {
            append("red-track scrobble $action: ")
            when (item) {
                is RedTrackScrobbleItem.Movie -> {
                    append("movie=")
                    append(item.title ?: "<no-title>")
                    append(", progress=${"%.1f".format(progressPercent)}%")
                }
                is RedTrackScrobbleItem.Episode -> {
                    append("episode=")
                    append(item.episodeTitle ?: "<no-title>")
                    append(", show=")
                    append(item.showTitle ?: "<no-title>")
                    append(", S${item.season}E${item.number}")
                    append(", progress=${"%.1f".format(progressPercent)}%")
                }
            }
            append(", targetBaseUrl=$baseUrl")
        }
        log.i { description }
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
