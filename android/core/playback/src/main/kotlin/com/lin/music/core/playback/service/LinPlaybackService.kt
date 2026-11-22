package com.lin.music.core.playback.service

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.LoudnessEnhancer
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import okhttp3.OkHttpClient
import java.io.File
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.PriorityTaskManager
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.lin.music.core.data.database.CacheDao
import com.lin.music.core.data.database.PersistedQueueEntity
import com.lin.music.core.data.database.toEntity
import com.lin.music.core.data.datastore.PreferencesStore
import com.lin.music.core.data.repository.MusicRepository
import com.lin.music.core.innertube.parser.InnerTubeParsers
import com.lin.music.core.innertube.stream.StreamResolver
import com.lin.music.core.model.AmbientMode
import com.lin.music.core.model.RepeatMode
import com.lin.music.core.model.SkipSegment
import com.lin.music.core.model.Track
import com.lin.music.core.playback.cache.TrackPrecacher
import com.lin.music.core.playback.di.PlaybackModule
import com.lin.music.core.playback.di.StreamCacheDataSourceFactory
import com.lin.music.core.playback.download.DownloadController
import com.lin.music.core.playback.download.SmartDownloadManager
import com.lin.music.core.playback.queue.PlaybackStateHub
import com.lin.music.core.playback.timer.SleepTimerController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class LinPlaybackService : MediaLibraryService(), PlaybackStateHub.ServiceTimerBridge {

    @Inject @StreamCacheDataSourceFactory
    lateinit var cacheDataSourceFactory: DataSource.Factory

    @Inject lateinit var stateHub: PlaybackStateHub
    @Inject lateinit var preferencesStore: PreferencesStore
    @Inject lateinit var cacheDao: CacheDao
    @Inject lateinit var musicRepository: MusicRepository
    @Inject lateinit var streamResolver: StreamResolver
    @Inject lateinit var trackPrecacher: TrackPrecacher
    @Inject lateinit var priorityTaskManager: PriorityTaskManager
    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var downloadController: DownloadController
    @Inject lateinit var smartDownloadManager: SmartDownloadManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private var mediaSession: MediaLibrarySession? = null
    private lateinit var sleepTimer: SleepTimerController

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var positionTickerJob: Job? = null
    private var precacheKickoffJob: Job? = null
    private var qualifiedPlayJob: Job? = null
    private var persistQueueJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastNetworkHandle: Long? = null
    private var lastVpnActive: Boolean? = null
    private var readySinceMs: Long = 0L
    @Volatile private var healthyForPrecache: Boolean = false
    private var skipSegments: List<SkipSegment> = emptyList()
    private var lastAutoMixCheckVideoId: String? = null
    private var lastRetriedVideoId: String? = null
    private var lastRecordedPlayVideoId: String? = null
    private var lastRecordedPlayAtMs: Long = 0L
    private var qualifiedPlayTrack: Track? = null
    private var qualifiedPlayVideoId: String? = null
    private var qualifiedPlayRecorded: Boolean = false
    private var qualifiedPlayAccumulatedMs: Long = 0L
    private var qualifiedPlayFlushedMs: Long = 0L
    private var qualifiedPlaySegmentStartMs: Long = 0L
    private var sleepFadeMultiplier: Float = 1f
    private var normalizationGainMultiplier: Float = 1f

    private val json = Json { ignoreUnknownKeys = true }

    override fun onCreate() {
        super.onCreate()

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(TeeAudioProcessor(stateHub.reactiveAudioProcessor)))
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            }
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(25_000, 60_000, 350, 2_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        player = ExoPlayer.Builder(this, renderersFactory)
            .setLoadControl(loadControl)
            .setPriorityTaskManager(priorityTaskManager)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        sleepTimer = SleepTimerController(
            scope = serviceScope,
            onApplyFadeVolume = { mult ->
                sleepFadeMultiplier = mult
                applyEffectiveVolume()
            },
            onTimerFinishedPause = {
                player.pause()
            }
        )
        stateHub.serviceTimerBridge = this

        serviceScope.launch(Dispatchers.IO) {
            runCatching { File(cacheDir, "lin_media_cache").deleteRecursively() }
            runCatching { trackPrecacher.pruneLegacyUnkeyedStreamCache() }
            runCatching { streamResolver.warmUp() }
        }
        registerVpnAndNetworkObserver()

        serviceScope.launch {
            sleepTimer.state.collectLatest { stateHub.updateSleepTimer(it) }
        }

        serviceScope.launch {
            runCatching { preferencesStore.ensureNormalSpeedDefaultOnce() }
            preferencesStore.preferencesFlow.collectLatest { prefs ->
                stateHub.currentQuality = prefs.audioQuality
                stateHub.downloadQuality = prefs.downloadQuality
                stateHub.normalizeVolume = prefs.normalizeVolume
                stateHub.sponsorBlockEnabled = prefs.sponsorBlockEnabled
                stateHub.reactiveAudioProcessor.updateReactivity(prefs.ambientReactivity)
                downloadController.updateWifiOnlyRequirement(prefs.downloadWifiOnly)
                applyPreferences(
                    normalize = prefs.normalizeVolume,
                    skipSilence = prefs.skipSilence,
                    speed = prefs.playbackSpeed,
                    pitch = prefs.playbackPitch
                )
            }
        }

        serviceScope.launch {
            combine(
                stateHub.isUiForeground,
                preferencesStore.preferencesFlow.map { it.ambientMode }.distinctUntilChanged()
            ) { fg, mode -> fg && mode == AmbientMode.AUDIO_REACTIVE }
                .distinctUntilChanged()
                .collectLatest { enabled ->
                    stateHub.reactiveAudioProcessor.setEnabled(enabled)
                }
        }

        serviceScope.launch {
            stateHub.isUiForeground.collectLatest { fg ->
                if (fg) {
                    stateHub.updatePosition(player.currentPosition, player.duration)
                    if (player.isPlaying) {
                        ensurePositionTickerRunning()
                    }
                }
            }
        }

        setupPlayerListener()
        setupMediaSession()
        restorePersistedQueue()
    }

    private fun setupPlayerListener() {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    sleepTimer.onPlaybackResumed()
                    if (player.playbackState == Player.STATE_READY && readySinceMs == 0L) {
                        readySinceMs = android.os.SystemClock.elapsedRealtime()
                    }
                    evaluatePlaybackHealthForPrecache()
                    scheduleFastPrecacheCheck()
                    ensurePositionTickerRunning()
                    resumeQualifiedPlayTimer()
                } else {
                    precacheKickoffJob?.cancel()
                    pauseQualifiedPlayTimer()
                    healthyForPrecache = false
                    stopPositionTicker()
                    stateHub.reactiveAudioProcessor.resetEnergy()
                }
                stateHub.updatePlaybackState(
                    playing = isPlaying,
                    buffering = player.playbackState == Player.STATE_BUFFERING
                )
                persistQueueSnapshot()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                stateHub.updatePlaybackState(
                    playing = player.isPlaying,
                    buffering = playbackState == Player.STATE_BUFFERING
                )
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        precacheKickoffJob?.cancel()
                        readySinceMs = 0L
                        healthyForPrecache = false
                        trackPrecacher.onPlaybackLoadingOrBuffering(
                            stateHub.queueEngine.snapshot.currentTrack?.videoId
                        )
                    }
                    Player.STATE_IDLE, Player.STATE_ENDED -> {
                        precacheKickoffJob?.cancel()
                        pauseQualifiedPlayTimer()
                        readySinceMs = 0L
                        healthyForPrecache = false
                        stopPositionTicker()
                        trackPrecacher.onPlaybackLoadingOrBuffering(
                            stateHub.queueEngine.snapshot.currentTrack?.videoId
                        )
                    }
                    Player.STATE_READY -> {
                        if (readySinceMs == 0L) {
                            readySinceMs = android.os.SystemClock.elapsedRealtime()
                        }
                        stateHub.updatePosition(player.currentPosition, player.duration)
                        applyLoudnessForCurrentTrack()
                        evaluatePlaybackHealthForPrecache()
                        if (player.isPlaying) {
                            scheduleFastPrecacheCheck()
                            ensurePositionTickerRunning()
                            resumeQualifiedPlayTimer()
                        }
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                precacheKickoffJob?.cancel()
                readySinceMs = 0L
                healthyForPrecache = false
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    sleepTimer.onTrackTransition()
                }
                val newIndex = player.currentMediaItemIndex
                stateHub.publishQueue(stateHub.queueEngine.setCurrentIndex(newIndex))
                stateHub.updatePosition(0L, player.duration)

                val track = stateHub.queueEngine.snapshot.currentTrack
                if (track?.videoId != lastRetriedVideoId) {
                    lastRetriedVideoId = null
                }
                trackPrecacher.onPlaybackLoadingOrBuffering(track?.videoId)
                if (track != null) {
                    onActiveTrackChanged(track)
                }
                if (player.isPlaying) {
                    ensurePositionTickerRunning()
                }
                persistQueueSnapshot()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                    reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
                ) {
                    healthyForPrecache = false
                    stateHub.updatePosition(player.currentPosition, player.duration)
                    trackPrecacher.onPlaybackLoadingOrBuffering(
                        stateHub.queueEngine.snapshot.currentTrack?.videoId
                    )
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                stateHub.updateAudioSessionId(audioSessionId)
                setupLoudnessEnhancer(audioSessionId)
            }

            override fun onPlayerError(error: PlaybackException) {
                val track = stateHub.queueEngine.snapshot.currentTrack
                val rootCause = generateSequence(error.cause) { it.cause }.lastOrNull() ?: error
                val isOutOfRange = error.errorCode == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE ||
                    generateSequence<Throwable>(error) { it.cause }.any {
                        (it as? DataSourceException)?.reason == DataSourceException.POSITION_OUT_OF_RANGE ||
                            (it as? HttpDataSource.InvalidResponseCodeException)?.responseCode == 416
                    }
                val detail = when {
                    rootCause is HttpDataSource.InvalidResponseCodeException -> {
                        val src = stateHub.activeStreamInfo.value?.clientSource ?: "?"
                        "HTTP ${rootCause.responseCode} ($src)"
                    }
                    isOutOfRange -> "stream cache range mismatch"
                    else -> rootCause.message ?: error.errorCodeName
                }

                if (track != null) {
                    val shouldRetryOnce = lastRetriedVideoId != track.videoId
                    if (shouldRetryOnce) {
                        lastRetriedVideoId = track.videoId
                        val resumeIdx = player.currentMediaItemIndex.coerceAtLeast(0)
                        val curPos = player.currentPosition.coerceAtLeast(0L)
                        val curDur = player.duration
                        val safeResumePos = if (curDur > 0L && curPos >= curDur - 3_000L) 0L else curPos
                        serviceScope.launch {
                            kotlinx.coroutines.withContext(Dispatchers.IO) {
                                cacheDao.invalidateStream(track.videoId)
                                trackPrecacher.evictTrackStreamCache(track.videoId)
                            }
                            if (isOutOfRange) {
                                player.seekTo(resumeIdx, safeResumePos)
                            }
                            player.prepare()
                            player.play()
                        }
                        return
                    }
                    serviceScope.launch(Dispatchers.IO) {
                        cacheDao.invalidateStream(track.videoId)
                        trackPrecacher.evictTrackStreamCache(track.videoId)
                    }
                }
                stateHub.emitError("playback failed: $detail")
            }
        })
    }

    private fun onActiveTrackChanged(track: Track) {
        // Fetch SponsorBlock skip segments if enabled
        skipSegments = emptyList()
        if (stateHub.sponsorBlockEnabled) {
            serviceScope.launch {
                skipSegments = streamResolver.fetchSponsorBlockSegments(track.videoId)
                if (skipSegments.isNotEmpty() && player.isPlaying) {
                    ensurePositionTickerRunning()
                }
            }
        }
        // Trigger endless AutoMix continuation when nearing end of queue
        val snap = stateHub.queueEngine.snapshot
        val remaining = snap.items.size - 1 - snap.currentIndex
        val continuation = stateHub.autoMixContinuation
        if (remaining <= 2 && continuation != null && lastAutoMixCheckVideoId != track.videoId) {
            lastAutoMixCheckVideoId = track.videoId
            serviceScope.launch {
                val prefs = preferencesStore.current()
                if (!prefs.autoMix) return@launch
                runCatching {
                    val nextPage = musicRepository.getNext(
                        playlistId = stateHub.autoMixPlaylistId,
                        continuation = continuation
                    )
                    stateHub.autoMixContinuation = nextPage.continuation
                    val existingItems = stateHub.queueEngine.snapshot.items
                    val fresh = InnerTubeParsers.deduplicateMixTracks(
                        tracks = nextPage.tracks,
                        existing = existingItems
                    )
                    if (fresh.isNotEmpty()) {
                        val (updated, _) = stateHub.queueEngine.addAllToQueue(fresh, "AutoMix")
                        stateHub.publishQueue(updated)
                        player.addMediaItems(fresh.map { it.toMediaItem() })
                    }
                }
            }
        }
        applyLoudnessForCurrentTrack()
        resetQualifiedPlayTracking(track)

        serviceScope.launch(Dispatchers.IO) {
            val cached = runCatching { musicRepository.getTrackCachedState(track.videoId) }.getOrNull()
            if (cached != null) {
                val hydrated = track.copy(
                    inLibrary = track.inLibrary || cached.inLibrary,
                    isLiked = track.isLiked || cached.isLiked,
                    album = track.album ?: cached.album,
                    feedbackTokenAdd = track.feedbackTokenAdd ?: cached.feedbackTokenAdd,
                    feedbackTokenRemove = track.feedbackTokenRemove ?: cached.feedbackTokenRemove
                )
                if (hydrated != track) {
                    kotlinx.coroutines.withContext(Dispatchers.Main.immediate) {
                        stateHub.updateTrackMetadata(hydrated)
                    }
                }
            }
        }
    }

    private fun flushListenSessionIfNeeded(immediateOnDestroy: Boolean = false) {
        val track = qualifiedPlayTrack ?: return
        if (qualifiedPlaySegmentStartMs > 0L) {
            val elapsed = (android.os.SystemClock.elapsedRealtime() - qualifiedPlaySegmentStartMs).coerceAtLeast(0L)
            qualifiedPlayAccumulatedMs += elapsed
            qualifiedPlaySegmentStartMs = 0L
        }
        val deltaMs = (qualifiedPlayAccumulatedMs - qualifiedPlayFlushedMs).coerceAtLeast(0L)
        if (deltaMs < MIN_LISTEN_SESSION_MS) return
        qualifiedPlayFlushedMs = qualifiedPlayAccumulatedMs
        if (immediateOnDestroy) {
            runBlocking(Dispatchers.IO) {
                runCatching { musicRepository.recordListenSession(track, deltaMs) }
            }
        } else {
            serviceScope.launch(Dispatchers.IO) {
                runCatching { musicRepository.recordListenSession(track, deltaMs) }
            }
        }
    }

    private fun resetQualifiedPlayTracking(track: Track?) {
        qualifiedPlayJob?.cancel()
        qualifiedPlayJob = null
        flushListenSessionIfNeeded(immediateOnDestroy = false)
        qualifiedPlayTrack = track
        qualifiedPlayVideoId = track?.videoId
        qualifiedPlayRecorded = false
        qualifiedPlayAccumulatedMs = 0L
        qualifiedPlayFlushedMs = 0L
        qualifiedPlaySegmentStartMs = 0L
        if (track != null && player.isPlaying) {
            resumeQualifiedPlayTimer()
        }
    }

    private fun pauseQualifiedPlayTimer() {
        qualifiedPlayJob?.cancel()
        qualifiedPlayJob = null
        flushListenSessionIfNeeded(immediateOnDestroy = false)
    }

    private fun resumeQualifiedPlayTimer() {
        val track = qualifiedPlayTrack ?: return
        if (!player.isPlaying) return
        if (qualifiedPlaySegmentStartMs == 0L) {
            qualifiedPlaySegmentStartMs = android.os.SystemClock.elapsedRealtime()
        }
        if (qualifiedPlayRecorded || qualifiedPlayJob?.isActive == true) return
        val dur = if (player.duration > 0L) player.duration else track.durationMs
        val thresholdMs = if (dur > 0L) {
            minOf(QUALIFIED_PLAY_MIN_MS, (dur / 2L).coerceAtLeast(8_000L))
        } else {
            QUALIFIED_PLAY_MIN_MS
        }
        val remainingMs = (thresholdMs - qualifiedPlayAccumulatedMs).coerceAtLeast(0L)
        qualifiedPlayJob = serviceScope.launch {
            if (remainingMs > 0L) {
                delay(remainingMs)
            }
            if (!player.isPlaying || qualifiedPlayRecorded || qualifiedPlayVideoId != track.videoId) {
                return@launch
            }
            qualifiedPlayRecorded = true
            // Keep qualifiedPlaySegmentStartMs active so total listen time accrues beyond the 20s mark.
            val now = System.currentTimeMillis()
            val shouldRecord = track.videoId != lastRecordedPlayVideoId || (now - lastRecordedPlayAtMs > 10_000L)
            if (shouldRecord) {
                lastRecordedPlayVideoId = track.videoId
                lastRecordedPlayAtMs = now
                withContext(Dispatchers.IO) {
                    runCatching { musicRepository.recordTrackPlayed(track) }
                }
            }
        }
    }

    private fun setupLoudnessEnhancer(sessionId: Int) {
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId == 0) return
        runCatching {
            loudnessEnhancer?.release()
            loudnessEnhancer = LoudnessEnhancer(sessionId)
            applyLoudnessForCurrentTrack()
        }
    }

    private fun applyLoudnessForCurrentTrack() {
        val db = stateHub.activeStreamInfo.value?.loudnessDb
        if (!stateHub.normalizeVolume || db == null) {
            normalizationGainMultiplier = 1f
            runCatching { loudnessEnhancer?.enabled = false }
            applyEffectiveVolume()
            return
        }
        // Normalize toward -14 LUFS equivalent
        val gainDb = (-db).coerceIn(-12f, 6f)
        if (gainDb < 0f) {
            normalizationGainMultiplier = 10f.pow(gainDb / 20f).coerceIn(0.25f, 1f)
            runCatching { loudnessEnhancer?.enabled = false }
        } else {
            normalizationGainMultiplier = 1f
            runCatching {
                loudnessEnhancer?.setTargetGain((gainDb * 100f).roundToInt())
                loudnessEnhancer?.enabled = true
            }
        }
        applyEffectiveVolume()
    }

    private fun applyEffectiveVolume() {
        player.volume = (normalizationGainMultiplier * sleepFadeMultiplier).coerceIn(0f, 1f)
    }

    private fun setupMediaSession() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = if (launchIntent != null) {
            PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else null

        val likeButton = CommandButton.Builder(CommandButton.ICON_HEART_UNFILLED)
            .setDisplayName("Like")
            .setSessionCommand(SessionCommand(CMD_TOGGLE_LIKE, Bundle.EMPTY))
            .build()

        val shuffleButton = CommandButton.Builder(CommandButton.ICON_SHUFFLE_OFF)
            .setDisplayName("Shuffle")
            .setSessionCommand(SessionCommand(CMD_TOGGLE_SHUFFLE, Bundle.EMPTY))
            .build()

        mediaSession = MediaLibrarySession.Builder(
            this,
            player,
            object : MediaLibrarySession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                        .buildUpon()
                        .add(SessionCommand(CMD_TOGGLE_LIKE, Bundle.EMPTY))
                        .add(SessionCommand(CMD_TOGGLE_SHUFFLE, Bundle.EMPTY))
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(sessionCommands)
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    when (customCommand.customAction) {
                        CMD_TOGGLE_LIKE -> {
                            stateHub.queueEngine.snapshot.currentTrack?.let { current ->
                                serviceScope.launch {
                                    musicRepository.toggleLike(current).onSuccess { liked ->
                                        stateHub.updateTrackMetadata(current.copy(isLiked = liked))
                                    }
                                }
                            }
                        }
                        CMD_TOGGLE_SHUFFLE -> {
                            val next = !player.shuffleModeEnabled
                            player.shuffleModeEnabled = next
                            stateHub.publishQueue(stateHub.queueEngine.setShuffleEnabled(next))
                        }
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
        )
            .setCustomLayout(listOf(likeButton, shuffleButton))
            .apply { if (pendingIntent != null) setSessionActivity(pendingIntent) }
            .build()
    }

    private fun scheduleFastPrecacheCheck() {
        if (!player.isPlaying || player.playbackState != Player.STATE_READY) return
        precacheKickoffJob?.cancel()
        precacheKickoffJob = serviceScope.launch {
            delay(450L)
            evaluatePlaybackHealthForPrecache()
        }
    }

    private fun evaluatePlaybackHealthForPrecache() {
        val snap = stateHub.queueEngine.snapshot
        val activeTrack = snap.currentTrack
        if (activeTrack == null || !player.isPlaying || player.playbackState != Player.STATE_READY) {
            healthyForPrecache = false
            return
        }
        val readyElapsedMs = if (readySinceMs > 0L) {
            android.os.SystemClock.elapsedRealtime() - readySinceMs
        } else 0L
        val leadBufferMs = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0L)
        val healthy = readyElapsedMs >= 400L &&
            (leadBufferMs >= 3_500L || player.bufferedPercentage >= 10)
        healthyForPrecache = healthy
        if (healthy) {
            val upcoming = listOfNotNull(
                snap.items.getOrNull(snap.currentIndex + 1),
                snap.items.getOrNull(snap.currentIndex + 2)
            )
            trackPrecacher.precacheNextIfHealthy(
                activeVideoId = activeTrack.videoId,
                upcoming = upcoming,
                isPlaybackHealthy = ::isPrecacheHealthy
            )
        }
    }

    private fun isPrecacheHealthy(): Boolean = healthyForPrecache

    private fun registerVpnAndNetworkObserver() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val handle = network.networkHandle
                if (lastNetworkHandle != null && lastNetworkHandle != handle) {
                    evictStaleSockets()
                }
                lastNetworkHandle = handle
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val vpnNow = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                if (lastVpnActive != null && lastVpnActive != vpnNow) {
                    evictStaleSockets()
                }
                lastVpnActive = vpnNow
            }

            override fun onLost(network: Network) {
                evictStaleSockets()
            }
        }
        runCatching {
            cm.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        }
    }

    private fun evictStaleSockets() {
        serviceScope.launch(Dispatchers.IO) {
            runCatching { okHttpClient.connectionPool.evictAll() }
        }
    }

    private fun stopPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = null
    }

    private fun ensurePositionTickerRunning() {
        if (!player.isPlaying || positionTickerJob?.isActive == true) return
        positionTickerJob = serviceScope.launch {
            while (isActive && player.isPlaying) {
                val fg = stateHub.isUiForeground.value
                val lyricsActive = stateHub.isLyricsActive.value
                val hasSkips = skipSegments.isNotEmpty()
                val pos = player.currentPosition
                val dur = player.duration
                if (fg || hasSkips) {
                    stateHub.updatePosition(pos, dur)
                }
                if (hasSkips) {
                    val hit = skipSegments.firstOrNull { pos in it.startMs until (it.endMs - 250L) }
                    if (hit != null) {
                        player.seekTo(hit.endMs)
                    }
                }
                evaluatePlaybackHealthForPrecache()
                if (!fg && !hasSkips) {
                    if (healthyForPrecache) {
                        break
                    }
                    delay(1_500L)
                } else {
                    delay(if (fg && lyricsActive) 250L else 500L)
                }
            }
        }
    }

    private fun restorePersistedQueue() {
        serviceScope.launch {
            val saved = runCatching { cacheDao.getPersistedQueue() }.getOrDefault(emptyList())
            if (saved.isEmpty()) return@launch
            val tracks = saved.mapNotNull { runCatching { json.decodeFromString<Track>(it.trackJson) }.getOrNull() }
            if (tracks.isEmpty()) return@launch
            val currentIdx = saved.indexOfFirst { it.isCurrent }.coerceAtLeast(0).coerceAtMost(tracks.lastIndex)
            val rawPos = saved.getOrNull(currentIdx)?.playbackPositionMs ?: 0L
            val curDur = tracks[currentIdx].durationMs
            val savedPos = if (curDur > 0L && rawPos >= curDur - 2_000L) 0L else rawPos.coerceAtLeast(0L)
            stateHub.publishQueue(stateHub.queueEngine.replaceWithContext(tracks, currentIdx, "queue"))
            player.setMediaItems(tracks.map { it.toMediaItem() }, currentIdx, savedPos)
            player.playWhenReady = false
        }
    }

    private fun persistQueueSnapshot(immediate: Boolean = false) {
        val snap = stateHub.queueEngine.snapshot
        val pos = player.currentPosition.coerceAtLeast(0L)
        persistQueueJob?.cancel()
        if (immediate) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                runCatching { writePersistedQueue(snap, pos) }
            }
            return
        }
        persistQueueJob = serviceScope.launch(Dispatchers.IO) {
            delay(1_200L)
            writePersistedQueue(snap, pos)
        }
    }

    private suspend fun writePersistedQueue(
        snap: com.lin.music.core.playback.queue.QueueSnapshot,
        pos: Long
    ) {
        cacheDao.clearPersistedQueue()
        if (snap.items.isNotEmpty()) {
            val entities = snap.items.mapIndexed { idx, track ->
                PersistedQueueEntity(
                    position = idx,
                    videoId = track.videoId,
                    trackJson = json.encodeToString(track),
                    isCurrent = idx == snap.currentIndex,
                    playbackPositionMs = if (idx == snap.currentIndex) pos else 0L
                )
            }
            cacheDao.savePersistedQueue(entities)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        persistQueueSnapshot(immediate = true)
        networkCallback?.let { cb ->
            runCatching { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) }
            networkCallback = null
        }
        precacheKickoffJob?.cancel()
        qualifiedPlayJob?.cancel()
        flushListenSessionIfNeeded(immediateOnDestroy = true)
        persistQueueJob?.cancel()
        stateHub.serviceTimerBridge = null
        positionTickerJob?.cancel()
        sleepTimer.cancel()
        runCatching { loudnessEnhancer?.release() }
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    // --- ServiceTimerBridge ---

    override fun startCountdown(durationMs: Long, fadeOut: Boolean) {
        sleepTimer.startCountdown(durationMs, fadeOut)
    }

    override fun startEndOfTrack(fadeOut: Boolean) {
        sleepTimer.startEndOfTrack(fadeOut)
    }

    override fun extend(extraMs: Long) {
        sleepTimer.extend(extraMs)
    }

    override fun cancel() {
        sleepTimer.cancel()
    }

    override fun applyPreferences(normalize: Boolean, skipSilence: Boolean, speed: Float, pitch: Float) {
        stateHub.normalizeVolume = normalize
        player.skipSilenceEnabled = skipSilence
        player.playbackParameters = if (abs(speed - 1.0f) < 0.01f && abs(pitch - 1.0f) < 0.01f) {
            PlaybackParameters.DEFAULT
        } else {
            PlaybackParameters(speed.coerceIn(0.5f, 2f), pitch.coerceIn(0.5f, 2f))
        }
        applyLoudnessForCurrentTrack()
    }

    companion object {
        const val CMD_TOGGLE_LIKE = "com.lin.music.CMD_TOGGLE_LIKE"
        const val CMD_TOGGLE_SHUFFLE = "com.lin.music.CMD_TOGGLE_SHUFFLE"
        private const val QUALIFIED_PLAY_MIN_MS = 20_000L
        private const val MIN_LISTEN_SESSION_MS = 5_000L

        fun Track.toMediaItem(): MediaItem {
            val metadata = MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artistDisplay)
                .setAlbumTitle(album?.title)
                .setArtworkUri(thumbnailUrl?.let { Uri.parse(it) })
                .build()
            return MediaItem.Builder()
                .setMediaId(videoId)
                .setUri("${PlaybackModule.STREAM_URI_PREFIX}$videoId")
                .setCustomCacheKey(videoId)
                .setMediaMetadata(metadata)
                .build()
        }
    }
}
