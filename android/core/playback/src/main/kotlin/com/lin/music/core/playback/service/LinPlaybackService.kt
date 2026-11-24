package com.lin.music.core.playback.service

import android.app.PendingIntent
import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.database.ContentObserver
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
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
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.lin.music.core.data.database.CacheDao
import com.lin.music.core.data.database.PersistedQueueEntity
import com.lin.music.core.data.database.toEntity
import com.lin.music.core.data.datastore.PreferencesStore
import com.lin.music.core.data.repository.MusicRepository
import com.lin.music.core.innertube.parser.InnerTubeParsers
import com.lin.music.core.innertube.stream.StreamResolver
import com.lin.music.core.model.AmbientMode
import com.lin.music.core.model.BuiltInEqPresets
import com.lin.music.core.model.RepeatMode
import com.lin.music.core.model.ShuffleMode
import com.lin.music.core.model.SkipSegment
import com.lin.music.core.model.Track
import com.lin.music.core.playback.audio.LinEqualizerController
import com.lin.music.core.playback.cache.TrackPrecacher
import com.lin.music.core.playback.di.PlaybackModule
import com.lin.music.core.playback.di.StreamCacheDataSourceFactory
import com.lin.music.core.playback.download.DownloadController
import com.lin.music.core.playback.download.SmartDownloadManager
import com.lin.music.core.playback.queue.PlaybackStateHub
import com.lin.music.core.playback.queue.SmartShuffleEngine
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
    private val equalizerController = LinEqualizerController()
    private var activeAudioSessionId: Int = 0
    private var audioDeviceCallback: AudioDeviceCallback? = null
    private var activeBluetoothDeviceKey: String? = null
    private val connectedAutoControllers = mutableSetOf<String>()
    private var carModeReceiver: BroadcastReceiver? = null
    private var carConnectionObserver: ContentObserver? = null
    @Volatile private var carProviderConnected: Boolean = false
    private var positionTickerJob: Job? = null
    private var precacheKickoffJob: Job? = null
    private var qualifiedPlayJob: Job? = null
    private var persistQueueJob: Job? = null
    private var microFadeJob: Job? = null
    private var crossfadeJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastNetworkHandle: Long? = null
    private var lastVpnActive: Boolean? = null
    private var readySinceMs: Long = 0L
    @Volatile private var healthyForPrecache: Boolean = false
    private var skipSegments: List<SkipSegment> = emptyList()
    private var lastAutoMixCheckVideoId: String? = null
    @Volatile private var lastRetriedVideoId: String? = null
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
    private var microFadeMultiplier: Float = 1f
    private var crossfadeMultiplier: Float = 1f

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
        registerAudioDeviceObserver()

        serviceScope.launch {
            sleepTimer.state.collectLatest { stateHub.updateSleepTimer(it) }
        }

        serviceScope.launch {
            runCatching { preferencesStore.ensureNormalSpeedDefaultOnce() }
            preferencesStore.preferencesFlow.collectLatest { prefs ->
                stateHub.latestPreferences = prefs
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
                applyEqualizer()
                scheduleCrossfadeIfNeeded()
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
        serviceScope.launch {
            stateHub.queueSnapshot
                .map { snap -> (snap.currentTrack?.isLiked == true) to snap.shuffleEnabled }
                .distinctUntilChanged()
                .collectLatest { (isLiked, shuffleEnabled) ->
                    updateSessionNotificationButtons(isLiked, shuffleEnabled)
                }
        }
        registerCarConnectionObservers()
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
                    scheduleCrossfadeIfNeeded()
                } else {
                    crossfadeJob?.cancel()
                    crossfadeMultiplier = 1f
                    applyEffectiveVolume()
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
                        crossfadeJob?.cancel()
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
                            scheduleCrossfadeIfNeeded()
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
                    scheduleCrossfadeIfNeeded()
                }
                persistQueueSnapshot()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                scheduleCrossfadeIfNeeded()
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
                    scheduleCrossfadeIfNeeded()
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                activeAudioSessionId = audioSessionId
                stateHub.updateAudioSessionId(audioSessionId)
                setupLoudnessEnhancer(audioSessionId)
                applyEqualizer()
            }

            override fun onPlayerError(error: PlaybackException) {
                val track = stateHub.queueEngine.snapshot.currentTrack
                val rootCause = generateSequence(error.cause) { it.cause }.lastOrNull() ?: error
                val isOutOfRange = error.errorCode == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE ||
                    generateSequence<Throwable>(error) { it.cause }.any {
                        (it as? DataSourceException)?.reason == DataSourceException.POSITION_OUT_OF_RANGE ||
                            (it as? HttpDataSource.InvalidResponseCodeException)?.responseCode == 416
                    }
                val isForbiddenOrExpired = generateSequence<Throwable>(error) { it.cause }.any {
                    val code = (it as? HttpDataSource.InvalidResponseCodeException)?.responseCode
                    code == 403 || code == 410
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
                            withContext(Dispatchers.IO) {
                                streamResolver.invalidateStream(track.videoId, clearVisitorData = isForbiddenOrExpired)
                                cacheDao.invalidateStream(track.videoId)
                                trackPrecacher.evictTrackStreamCache(track.videoId)
                                if (isForbiddenOrExpired) {
                                    runCatching { okHttpClient.connectionPool.evictAll() }
                                }
                            }
                            if (isOutOfRange || isForbiddenOrExpired) {
                                player.seekTo(resumeIdx, safeResumePos)
                            }
                            player.prepare()
                            player.play()
                        }
                        return
                    }
                    serviceScope.launch(Dispatchers.IO) {
                        streamResolver.invalidateStream(track.videoId, clearVisitorData = isForbiddenOrExpired)
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
                if (!prefs.autoMix || prefs.offlineMode) return@launch
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
        player.volume = (normalizationGainMultiplier * sleepFadeMultiplier * microFadeMultiplier * crossfadeMultiplier)
            .coerceIn(0f, 1f)
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

        val initSnap = stateHub.queueEngine.snapshot

        mediaSession = MediaLibrarySession.Builder(
            this,
            player,
            object : MediaLibrarySession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    if (isAutomotiveControllerPackage(controller.packageName)) {
                        connectedAutoControllers.add(controller.packageName)
                        updateCarConnectionState()
                    }
                    val curSnap = stateHub.queueEngine.snapshot
                    val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                        .buildUpon()
                        .add(SessionCommand(CMD_TOGGLE_LIKE, Bundle.EMPTY))
                        .add(SessionCommand(CMD_TOGGLE_SHUFFLE, Bundle.EMPTY))
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(sessionCommands)
                        .setCustomLayout(
                            buildNotificationButtons(
                                isLiked = curSnap.currentTrack?.isLiked == true,
                                shuffleEnabled = curSnap.shuffleEnabled
                            )
                        )
                        .build()
                }

                override fun onDisconnected(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ) {
                    if (connectedAutoControllers.remove(controller.packageName)) {
                        updateCarConnectionState()
                    }
                }

                override fun onGetLibraryRoot(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    params: LibraryParams?
                ): ListenableFuture<LibraryResult<MediaItem>> =
                    Futures.immediateFuture(LibraryResult.ofItem(LinAutoMediaTree.rootItem(), params))

                override fun onGetItem(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    mediaId: String
                ): ListenableFuture<LibraryResult<MediaItem>> {
                    val item = when {
                        mediaId == LinAutoMediaTree.ROOT_ID -> LinAutoMediaTree.rootItem()
                        mediaId.startsWith(LinAutoMediaTree.TRACK_PREFIX) -> {
                            val parts = mediaId.removePrefix(LinAutoMediaTree.TRACK_PREFIX).split(":", limit = 2)
                            val videoId = parts.getOrNull(1).orEmpty()
                            val found = stateHub.queueEngine.snapshot.items.firstOrNull { it.videoId == videoId }
                            found?.let { LinAutoMediaTree.trackItems(listOf(it), parts.getOrNull(0).orEmpty()).firstOrNull() }
                        }
                        else -> LinAutoMediaTree.topLevelCategories().firstOrNull { it.mediaId == mediaId }
                    }
                    return Futures.immediateFuture(
                        if (item != null) LibraryResult.ofItem(item, null)
                        else LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                    )
                }

                override fun onGetChildren(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    parentId: String,
                    page: Int,
                    pageSize: Int,
                    params: LibraryParams?
                ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                    if (parentId == LinAutoMediaTree.ROOT_ID) {
                        val paged = paginateItems(LinAutoMediaTree.topLevelCategories(), page, pageSize)
                        return Futures.immediateFuture(LibraryResult.ofItemList(paged, params))
                    }
                    val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                    serviceScope.launch(Dispatchers.IO) {
                        val items = runCatching {
                            when {
                                parentId == LinAutoMediaTree.DOWNLOADS_ID -> {
                                    LinAutoMediaTree.trackItems(musicRepository.observeDownloadedTracks().first(), "downloads")
                                }
                                parentId == LinAutoMediaTree.PLAYLISTS_ID -> {
                                    LinAutoMediaTree.playlistItems(musicRepository.observePlaylists().first())
                                }
                                parentId == LinAutoMediaTree.RECENT_ID -> {
                                    LinAutoMediaTree.trackItems(musicRepository.observeHistoryTracks().first().take(80), "recent")
                                }
                                parentId == LinAutoMediaTree.SONGS_ID -> {
                                    LinAutoMediaTree.trackItems(musicRepository.observeLibraryTracks().first().take(150), "songs")
                                }
                                parentId.startsWith(LinAutoMediaTree.PLAYLIST_PREFIX) -> {
                                    val plId = parentId.removePrefix(LinAutoMediaTree.PLAYLIST_PREFIX)
                                    val cached = musicRepository.getPlaylistTracksSnapshot(plId)
                                    val tracks = if (cached.isNotEmpty() || stateHub.latestPreferences.offlineMode) {
                                        cached
                                    } else {
                                        runCatching { musicRepository.getPlaylist("VL$plId").tracks }.getOrDefault(emptyList())
                                    }
                                    LinAutoMediaTree.trackItems(tracks, "pl_$plId")
                                }
                                else -> emptyList()
                            }
                        }.getOrDefault(emptyList())
                        future.set(LibraryResult.ofItemList(paginateItems(items, page, pageSize), params))
                    }
                    return future
                }

                override fun onSearch(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    query: String,
                    params: LibraryParams?
                ): ListenableFuture<LibraryResult<Void>> {
                    val future = SettableFuture.create<LibraryResult<Void>>()
                    serviceScope.launch(Dispatchers.IO) {
                        val results = resolveSearchTracks(query)
                        autoSearchCache = results
                        session.notifySearchResultChanged(browser, query, results.size, params)
                        future.set(LibraryResult.ofVoid(params))
                    }
                    return future
                }

                override fun onGetSearchResult(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    query: String,
                    page: Int,
                    pageSize: Int,
                    params: LibraryParams?
                ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                    val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                    serviceScope.launch(Dispatchers.IO) {
                        val results = autoSearchCache.ifEmpty { resolveSearchTracks(query) }
                        val items = LinAutoMediaTree.trackItems(results, "search")
                        future.set(LibraryResult.ofItemList(paginateItems(items, page, pageSize), params))
                    }
                    return future
                }

                override fun onSetMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                    startIndex: Int,
                    startPositionMs: Long
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    val voiceQuery = mediaItems.firstOrNull()?.requestMetadata?.searchQuery
                    if (!voiceQuery.isNullOrBlank()) {
                        val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
                        serviceScope.launch {
                            val tracks = withContext(Dispatchers.IO) { resolveSearchTracks(voiceQuery) }
                            if (tracks.isEmpty()) {
                                future.set(MediaSession.MediaItemsWithStartPosition(emptyList(), C.INDEX_UNSET, 0L))
                            } else {
                                val snap = stateHub.queueEngine.replaceWithContext(tracks, 0, "voice · $voiceQuery")
                                stateHub.publishQueue(snap)
                                future.set(
                                    MediaSession.MediaItemsWithStartPosition(
                                        tracks.map { it.toMediaItem() },
                                        0,
                                        0L
                                    )
                                )
                            }
                        }
                        return future
                    }

                    val firstId = mediaItems.firstOrNull()?.mediaId.orEmpty()
                    if (firstId.startsWith(LinAutoMediaTree.TRACK_PREFIX)) {
                        val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
                        serviceScope.launch {
                            val payload = firstId.removePrefix(LinAutoMediaTree.TRACK_PREFIX)
                            val contextTag = payload.substringBefore(":", "")
                            val videoId = payload.substringAfter(":", "")
                            val contextTracks = withContext(Dispatchers.IO) { resolveContextTracksForAuto(contextTag, videoId) }
                            val resolvedIdx = contextTracks.indexOfFirst { it.videoId == videoId }.coerceAtLeast(0)
                            if (contextTracks.isNotEmpty()) {
                                val snap = stateHub.queueEngine.replaceWithContext(contextTracks, resolvedIdx, "auto · $contextTag")
                                stateHub.publishQueue(snap)
                                future.set(
                                    MediaSession.MediaItemsWithStartPosition(
                                        contextTracks.map { it.toMediaItem() },
                                        resolvedIdx,
                                        startPositionMs.coerceAtLeast(0L)
                                    )
                                )
                            } else {
                                future.set(MediaSession.MediaItemsWithStartPosition(emptyList(), C.INDEX_UNSET, 0L))
                            }
                        }
                        return future
                    }

                    return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
                }

                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>
                ): ListenableFuture<MutableList<MediaItem>> {
                    val future = SettableFuture.create<MutableList<MediaItem>>()
                    serviceScope.launch {
                        val mapped = mediaItems.mapNotNull { item ->
                            val id = item.mediaId
                            val videoId = if (id.startsWith(LinAutoMediaTree.TRACK_PREFIX)) {
                                id.removePrefix(LinAutoMediaTree.TRACK_PREFIX).substringAfter(":", "")
                            } else id
                            val existing = stateHub.queueEngine.snapshot.items.firstOrNull { it.videoId == videoId }
                                ?: withContext(Dispatchers.IO) { musicRepository.getTrackCachedState(videoId) }
                            existing?.toMediaItem() ?: item.takeIf { it.localConfiguration != null }
                        }.toMutableList()
                        future.set(mapped)
                    }
                    return future
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
                            serviceScope.launch {
                                toggleShuffleFromService()
                            }
                        }
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
        )
            .setCustomLayout(
                buildNotificationButtons(
                    isLiked = initSnap.currentTrack?.isLiked == true,
                    shuffleEnabled = initSnap.shuffleEnabled
                )
            )
            .apply { if (pendingIntent != null) setSessionActivity(pendingIntent) }
            .build()
    }

    private fun buildNotificationButtons(isLiked: Boolean, shuffleEnabled: Boolean): ImmutableList<CommandButton> {
        val likeButton = CommandButton.Builder(
            if (isLiked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED
        )
            .setDisplayName(if (isLiked) "Unlike" else "Like")
            .setSessionCommand(SessionCommand(CMD_TOGGLE_LIKE, Bundle.EMPTY))
            .build()

        val shuffleButton = CommandButton.Builder(
            if (shuffleEnabled) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF
        )
            .setDisplayName(if (shuffleEnabled) "Shuffle on" else "Shuffle off")
            .setSessionCommand(SessionCommand(CMD_TOGGLE_SHUFFLE, Bundle.EMPTY))
            .build()

        return ImmutableList.of(likeButton, shuffleButton)
    }

    private fun updateSessionNotificationButtons(isLiked: Boolean, shuffleEnabled: Boolean) {
        mediaSession?.setCustomLayout(buildNotificationButtons(isLiked, shuffleEnabled))
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
                    onNetworkOrVpnChanged(mayRecoverBuffering = true)
                }
                lastNetworkHandle = handle
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val vpnNow = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                if (lastVpnActive != null && lastVpnActive != vpnNow) {
                    onNetworkOrVpnChanged(mayRecoverBuffering = true)
                }
                lastVpnActive = vpnNow
            }

            override fun onLost(network: Network) {
                onNetworkOrVpnChanged(mayRecoverBuffering = false)
            }
        }
        runCatching {
            cm.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        }
    }

    private fun onNetworkOrVpnChanged(mayRecoverBuffering: Boolean) {
        lastRetriedVideoId = null
        serviceScope.launch(Dispatchers.IO) {
            runCatching { okHttpClient.connectionPool.evictAll() }
            streamResolver.invalidateAllStreams()
            runCatching { cacheDao.clearAllStreamCache() }
        }
        if (mayRecoverBuffering) {
            serviceScope.launch {
                delay(400L)
                if (player.playWhenReady &&
                    (player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_IDLE) &&
                    player.mediaItemCount > 0
                ) {
                    val idx = player.currentMediaItemIndex.coerceAtLeast(0)
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    player.seekTo(idx, pos)
                    player.prepare()
                    player.play()
                }
            }
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
            val firstRow = saved.first()
            val savedLabel = firstRow.sourceLabel.ifBlank { "queue" }
            val savedShuffle = firstRow.shuffleEnabled
            val savedRepeat = runCatching { RepeatMode.valueOf(firstRow.repeatMode) }.getOrDefault(RepeatMode.OFF)

            stateHub.publishQueue(
                stateHub.queueEngine.restorePersisted(
                    tracks = tracks,
                    currentIndex = currentIdx,
                    sourceLabel = savedLabel,
                    shuffleEnabled = savedShuffle,
                    repeatMode = savedRepeat
                )
            )
            player.shuffleModeEnabled = false
            player.repeatMode = when (savedRepeat) {
                RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            }
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
                    playbackPositionMs = if (idx == snap.currentIndex) pos else 0L,
                    sourceLabel = snap.sourceLabel,
                    shuffleEnabled = snap.shuffleEnabled,
                    repeatMode = snap.repeatMode.name
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
        audioDeviceCallback?.let { cb ->
            runCatching { getSystemService(AudioManager::class.java)?.unregisterAudioDeviceCallback(cb) }
            audioDeviceCallback = null
        }
        carModeReceiver?.let { r ->
            runCatching { unregisterReceiver(r) }
            carModeReceiver = null
        }
        carConnectionObserver?.let { obs ->
            runCatching { contentResolver.unregisterContentObserver(obs) }
            carConnectionObserver = null
        }
        precacheKickoffJob?.cancel()
        qualifiedPlayJob?.cancel()
        microFadeJob?.cancel()
        crossfadeJob?.cancel()
        flushListenSessionIfNeeded(immediateOnDestroy = true)
        persistQueueJob?.cancel()
        stateHub.serviceTimerBridge = null
        positionTickerJob?.cancel()
        sleepTimer.cancel()
        equalizerController.release()
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
        applyEqualizer()
    }

    override fun requestMicroFadePause() {
        microFadeJob?.cancel()
        microFadeJob = serviceScope.launch {
            val steps = 6
            for (s in steps - 1 downTo 0) {
                microFadeMultiplier = s / steps.toFloat()
                applyEffectiveVolume()
                delay(30L)
            }
            player.pause()
            microFadeMultiplier = 1f
            applyEffectiveVolume()
        }
    }

    override fun requestMicroFadePlay() {
        microFadeJob?.cancel()
        if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) {
            player.prepare()
        }
        microFadeMultiplier = 0.08f
        applyEffectiveVolume()
        player.play()
        microFadeJob = serviceScope.launch {
            val steps = 6
            for (s in 1..steps) {
                microFadeMultiplier = s / steps.toFloat()
                applyEffectiveVolume()
                delay(30L)
            }
            microFadeMultiplier = 1f
            applyEffectiveVolume()
        }
    }

    override fun forceApplyEq() {
        applyEqualizer()
    }

    // --- Equalizer & Bluetooth Device Auto-Switching ---

    private fun registerAudioDeviceObserver() {
        val am = getSystemService(AudioManager::class.java) ?: return
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                refreshConnectedAudioDevice()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                refreshConnectedAudioDevice()
            }
        }
        audioDeviceCallback = cb
        am.registerAudioDeviceCallback(cb, null)
        refreshConnectedAudioDevice()
    }

    private fun refreshConnectedAudioDevice() {
        val am = getSystemService(AudioManager::class.java) ?: return
        val preferred = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { isExternalHeadsetOrSpeaker(it) }
        if (preferred == null) {
            activeBluetoothDeviceKey = null
            stateHub.publishActiveOutputDevice(null, null)
        } else {
            val rawName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                preferred.productName?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            } else {
                null
            }
            val displayName = rawName ?: "External Audio"
            val addr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                preferred.address?.takeIf { it.isNotBlank() }
            } else {
                null
            }
            val key = if (addr != null) "addr:$addr" else "type:${preferred.type}:$displayName"
            activeBluetoothDeviceKey = key
            stateHub.publishActiveOutputDevice(key, displayName)
        }
        applyEqualizer()
    }

    private fun isExternalHeadsetOrSpeaker(info: AudioDeviceInfo): Boolean =
        when (info.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> true
            else -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                (info.type == AudioDeviceInfo.TYPE_BLE_HEADSET || info.type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
        }

    private fun applyEqualizer() {
        val prefs = stateHub.latestPreferences
        val btKey = activeBluetoothDeviceKey
        val assignedProfileId = btKey?.let { key ->
            prefs.bluetoothEqAssignments.firstOrNull { it.deviceKey == key }?.profileId
        }
        val profile = BuiltInEqPresets.resolve(
            profileId = assignedProfileId ?: prefs.activeEqProfileId,
            customProfiles = prefs.customEqProfiles
        )
        equalizerController.attachOrUpdate(
            audioSessionId = activeAudioSessionId,
            enabled = prefs.eqEnabled,
            profile = profile
        )
    }

    // --- Smooth Envelope Crossfade (0 polling; single delayed trigger) ---

    private fun scheduleCrossfadeIfNeeded() {
        crossfadeJob?.cancel()
        val prefs = stateHub.latestPreferences
        val crossfadeSec = prefs.crossfadeSeconds.coerceIn(0, 10)
        if (crossfadeSec <= 0 || !player.isPlaying || player.repeatMode == Player.REPEAT_MODE_ONE) {
            crossfadeMultiplier = 1f
            applyEffectiveVolume()
            return
        }
        val snap = stateHub.queueEngine.snapshot
        val curIdx = snap.currentIndex
        val nextIdx = snap.nextTrackIndexOrNull() ?: return
        val curTrack = snap.items.getOrNull(curIdx) ?: return
        val nextTrack = snap.items.getOrNull(nextIdx) ?: return

        // Preserve album transitions naturally when playing consecutive tracks from the same album
        val sameAlbum = !curTrack.album?.id.isNullOrBlank() && curTrack.album?.id == nextTrack.album?.id
        if (sameAlbum) {
            crossfadeMultiplier = 1f
            applyEffectiveVolume()
            return
        }

        val durationMs = player.duration.takeIf { it > 0L } ?: curTrack.durationMs
        if (durationMs <= 0L) return
        val halfFadeMs = (crossfadeSec * 500L).coerceIn(500L, 5_000L)
        val remainingMs = (durationMs - player.currentPosition).coerceAtLeast(0L)
        if (remainingMs <= halfFadeMs + 400L) return

        crossfadeJob = serviceScope.launch {
            delay(remainingMs - halfFadeMs)
            if (!isActive || !player.isPlaying || player.repeatMode == Player.REPEAT_MODE_ONE) return@launch
            val outSteps = 10
            val outStepMs = (halfFadeMs / outSteps).coerceAtLeast(35L)
            for (s in outSteps - 1 downTo 1) {
                if (!isActive || !player.isPlaying) {
                    crossfadeMultiplier = 1f
                    applyEffectiveVolume()
                    return@launch
                }
                crossfadeMultiplier = (s / outSteps.toFloat()).coerceAtLeast(0.08f)
                applyEffectiveVolume()
                delay(outStepMs)
            }
            if (!isActive || !player.isPlaying) return@launch
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
            }
            val inSteps = 10
            val inStepMs = (halfFadeMs / inSteps).coerceAtLeast(35L)
            for (s in 1..inSteps) {
                if (!isActive || !player.isPlaying) break
                crossfadeMultiplier = s / inSteps.toFloat()
                applyEffectiveVolume()
                delay(inStepMs)
            }
            crossfadeMultiplier = 1f
            applyEffectiveVolume()
        }
    }

    // --- Android Auto Browse / Search / Queue Helpers ---

    @Volatile private var autoSearchCache: List<Track> = emptyList()

    private fun paginateItems(items: List<MediaItem>, page: Int, pageSize: Int): ImmutableList<MediaItem> {
        if (items.isEmpty() || page < 0 || pageSize <= 0) return ImmutableList.of()
        val from = (page * pageSize).coerceAtMost(items.size)
        val to = (from + pageSize).coerceAtMost(items.size)
        return ImmutableList.copyOf(items.subList(from, to))
    }

    private suspend fun resolveSearchTracks(query: String): List<Track> {
        val clean = query.trim()
        if (clean.isEmpty()) return emptyList()
        val downloaded = runCatching { musicRepository.observeDownloadedTracks().first() }.getOrDefault(emptyList())
        val library = runCatching { musicRepository.observeLibraryTracks().first() }.getOrDefault(emptyList())
        val localMatches = (downloaded + library)
            .distinctBy { it.videoId }
            .filter {
                it.title.contains(clean, ignoreCase = true) ||
                    it.artistDisplay.contains(clean, ignoreCase = true)
            }
        if (stateHub.latestPreferences.offlineMode) return localMatches.take(40)
        val remote = runCatching {
            musicRepository.search(clean, com.lin.music.core.model.SearchFilter.SONGS).tracks
        }.getOrDefault(emptyList())
        return (localMatches + remote).distinctBy { it.videoId }.take(40)
    }

    private suspend fun resolveContextTracksForAuto(contextTag: String, videoId: String): List<Track> =
        when {
            contextTag == "downloads" -> musicRepository.observeDownloadedTracks().first()
            contextTag == "recent" -> musicRepository.observeHistoryTracks().first()
            contextTag == "songs" -> musicRepository.observeLibraryTracks().first().take(150)
            contextTag == "search" -> autoSearchCache.ifEmpty {
                listOfNotNull(musicRepository.getTrackCachedState(videoId))
            }
            contextTag.startsWith("pl_") -> musicRepository.getPlaylistTracksSnapshot(contextTag.removePrefix("pl_"))
            else -> listOfNotNull(musicRepository.getTrackCachedState(videoId))
        }

    // --- Android Auto / Car Connection Detection ---

    private fun isAutomotiveControllerPackage(pkg: String): Boolean =
        pkg == "com.google.android.projection.gearhead" ||
            pkg == "com.google.android.carassistant" ||
            pkg == "com.android.car.media"

    private fun registerCarConnectionObservers() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                updateCarConnectionState()
            }
        }
        carModeReceiver = receiver
        val filter = IntentFilter().apply {
            addAction(UiModeManager.ACTION_ENTER_CAR_MODE)
            addAction(UiModeManager.ACTION_EXIT_CAR_MODE)
        }
        runCatching {
            ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }

        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                refreshCarProviderConnection()
            }
        }
        carConnectionObserver = obs
        runCatching {
            contentResolver.registerContentObserver(
                Uri.parse("content://androidx.car.app.connection"),
                false,
                obs
            )
        }
        refreshCarProviderConnection()
        updateCarConnectionState()
    }

    private fun refreshCarProviderConnection() {
        serviceScope.launch(Dispatchers.IO) {
            val stateVal = runCatching {
                contentResolver.query(
                    Uri.parse("content://androidx.car.app.connection"),
                    arrayOf("CarConnectionState"),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                } ?: 0
            }.getOrDefault(0)
            withContext(Dispatchers.Main.immediate) {
                carProviderConnected = stateVal > 0
                updateCarConnectionState()
            }
        }
    }

    private fun updateCarConnectionState() {
        val isUiCar = runCatching {
            getSystemService(UiModeManager::class.java)?.currentModeType == Configuration.UI_MODE_TYPE_CAR
        }.getOrDefault(false)
        stateHub.setCarConnected(
            connectedAutoControllers.isNotEmpty() || isUiCar || carProviderConnected
        )
    }

    private suspend fun toggleShuffleFromService() {
        val curSnap = stateHub.queueEngine.snapshot
        val next = !curSnap.shuffleEnabled
        val mode = stateHub.latestPreferences.shuffleMode
        val counts = if (next && mode == ShuffleMode.LEAST_PLAYED) {
            withContext(Dispatchers.IO) { runCatching { musicRepository.getAllPlayCountsSnapshot() }.getOrDefault(emptyMap()) }
        } else {
            emptyMap()
        }
        val newSnap = stateHub.queueEngine.setShuffleWithOrder(next, mode, counts)
        stateHub.publishQueue(newSnap)
        player.shuffleModeEnabled = false
        val newIdx = newSnap.currentIndex
        val curCtrlIdx = player.currentMediaItemIndex
        if (newSnap.items.isNotEmpty() &&
            curCtrlIdx in 0 until player.mediaItemCount &&
            newIdx in newSnap.items.indices &&
            player.getMediaItemAt(curCtrlIdx).mediaId == newSnap.items[newIdx].videoId
        ) {
            if (curCtrlIdx + 1 < player.mediaItemCount) {
                player.removeMediaItems(curCtrlIdx + 1, player.mediaItemCount)
            }
            if (curCtrlIdx > 0) {
                player.removeMediaItems(0, curCtrlIdx)
            }
            if (newIdx > 0) {
                player.addMediaItems(0, newSnap.items.subList(0, newIdx).map { it.toMediaItem() })
            }
            if (newIdx + 1 < newSnap.items.size) {
                player.addMediaItems(newSnap.items.subList(newIdx + 1, newSnap.items.size).map { it.toMediaItem() })
            }
        }
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
