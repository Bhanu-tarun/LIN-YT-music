package com.lin.music.core.playback.queue

import com.lin.music.core.model.AudioQuality
import com.lin.music.core.model.MixChip
import com.lin.music.core.model.RepeatMode
import com.lin.music.core.model.SleepTimerState
import com.lin.music.core.model.StreamInfo
import com.lin.music.core.model.Track
import com.lin.music.core.playback.audio.ReactiveAudioProcessor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackStateHub @Inject constructor(
    val reactiveAudioProcessor: ReactiveAudioProcessor
) {
    val queueEngine = QueueEngine()

    private val _queueSnapshot = MutableStateFlow(QueueSnapshot())
    val queueSnapshot: StateFlow<QueueSnapshot> = _queueSnapshot.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _activeStreamInfo = MutableStateFlow<StreamInfo?>(null)
    val activeStreamInfo: StateFlow<StreamInfo?> = _activeStreamInfo.asStateFlow()

    private val _mixChips = MutableStateFlow<List<MixChip>>(emptyList())
    val mixChips: StateFlow<List<MixChip>> = _mixChips.asStateFlow()

    private val _sleepTimerState = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)
    val sleepTimerState: StateFlow<SleepTimerState> = _sleepTimerState.asStateFlow()

    private val _audioSessionId = MutableStateFlow(0)
    val audioSessionId: StateFlow<Int> = _audioSessionId.asStateFlow()

    private val _isUiForeground = MutableStateFlow(false)
    val isUiForeground: StateFlow<Boolean> = _isUiForeground.asStateFlow()

    private val _isLyricsActive = MutableStateFlow(false)
    val isLyricsActive: StateFlow<Boolean> = _isLyricsActive.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    @Volatile
    var currentQuality: AudioQuality = AudioQuality.HIGH

    @Volatile
    var downloadQuality: AudioQuality = AudioQuality.HIGH

    @Volatile
    var normalizeVolume: Boolean = true

    @Volatile
    var sponsorBlockEnabled: Boolean = false

    @Volatile
    var autoMixContinuation: String? = null

    @Volatile
    var autoMixPlaylistId: String? = null

    @Volatile
    var serviceTimerBridge: ServiceTimerBridge? = null

    fun publishQueue(snapshot: QueueSnapshot) {
        _queueSnapshot.value = snapshot
    }

    fun updatePlaybackState(playing: Boolean, buffering: Boolean) {
        _isPlaying.value = playing
        _isBuffering.value = buffering
    }

    fun updatePosition(position: Long, duration: Long) {
        _positionMs.value = position.coerceAtLeast(0L)
        if (duration > 0L) _durationMs.value = duration
    }

    fun updateStreamInfo(info: StreamInfo?) {
        _activeStreamInfo.value = info
    }

    fun updateMixChips(chips: List<MixChip>) {
        _mixChips.value = chips
    }

    fun updateSleepTimer(state: SleepTimerState) {
        _sleepTimerState.value = state
    }

    fun updateAudioSessionId(sessionId: Int) {
        _audioSessionId.value = sessionId
    }

    fun setUiForeground(foreground: Boolean) {
        _isUiForeground.value = foreground
    }

    fun setLyricsActive(active: Boolean) {
        _isLyricsActive.value = active
    }

    fun emitError(message: String) {
        _errors.tryEmit(message)
    }

    fun updateTrackMetadata(updatedTrack: Track) {
        val cur = queueEngine.snapshot
        val newItems = cur.items.map { if (it.videoId == updatedTrack.videoId) updatedTrack else it }
        if (newItems != cur.items) {
            val nextSnap = cur.copy(items = newItems)
            queueEngine.replaceWithContext(newItems, cur.currentIndex, cur.sourceLabel)
            _queueSnapshot.value = nextSnap
        }
    }

    interface ServiceTimerBridge {
        fun startCountdown(durationMs: Long, fadeOut: Boolean)
        fun startEndOfTrack(fadeOut: Boolean)
        fun extend(extraMs: Long)
        fun cancel()
        fun applyPreferences(normalize: Boolean, skipSilence: Boolean, speed: Float, pitch: Float)
    }
}
