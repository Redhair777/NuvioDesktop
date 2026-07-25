package com.nuvio.app.features.player

import com.nuvio.app.features.redtrack.RedTrackScrobbleRepository
import com.nuvio.app.features.tmdb.TmdbService
import com.nuvio.app.features.trakt.TraktScrobbleRepository
import com.nuvio.app.features.watchprogress.WatchProgressClock
import com.nuvio.app.features.watchprogress.WatchProgressPlaybackSession
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import com.nuvio.app.features.watchprogress.buildPlaybackVideoId
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val PlayerScreenRuntime.activePlaybackIdentity: String
    get() = activeTorrentInfoHash
        ?.let { hash -> "torrent:$hash:${activeTorrentFileIdx ?: -1}" }
        ?: activeSourceUrl

internal val PlayerScreenRuntime.playbackSession: WatchProgressPlaybackSession
    get() = WatchProgressPlaybackSession(
        profileId = profileId,
        contentType = contentType ?: parentMetaType,
        parentMetaId = parentMetaId,
        parentMetaType = parentMetaType,
        videoId = activeVideoId?.takeIf { it.isNotBlank() } ?: buildPlaybackVideoId(
            parentMetaId = parentMetaId,
            seasonNumber = activeSeasonNumber,
            episodeNumber = activeEpisodeNumber,
            fallbackVideoId = activeVideoId,
        ),
        title = title,
        logo = logo,
        poster = poster,
        background = background,
        seasonNumber = activeSeasonNumber,
        episodeNumber = activeEpisodeNumber,
        episodeTitle = activeEpisodeTitle,
        episodeThumbnail = activeEpisodeThumbnail,
        providerName = activeProviderName,
        providerAddonId = activeProviderAddonId,
        lastStreamTitle = activeStreamTitle,
        lastStreamSubtitle = activeStreamSubtitle,
        pauseDescription = pauseDescription,
        lastSourceUrl = activeSourceUrl,
    )

internal fun PlayerScreenRuntime.resetIdentityStateIfNeeded() {
    val identity = activePlaybackIdentity
    if (lastResetPlaybackIdentity != identity) {
        lastResetPlaybackIdentity = identity
        shouldPlay = true
        initialLoadCompleted = false
        speedBoostRestoreSpeed = null
        isHoldToSpeedGestureActive = false
        initialSeekApplied = activeInitialPositionMs <= 0L &&
            (activeInitialProgressFraction == null || activeInitialProgressFraction!! <= 0f)
        lastProgressPersistEpochMs = 0L
        previousIsPlaying = false
        pendingScrobbleStartAfterSeek = false
        pendingRedTrackScrobbleStartAfterSeek = false
        autoFetchedAddonSubtitlesForKey = null
        trackPreferenceRestoreApplied = false
        preferredAudioSelectionApplied = false
        preferredSubtitleSelectionApplied = false
    }

    val videoIdentity = "$identity:$activeVideoId:$activeSeasonNumber:$activeEpisodeNumber"
    if (lastResetVideoIdentity != videoIdentity) {
        lastResetVideoIdentity = videoIdentity
        hasRequestedScrobbleStartForCurrentItem = false
        scrobbleStartRequestGeneration = 0L
        pendingScrobbleStartAfterSeek = false
        hasSentCompletionScrobbleForCurrentItem = false
        currentTraktScrobbleItem = null
        hasRequestedRedTrackScrobbleStartForCurrentItem = false
        redTrackScrobbleStartRequestGeneration = 0L
        pendingRedTrackScrobbleStartAfterSeek = false
        hasSentCompletionRedTrackScrobbleForCurrentItem = false
        currentRedTrackScrobbleItem = null
    }
}

internal fun PlayerScreenRuntime.currentPlaybackProgressPercent(
    snapshot: PlayerPlaybackSnapshot = playbackSnapshot,
): Float {
    val duration = snapshot.durationMs.takeIf { it > 0L } ?: return 0f
    return ((snapshot.positionMs.toFloat() / duration.toFloat()) * 100f)
        .coerceIn(0f, 100f)
}

internal data class TraktScrobbleItemInputs(
    val contentType: String,
    val parentMetaId: String,
    val videoId: String?,
    val title: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val episodeTitle: String?,
)

internal fun PlayerScreenRuntime.snapshotTraktScrobbleItemInputs() = TraktScrobbleItemInputs(
    contentType = contentType ?: parentMetaType,
    parentMetaId = parentMetaId,
    videoId = activeVideoId,
    title = title,
    seasonNumber = activeSeasonNumber,
    episodeNumber = activeEpisodeNumber,
    episodeTitle = activeEpisodeTitle,
)

internal data class RedTrackScrobbleItemInputs(
    val contentType: String,
    val parentMetaId: String,
    val videoId: String?,
    val title: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val episodeTitle: String?,
)

internal fun PlayerScreenRuntime.snapshotRedTrackScrobbleItemInputs() = RedTrackScrobbleItemInputs(
    contentType = contentType ?: parentMetaType,
    parentMetaId = parentMetaId,
    videoId = activeVideoId,
    title = title,
    seasonNumber = activeSeasonNumber,
    episodeNumber = activeEpisodeNumber,
    episodeTitle = activeEpisodeTitle,
)

private suspend fun RedTrackScrobbleItemInputs.buildRedTrackItem(): com.nuvio.app.features.redtrack.RedTrackScrobbleItem? =
    RedTrackScrobbleRepository.buildItem(
        contentType = contentType,
        parentMetaId = parentMetaId,
        videoId = videoId,
        title = title,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        episodeTitle = episodeTitle,
    )

internal suspend fun PlayerScreenRuntime.currentRedTrackScrobbleItem() =
    snapshotRedTrackScrobbleItemInputs().buildRedTrackItem()

private suspend fun TraktScrobbleItemInputs.buildItem() =
    TraktScrobbleRepository.buildItem(
        contentType = contentType,
        parentMetaId = parentMetaId,
        videoId = videoId,
        title = title,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        episodeTitle = episodeTitle,
    )

internal suspend fun PlayerScreenRuntime.currentTraktScrobbleItem() =
    snapshotTraktScrobbleItemInputs().buildItem()

internal fun PlayerScreenRuntime.emitTraktScrobbleStart() {
    if (hasRequestedScrobbleStartForCurrentItem) return
    hasRequestedScrobbleStartForCurrentItem = true
    val requestGeneration = scrobbleStartRequestGeneration + 1L
    scrobbleStartRequestGeneration = requestGeneration

    scope.launch {
        val item = currentTraktScrobbleItem()
        if (item == null) {
            hasRequestedScrobbleStartForCurrentItem = false
            return@launch
        }
        if (requestGeneration != scrobbleStartRequestGeneration || !hasRequestedScrobbleStartForCurrentItem) {
            return@launch
        }
        currentTraktScrobbleItem = item
        TraktScrobbleRepository.scrobbleStart(
            profileId = profileId,
            item = item,
            progressPercent = currentPlaybackProgressPercent(),
        )
    }
}

internal fun PlayerScreenRuntime.emitTraktScrobbleStop(progressPercent: Float? = null) {
    val provided = progressPercent
    if (!hasRequestedScrobbleStartForCurrentItem && (provided ?: 0f) < 80f) return

    val percent = provided ?: currentPlaybackProgressPercent()
    val itemSnapshot = currentTraktScrobbleItem
    val inputsSnapshot = snapshotTraktScrobbleItemInputs()
    scope.launch(NonCancellable) {
        val item = itemSnapshot ?: inputsSnapshot.buildItem() ?: return@launch
        TraktScrobbleRepository.scrobbleStop(
            profileId = profileId,
            item = item,
            progressPercent = percent,
        )
    }
    currentTraktScrobbleItem = null
    hasRequestedScrobbleStartForCurrentItem = false
    scrobbleStartRequestGeneration += 1L
}

internal fun PlayerScreenRuntime.emitRedTrackScrobbleStart() {
    if (hasRequestedRedTrackScrobbleStartForCurrentItem) return
    hasRequestedRedTrackScrobbleStartForCurrentItem = true
    val requestGeneration = redTrackScrobbleStartRequestGeneration + 1L
    redTrackScrobbleStartRequestGeneration = requestGeneration

    scope.launch {
        val item = currentRedTrackScrobbleItem()
        if (item == null) {
            hasRequestedRedTrackScrobbleStartForCurrentItem = false
            return@launch
        }
        if (requestGeneration != redTrackScrobbleStartRequestGeneration || !hasRequestedRedTrackScrobbleStartForCurrentItem) {
            return@launch
        }
        currentRedTrackScrobbleItem = item
        RedTrackScrobbleRepository.scrobbleStart(
            profileId = profileId,
            item = item,
            progressPercent = currentPlaybackProgressPercent(),
        )
    }
}

internal fun PlayerScreenRuntime.emitRedTrackScrobbleStop(progressPercent: Float? = null) {
    val provided = progressPercent
    if (!hasRequestedRedTrackScrobbleStartForCurrentItem && (provided ?: 0f) < 80f) return

    val percent = provided ?: currentPlaybackProgressPercent()
    val itemSnapshot = currentRedTrackScrobbleItem
    val inputsSnapshot = snapshotRedTrackScrobbleItemInputs()
    scope.launch(NonCancellable) {
        val item = itemSnapshot ?: inputsSnapshot.buildRedTrackItem() ?: return@launch
        RedTrackScrobbleRepository.scrobbleStop(
            profileId = profileId,
            item = item,
            progressPercent = percent,
        )
    }
    currentRedTrackScrobbleItem = null
    hasRequestedRedTrackScrobbleStartForCurrentItem = false
    redTrackScrobbleStartRequestGeneration += 1L
}

internal fun PlayerScreenRuntime.emitStopRedTrackScrobbleForCurrentProgress() {
    val progressPercent = currentPlaybackProgressPercent()
    if (progressPercent >= 1f && progressPercent < 80f) {
        emitRedTrackScrobbleStop(progressPercent)
        return
    }

    if (progressPercent >= 80f && !hasSentCompletionRedTrackScrobbleForCurrentItem) {
        hasSentCompletionRedTrackScrobbleForCurrentItem = true
        emitRedTrackScrobbleStop(progressPercent)
    }
}

internal fun PlayerScreenRuntime.emitStopScrobbleForCurrentProgress() {
    val progressPercent = currentPlaybackProgressPercent()
    if (progressPercent >= 1f && progressPercent < 80f) {
        emitTraktScrobbleStop(progressPercent)
        return
    }

    if (progressPercent >= 80f && !hasSentCompletionScrobbleForCurrentItem) {
        hasSentCompletionScrobbleForCurrentItem = true
        emitTraktScrobbleStop(progressPercent)
    }
}

internal fun PlayerScreenRuntime.tryShowParentalGuide() {
    if (!playerSettingsUiState.showParentalGuide) return
    if (!parentalGuideHasShown && parentalWarnings.isNotEmpty() && !playbackStartedForParentalGuide) {
        playbackStartedForParentalGuide = true
        controlsVisible = true
        showParentalGuide = true
        parentalGuideHasShown = true
    }
}

internal suspend fun PlayerScreenRuntime.resolveParentalGuideImdbId(): String? {
    val candidates = listOf(parentMetaId, activeVideoId)
    candidates.firstNotNullOfOrNull(::extractParentalGuideImdbId)?.let { return it }
    val tmdbId = candidates.firstNotNullOfOrNull(::extractParentalGuideTmdbId) ?: return null
    return TmdbService.tmdbToImdb(
        tmdbId = tmdbId,
        mediaType = contentType ?: parentMetaType,
    )
}

internal fun PlayerScreenRuntime.flushWatchProgress() {
    emitStopScrobbleForCurrentProgress()
    emitStopRedTrackScrobbleForCurrentProgress()
    WatchProgressRepository.flushPlaybackProgress(
        session = playbackSession,
        snapshot = playbackSnapshot,
    )
}

internal fun PlayerScreenRuntime.scheduleProgressSyncAfterSeek() {
    val shouldRestartScrobbleAfterSeek = shouldPlay || playbackSnapshot.isPlaying
    seekProgressSyncJob?.cancel()
    seekProgressSyncJob = scope.launch {
        delay(PlayerSeekProgressSyncDebounceMs)
        WatchProgressRepository.upsertPlaybackProgress(
            session = playbackSession,
            snapshot = playbackSnapshot,
        )

        val progressPercent = currentPlaybackProgressPercent()
        if (progressPercent >= 1f && progressPercent < 80f) {
            emitTraktScrobbleStop(progressPercent)
            emitRedTrackScrobbleStop(progressPercent)
            val shouldRestartScrobbleNow = shouldRestartScrobbleAfterSeek && shouldPlay
            if (shouldRestartScrobbleNow && playbackSnapshot.isPlaying) {
                pendingScrobbleStartAfterSeek = false
                pendingRedTrackScrobbleStartAfterSeek = false
                emitTraktScrobbleStart()
                emitRedTrackScrobbleStart()
            } else if (shouldRestartScrobbleNow) {
                pendingScrobbleStartAfterSeek = true
                pendingRedTrackScrobbleStartAfterSeek = true
            }
        }
    }
}

internal fun PlayerScreenRuntime.persistPlaybackProgressTick() {
    val now = WatchProgressClock.nowEpochMs()
    if (now - lastProgressPersistEpochMs < PlaybackProgressPersistIntervalMs) return
    lastProgressPersistEpochMs = now
    WatchProgressRepository.upsertPlaybackProgress(
        session = playbackSession,
        snapshot = playbackSnapshot,
        syncRemote = false,
    )
}
