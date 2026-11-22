package com.lin.music.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.size.Size
import com.lin.music.core.data.datastore.AuthStore
import com.lin.music.core.data.datastore.PreferencesStore
import com.lin.music.core.data.repository.MusicRepository
import com.lin.music.core.designsystem.color.AlbumColorExtractor
import com.lin.music.core.designsystem.components.highResArtworkUrl
import com.lin.music.core.innertube.InnerTubeApi
import com.lin.music.core.model.AccountSession
import com.lin.music.core.model.AlbumDetail
import com.lin.music.core.model.AlbumSummary
import com.lin.music.core.model.AmbientMode
import com.lin.music.core.model.AmbientReactivity
import com.lin.music.core.model.ArtistDetail
import com.lin.music.core.model.ArtistRef
import com.lin.music.core.model.AudioQuality
import com.lin.music.core.model.HomeItem
import com.lin.music.core.model.HomeSection
import com.lin.music.core.model.LyricsResult
import com.lin.music.core.model.PlaylistDetail
import com.lin.music.core.model.PlaylistSummary
import com.lin.music.core.model.SearchFilter
import com.lin.music.core.model.SearchResultPage
import com.lin.music.core.model.SpeedDialEntry
import com.lin.music.core.model.Track
import com.lin.music.core.model.TrackArtworkPalette
import com.lin.music.core.model.UserPreferences
import com.lin.music.core.model.speedDialKey
import com.lin.music.core.playback.cache.TrackPrecacher
import com.lin.music.core.playback.download.DownloadController
import com.lin.music.core.playback.download.SmartDownloadManager
import com.lin.music.core.playback.queue.PlayerController
import com.lin.music.core.playback.queue.QueueSnapshot
import com.lin.music.core.playback.queue.QueueUndoToken
import com.lin.music.util.ShareHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface MainTab {
    data object Home : MainTab
    data object Library : MainTab
    data object Playlists : MainTab
}

enum class LibrarySubTab(val label: String) {
    SONGS("songs"),
    ARTISTS("artists"),
    ALBUMS("albums"),
    DOWNLOADS("downloads")
}

enum class LocalDownloadPlaylistKind(val title: String, val subtitle: String) {
    SMART_DOWNLOADS("smart downloads", "auto-downloaded favorites & frequent plays"),
    RECENTLY_PLAYED_DOWNLOADS("recently played downloads", "auto-downloaded recent listening")
}

sealed interface DetailRoute {
    data class Album(val browseId: String) : DetailRoute
    data class Playlist(val browseId: String) : DetailRoute
    data class LocalPlaylist(val kind: LocalDownloadPlaylistKind) : DetailRoute
    data class Artist(val browseId: String) : DetailRoute
    data object Settings : DetailRoute
}

data class LinToastMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

data class EditablePlaylistContext(
    val playlistId: String,
    val playlistTitle: String,
    val onRemoved: ((Track) -> Unit)? = null,
    val onRestored: ((Track) -> Unit)? = null
)

data class TagEditorTarget(
    val itemId: String,
    val itemType: String,
    val title: String
)

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authStore: AuthStore,
    private val preferencesStore: PreferencesStore,
    private val repository: MusicRepository,
    val playerController: PlayerController,
    val downloadController: DownloadController,
    private val smartDownloadManager: SmartDownloadManager,
    private val trackPrecacher: TrackPrecacher,
    private val innerTubeApi: InnerTubeApi
) : ViewModel() {

    private val _sessionInitialized = MutableStateFlow(false)
    val sessionInitialized: StateFlow<Boolean> = _sessionInitialized.asStateFlow()

    val session: StateFlow<AccountSession?> = authStore.sessionFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val preferences: StateFlow<UserPreferences> = preferencesStore.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    val playlists: StateFlow<List<PlaylistSummary>> = repository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val downloadMap: StateFlow<Map<String, Int>> = repository.observeDownloadMap()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    val distinctTags: StateFlow<List<String>> = repository.observeDistinctTags()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val pinnedSpeedDialKeys: StateFlow<Set<String>> = repository.observeSpeedDialPins()
        .map { pins -> pins.map { it.key }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    private val _activeTab = MutableStateFlow<MainTab>(MainTab.Home)
    val activeTab: StateFlow<MainTab> = _activeTab.asStateFlow()

    private val _detailStack = MutableStateFlow<List<DetailRoute>>(emptyList())
    val detailStack: StateFlow<List<DetailRoute>> = _detailStack.asStateFlow()

    private val _isSearchOpen = MutableStateFlow(false)
    val isSearchOpen: StateFlow<Boolean> = _isSearchOpen.asStateFlow()

    private val _isPlayerExpanded = MutableStateFlow(false)
    val isPlayerExpanded: StateFlow<Boolean> = _isPlayerExpanded.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _activeTagFilter = MutableStateFlow<String?>(null)
    val activeTagFilter: StateFlow<String?> = _activeTagFilter.asStateFlow()

    private val _artworkPalette = MutableStateFlow<TrackArtworkPalette?>(null)
    val artworkPalette: StateFlow<TrackArtworkPalette?> = _artworkPalette.asStateFlow()

    private val _toast = MutableStateFlow<LinToastMessage?>(null)
    val toast: StateFlow<LinToastMessage?> = _toast.asStateFlow()

    // Global sheets
    private val _actionSheetTrack = MutableStateFlow<Pair<Track, EditablePlaylistContext?>?>(null)
    val actionSheetTrack: StateFlow<Pair<Track, EditablePlaylistContext?>?> = _actionSheetTrack.asStateFlow()

    private val _addToPlaylistTrack = MutableStateFlow<Track?>(null)
    val addToPlaylistTrack: StateFlow<Track?> = _addToPlaylistTrack.asStateFlow()

    private val _containingPlaylistIds = MutableStateFlow<Set<String>>(emptySet())
    val containingPlaylistIds: StateFlow<Set<String>> = _containingPlaylistIds.asStateFlow()

    private val _showSleepTimerSheet = MutableStateFlow(false)
    val showSleepTimerSheet: StateFlow<Boolean> = _showSleepTimerSheet.asStateFlow()

    private val _artistPickerArtists = MutableStateFlow<List<ArtistRef>?>(null)
    val artistPickerArtists: StateFlow<List<ArtistRef>?> = _artistPickerArtists.asStateFlow()

    private val _tagEditorTarget = MutableStateFlow<TagEditorTarget?>(null)
    val tagEditorTarget: StateFlow<TagEditorTarget?> = _tagEditorTarget.asStateFlow()

    private val _tagEditorTags = MutableStateFlow<List<String>>(emptyList())
    val tagEditorTags: StateFlow<List<String>> = _tagEditorTags.asStateFlow()

    private val _lyricsResult = MutableStateFlow<LyricsResult?>(null)
    val lyricsResult: StateFlow<LyricsResult?> = _lyricsResult.asStateFlow()

    private val _lyricsLoading = MutableStateFlow(false)
    val lyricsLoading: StateFlow<Boolean> = _lyricsLoading.asStateFlow()

    private val _relatedSections = MutableStateFlow<List<HomeSection>>(emptyList())
    val relatedSections: StateFlow<List<HomeSection>> = _relatedSections.asStateFlow()

    private val paletteCache = object : LinkedHashMap<String, TrackArtworkPalette>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TrackArtworkPalette>?): Boolean =
            size > 100
    }
    private val imageLoader get() = SingletonImageLoader.get(context)
    private var toastDismissJob: Job? = null
    private var playlistMembershipJob: Job? = null
    private var tagObserveJob: Job? = null
    private var lyricsJob: Job? = null
    private var relatedJob: Job? = null
    private var paletteJob: Job? = null
    private var adjacentPrewarmJob: Job? = null
    private var lastSyncCompletedMs: Long = 0L
    private var lastPaletteVideoId: String? = null
    private var loadedLyricsVideoId: String? = null
    private var loadedRelatedVideoId: String? = null
    private var lyricsTabOpen: Boolean = false
    private var relatedTabOpen: Boolean = false

    init {
        viewModelScope.launch {
            playerController.queueSnapshot
                .map { it.currentTrack }
                .distinctUntilChanged { a, b -> a?.videoId == b?.videoId }
                .collectLatest { track ->
                    lyricsJob?.cancel()
                    relatedJob?.cancel()
                    paletteJob?.cancel()
                    loadedLyricsVideoId = null
                    loadedRelatedVideoId = null
                    _lyricsResult.value = null
                    _relatedSections.value = emptyList()
                    _lyricsLoading.value = false
                    if (track != null) {
                        if (playerController.isUiForeground.value) {
                            extractPalette(track)
                        }
                        if (lyricsTabOpen) {
                            ensureLyricsLoaded(track)
                        }
                        if (relatedTabOpen) {
                            ensureRelatedLoaded(track)
                        }
                    } else {
                        lastPaletteVideoId = null
                        _artworkPalette.value = null
                    }
                }
        }
        viewModelScope.launch {
            playerController.queueSnapshot
                .distinctUntilChanged { a, b ->
                    a.currentIndex == b.currentIndex &&
                        a.repeatMode == b.repeatMode &&
                        a.shuffleEnabled == b.shuffleEnabled &&
                        a.nextTrackIndexOrNull()?.let { a.items.getOrNull(it)?.videoId } ==
                        b.nextTrackIndexOrNull()?.let { b.items.getOrNull(it)?.videoId } &&
                        a.previousTrackIndexOrNull()?.let { a.items.getOrNull(it)?.videoId } ==
                        b.previousTrackIndexOrNull()?.let { b.items.getOrNull(it)?.videoId }
                }
                .collectLatest { snap ->
                    scheduleAdjacentPrewarm(snap)
                }
        }
        viewModelScope.launch {
            playerController.errors.collect { err -> showToast(err) }
        }
        viewModelScope.launch {
            authStore.sessionFlow
                .onEach { _sessionInitialized.value = true }
                .map { it?.takeIf { s -> s.isValid }?.sapisid }
                .distinctUntilChanged()
                .collectLatest { validSapisid ->
                    if (validSapisid != null) {
                        syncAccount(force = false)
                    }
                }
        }
    }

    fun setUiForeground(foreground: Boolean) {
        playerController.setUiForeground(foreground)
        if (foreground) {
            val snap = playerController.queueSnapshot.value
            val track = snap.currentTrack
            if (track != null && track.videoId != lastPaletteVideoId) {
                paletteJob?.cancel()
                paletteJob = viewModelScope.launch { extractPalette(track) }
            }
            scheduleAdjacentPrewarm(snap)
        } else {
            adjacentPrewarmJob?.cancel()
        }
    }

    private fun scheduleAdjacentPrewarm(snapshot: QueueSnapshot) {
        if (!playerController.isUiForeground.value) return
        adjacentPrewarmJob?.cancel()
        val currentId = snapshot.currentTrack?.videoId
        val next1 = snapshot.nextTrackIndexOrNull()?.let { snapshot.items.getOrNull(it) }
        val prev1 = snapshot.previousTrackIndexOrNull()?.let { snapshot.items.getOrNull(it) }
        val next2 = if (!snapshot.shuffleEnabled && snapshot.items.size > 1) {
            val rawNext2 = snapshot.currentIndex + 2
            val idx2 = when {
                rawNext2 in snapshot.items.indices -> rawNext2
                snapshot.repeatMode == com.lin.music.core.model.RepeatMode.ALL ->
                    (rawNext2 % snapshot.items.size)
                else -> -1
            }
            snapshot.items.getOrNull(idx2)
        } else null

        val targets = listOfNotNull(next1, prev1, next2)
            .filter { it.videoId.isNotBlank() && it.videoId != currentId }
            .distinctBy { it.videoId }
        if (targets.isEmpty()) return

        val paletteTargets = listOfNotNull(next1, prev1)
            .map { it.videoId }
            .toSet()

        adjacentPrewarmJob = viewModelScope.launch {
            delay(150L)
            targets.forEach { target ->
                val hiResUrl = highResArtworkUrl(target.thumbnailUrl) ?: return@forEach
                val req = ImageRequest.Builder(context)
                    .data(hiResUrl)
                    .size(Size.ORIGINAL)
                    .precision(Precision.EXACT)
                    .build()
                imageLoader.enqueue(req)

                val needPalette = target.videoId in paletteTargets &&
                    synchronized(paletteCache) { paletteCache[hiResUrl] == null }
                if (needPalette) {
                    val extracted = withContext(Dispatchers.IO) {
                        runCatching {
                            val pReq = ImageRequest.Builder(context)
                                .data(hiResUrl)
                                .allowHardware(false)
                                .size(96)
                                .build()
                            val res = imageLoader.execute(pReq)
                            val bmp = (res as? SuccessResult)?.image?.let { (it as? BitmapImage)?.bitmap }
                            bmp?.let { AlbumColorExtractor.extractFromBitmap(it) }
                        }.getOrNull()
                    }
                    if (extracted != null) {
                        synchronized(paletteCache) { paletteCache[hiResUrl] = extracted }
                    }
                }
            }
        }
    }

    fun onNowPlayingDockChanged(isExpanded: Boolean, isLyricsTab: Boolean, isRelatedTab: Boolean) {
        lyricsTabOpen = isExpanded && isLyricsTab
        relatedTabOpen = isExpanded && isRelatedTab
        playerController.setLyricsActive(lyricsTabOpen)
        val track = playerController.queueSnapshot.value.currentTrack ?: return
        if (lyricsTabOpen) ensureLyricsLoaded(track)
        if (relatedTabOpen) ensureRelatedLoaded(track)
    }

    private suspend fun extractPalette(track: Track) {
        val resolvedUrl = highResArtworkUrl(track.thumbnailUrl)
        if (resolvedUrl.isNullOrBlank()) {
            _artworkPalette.value = null
            lastPaletteVideoId = track.videoId
            return
        }
        synchronized(paletteCache) { paletteCache[resolvedUrl] }?.let {
            _artworkPalette.value = it
            lastPaletteVideoId = track.videoId
            return
        }
        val extracted = withContext(Dispatchers.IO) {
            runCatching {
                val req = ImageRequest.Builder(context)
                    .data(resolvedUrl)
                    .allowHardware(false)
                    .size(96)
                    .build()
                val res = imageLoader.execute(req)
                val bmp = (res as? SuccessResult)?.image?.let { (it as? BitmapImage)?.bitmap }
                bmp?.let { AlbumColorExtractor.extractFromBitmap(it) }
            }.getOrNull()
        }
        if (extracted != null) {
            synchronized(paletteCache) { paletteCache[resolvedUrl] = extracted }
            _artworkPalette.value = extracted
        } else {
            _artworkPalette.value = null
        }
        lastPaletteVideoId = track.videoId
    }

    fun prefetchTrack(track: Track) {
        playerController.prefetchTrack(track)
    }

    private fun ensureLyricsLoaded(track: Track) {
        if (loadedLyricsVideoId == track.videoId && (_lyricsResult.value != null || _lyricsLoading.value)) return
        if (lyricsJob?.isActive == true && loadedLyricsVideoId == track.videoId) return
        lyricsJob?.cancel()
        loadedLyricsVideoId = track.videoId
        lyricsJob = viewModelScope.launch {
            _lyricsLoading.value = true
            _lyricsResult.value = runCatching { repository.getLyrics(track) }.getOrNull()
            _lyricsLoading.value = false
        }
    }

    private fun ensureRelatedLoaded(track: Track) {
        if (loadedRelatedVideoId == track.videoId && _relatedSections.value.isNotEmpty()) return
        if (relatedJob?.isActive == true && loadedRelatedVideoId == track.videoId) return
        relatedJob?.cancel()
        loadedRelatedVideoId = track.videoId
        relatedJob = viewModelScope.launch {
            runCatching {
                val next = repository.getNext(videoId = track.videoId)
                val relatedId = next.relatedBrowseId
                _relatedSections.value = if (!relatedId.isNullOrBlank()) {
                    repository.getRelated(relatedId).sections
                } else {
                    emptyList()
                }
            }
        }
    }

    fun selectTab(tab: MainTab) {
        _activeTab.value = tab
        _detailStack.value = emptyList()
    }

    fun openDetail(route: DetailRoute) {
        _isSearchOpen.value = false
        _isPlayerExpanded.value = false
        _detailStack.value = _detailStack.value + route
    }

    fun popDetail(): Boolean {
        val current = _detailStack.value
        if (current.isEmpty()) return false
        _detailStack.value = current.dropLast(1)
        return true
    }

    fun setSearchOpen(open: Boolean) {
        _isSearchOpen.value = open
    }

    fun setPlayerExpanded(expanded: Boolean) {
        _isPlayerExpanded.value = expanded
        if (!expanded) {
            lyricsTabOpen = false
            relatedTabOpen = false
            playerController.setLyricsActive(false)
        }
    }

    fun setActiveTagFilter(tag: String?) {
        _activeTagFilter.value = if (_activeTagFilter.value == tag) null else tag
    }

    fun toggleTheme() {
        viewModelScope.launch { preferencesStore.toggleTheme() }
    }

    fun syncAccount(force: Boolean = true) {
        if (_isSyncing.value) return
        val now = System.currentTimeMillis()
        if (!force && lastSyncCompletedMs > 0L && (now - lastSyncCompletedMs) < SYNC_COOLDOWN_MS) {
            return
        }
        viewModelScope.launch {
            _isSyncing.value = true
            repository.syncAccountAndLibrary()
                .onSuccess { lastSyncCompletedMs = System.currentTimeMillis() }
                .onFailure { showToast("sync error: ${it.message ?: "check login"}") }
            val prefs = preferences.value
            if (prefs.smartDownloadsEnabled || prefs.recentlyPlayedDownloadsEnabled) {
                withContext(Dispatchers.IO) {
                    runCatching { smartDownloadManager.syncAll(force = false) }
                }
            }
            _isSyncing.value = false
        }
    }

    fun toggleSpeedDialPin(item: HomeItem) {
        viewModelScope.launch {
            val pinned = repository.toggleSpeedDialPin(item)
            showToast(if (pinned) "pinned to speed dial" else "unpinned from speed dial")
        }
    }

    fun clearStreamCache() {
        viewModelScope.launch {
            val freed = trackPrecacher.clearNonDownloadedStreamCache()
            showToast("cleared ${freed / (1024L * 1024L)} mb stream cache")
        }
    }

    fun deleteAllDownloads() {
        downloadController.removeAllDownloads()
        showToast("removed all downloads")
    }

    fun syncSmartDownloadsNow() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { smartDownloadManager.syncAll() }
        }
    }

    fun onWebViewLoggedIn(session: AccountSession) {
        viewModelScope.launch {
            runCatching {
                authStore.saveSession(session)
            }.onSuccess {
                showToast("signed in to youtube music")
                syncAccount(force = true)
            }.onFailure { err ->
                showToast("sign in storage failed: ${err.message ?: "unknown error"}")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authStore.clearSession()
            _detailStack.value = emptyList()
            showToast("signed out")
        }
    }

    // --- Non-Negotiable Swipe & Queue Actions ---

    fun playNext(track: Track) {
        val undo: QueueUndoToken = playerController.playNext(track)
        showToast(
            text = undo.description,
            actionLabel = "undo",
            onAction = { playerController.undoQueueChange(undo) }
        )
    }

    fun addToQueue(track: Track) {
        val undo: QueueUndoToken = playerController.addToQueue(track)
        showToast(
            text = undo.description,
            actionLabel = "undo",
            onAction = { playerController.undoQueueChange(undo) }
        )
    }

    fun playPlaylistNext(playlist: PlaylistSummary) {
        viewModelScope.launch {
            runCatching { repository.getPlaylist(playlist.browseId) }.onSuccess { detail ->
                if (detail.tracks.isNotEmpty()) {
                    val undo = playerController.playNextAll(detail.tracks, playlist.title)
                    showToast(undo.description, "undo") { playerController.undoQueueChange(undo) }
                }
            }.onFailure { showToast("failed to load ${playlist.title}") }
        }
    }

    fun addPlaylistToQueue(playlist: PlaylistSummary) {
        viewModelScope.launch {
            runCatching { repository.getPlaylist(playlist.browseId) }.onSuccess { detail ->
                if (detail.tracks.isNotEmpty()) {
                    val undo = playerController.addAllToQueue(detail.tracks, playlist.title)
                    showToast(undo.description, "undo") { playerController.undoQueueChange(undo) }
                }
            }.onFailure { showToast("failed to load ${playlist.title}") }
        }
    }

    fun startMixForTrack(track: Track) {
        _isPlayerExpanded.value = true
        playerController.startMix(
            seedVideoId = track.videoId,
            playlistId = "RDAMVM${track.videoId}",
            sourceLabel = "mix · ${track.title.lowercase()}"
        )
        showToast("started mix from ${track.title}")
    }

    fun startMixForPlaylist(playlistId: String, title: String) {
        val clean = playlistId.removePrefix("VL")
        val radioId = if (clean.startsWith("RD")) clean else "RDAMPL$clean"
        _isPlayerExpanded.value = true
        playerController.startMix(playlistId = radioId, sourceLabel = "mix · ${title.lowercase()}")
    }

    // --- Track Actions (Like, Library, Playlist, Go To Album/Artist, Download, Tags, Share) ---

    fun openTrackActions(track: Track, editableContext: EditablePlaylistContext? = null) {
        _actionSheetTrack.value = track to editableContext
    }

    fun dismissTrackActions() {
        _actionSheetTrack.value = null
    }

    fun toggleLike(track: Track) {
        viewModelScope.launch {
            repository.toggleLike(track).onSuccess { liked ->
                val updated = track.copy(isLiked = liked)
                playerController.updateTrackMetadata(updated)
                showToast(if (liked) "liked — ${track.title}" else "removed like — ${track.title}")
            }.onFailure {
                showToast("like failed: ${it.message}")
            }
        }
    }

    fun toggleLibrary(track: Track) {
        viewModelScope.launch {
            repository.toggleLibrary(track).onSuccess { inLib ->
                val updated = track.copy(inLibrary = inLib)
                playerController.updateTrackMetadata(updated)
                showToast(if (inLib) "added to library — ${track.title}" else "deleted from library — ${track.title}")
            }.onFailure {
                showToast("library update failed: ${it.message}")
            }
        }
    }

    fun openAddToPlaylist(track: Track) {
        _addToPlaylistTrack.value = track
        playlistMembershipJob?.cancel()
        playlistMembershipJob = viewModelScope.launch {
            repository.observePlaylistIdsContaining(track.videoId).collectLatest {
                _containingPlaylistIds.value = it
            }
        }
    }

    fun dismissAddToPlaylist() {
        playlistMembershipJob?.cancel()
        _addToPlaylistTrack.value = null
    }

    fun togglePlaylistMembership(playlist: PlaylistSummary, track: Track, shouldAdd: Boolean) {
        viewModelScope.launch {
            if (shouldAdd) {
                repository.addTrackToPlaylist(playlist.id, track)
                    .onSuccess { showToast("added to ${playlist.title}") }
                    .onFailure { showToast("failed: ${it.message}") }
            } else {
                repository.removeTrackFromPlaylist(playlist.id, track)
                    .onSuccess { showToast("removed from ${playlist.title}") }
                    .onFailure { showToast("failed: ${it.message}") }
            }
        }
    }

    fun deleteTrackFromEditablePlaylist(track: Track, ctx: EditablePlaylistContext) {
        viewModelScope.launch {
            ctx.onRemoved?.invoke(track)
            repository.removeTrackFromPlaylist(ctx.playlistId, track)
                .onSuccess {
                    showToast(
                        text = "removed from ${ctx.playlistTitle}",
                        actionLabel = "undo",
                        onAction = {
                            viewModelScope.launch {
                                repository.addTrackToPlaylist(ctx.playlistId, track)
                                ctx.onRestored?.invoke(track)
                            }
                        }
                    )
                }
                .onFailure {
                    ctx.onRestored?.invoke(track)
                    showToast("remove failed: ${it.message}")
                }
        }
    }

    fun createPlaylist(title: String, description: String, privacy: String, initialTrack: Track? = null) {
        viewModelScope.launch {
            repository.createPlaylist(title, description, privacy).onSuccess { newId ->
                if (initialTrack != null) {
                    repository.addTrackToPlaylist(newId, initialTrack)
                }
                showToast("created playlist — $title")
                syncAccount(force = true)
            }.onFailure { showToast("create playlist failed: ${it.message}") }
        }
    }

    fun goToAlbum(track: Track) {
        viewModelScope.launch {
            val albumId = repository.resolveAlbumId(track)
            if (!albumId.isNullOrBlank()) {
                openDetail(DetailRoute.Album(albumId))
            } else {
                showToast("album unavailable for this track")
            }
        }
    }

    fun goToArtist(track: Track) {
        val withIds = track.artists.filter { !it.id.isNullOrBlank() }
        when {
            withIds.size == 1 -> openDetail(DetailRoute.Artist(withIds.first().id!!))
            withIds.size > 1 -> _artistPickerArtists.value = withIds
            else -> {
                // Fallback: search artist name via InnerTube
                val firstArtist = track.artists.firstOrNull()?.name ?: return
                viewModelScope.launch {
                    val res = runCatching { innerTubeApi.search(firstArtist, SearchFilter.ARTISTS) }.getOrNull()
                    val artistId = res?.artists?.firstOrNull()?.browseId
                    if (artistId != null) {
                        openDetail(DetailRoute.Artist(artistId))
                    } else {
                        showToast("artist page unavailable")
                    }
                }
            }
        }
    }

    fun dismissArtistPicker() {
        _artistPickerArtists.value = null
    }

    fun toggleDownload(track: Track) {
        val state = downloadMap.value[track.videoId]
        if (state == 0 || state == 1 || state == 2) {
            downloadController.removeDownload(track.videoId)
            showToast("removed download — ${track.title}")
        } else {
            downloadController.downloadTrack(track)
            showToast("downloading — ${track.title}")
        }
    }

    fun downloadAlbum(album: AlbumDetail) {
        viewModelScope.launch { repository.cacheAlbumForOffline(album) }
        downloadController.downloadAll(album.tracks)
    }

    fun downloadPlaylist(playlist: PlaylistDetail) {
        viewModelScope.launch { repository.cachePlaylistForOffline(playlist) }
        downloadController.downloadAll(playlist.tracks)
    }

    fun openSleepTimerSheet() {
        _showSleepTimerSheet.value = true
    }

    fun dismissSleepTimerSheet() {
        _showSleepTimerSheet.value = false
    }

    fun openTagEditor(target: TagEditorTarget) {
        _tagEditorTarget.value = target
        tagObserveJob?.cancel()
        tagObserveJob = viewModelScope.launch {
            repository.observeTagsForItem(target.itemId, target.itemType).collectLatest {
                _tagEditorTags.value = it
            }
        }
    }

    fun dismissTagEditor() {
        tagObserveJob?.cancel()
        _tagEditorTarget.value = null
    }

    fun addTag(target: TagEditorTarget, rawTag: String) {
        viewModelScope.launch { repository.addTag(target.itemId, target.itemType, rawTag) }
    }

    fun removeTag(target: TagEditorTarget, tag: String) {
        viewModelScope.launch { repository.removeTag(target.itemId, target.itemType, tag) }
    }

    fun shareTrack(track: Track, timestampSec: Long? = null) {
        ShareHelper.shareTrack(context, track, timestampSec)
    }

    fun saveQueueAsPlaylist(title: String) {
        val tracks = playerController.queueSnapshot.value.items
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            repository.createPlaylist(title, "Saved from LIN queue", "PRIVATE").onSuccess { id ->
                tracks.forEach { repository.addTrackToPlaylist(id, it) }
                showToast("saved queue to \"$title\"")
                syncAccount(force = true)
            }.onFailure { showToast("failed: ${it.message}") }
        }
    }

    fun handleDeepLink(uri: Uri) {
        val videoId = uri.getQueryParameter("v")
            ?: if (uri.host == "youtu.be") uri.pathSegments.firstOrNull() else null
        val listId = uri.getQueryParameter("list")
        val path = uri.pathSegments

        when {
            !videoId.isNullOrBlank() -> {
                viewModelScope.launch {
                    val next = runCatching { repository.getNext(videoId = videoId, playlistId = listId) }.getOrNull()
                    val tracks = next?.tracks.orEmpty()
                    if (tracks.isNotEmpty()) {
                        playerController.playContext(tracks, 0, "shared link")
                        _isPlayerExpanded.value = true
                    } else {
                        playerController.playContext(listOf(Track(videoId = videoId, title = "Shared Track")), 0, "shared link")
                        _isPlayerExpanded.value = true
                    }
                }
            }
            !listId.isNullOrBlank() -> openDetail(DetailRoute.Playlist("VL$listId"))
            path.firstOrNull() == "channel" && path.size >= 2 -> openDetail(DetailRoute.Artist(path[1]))
            path.firstOrNull() == "browse" && path.size >= 2 -> {
                val id = path[1]
                when {
                    id.startsWith("MPREb_") -> openDetail(DetailRoute.Album(id))
                    id.startsWith("UC") -> openDetail(DetailRoute.Artist(id))
                    else -> openDetail(DetailRoute.Playlist(id))
                }
            }
        }
    }

    fun showToast(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        toastDismissJob?.cancel()
        _toast.value = LinToastMessage(text = text, actionLabel = actionLabel, onAction = onAction)
        toastDismissJob = viewModelScope.launch {
            delay(4800L)
            _toast.value = null
        }
    }

    fun dismissToast() {
        toastDismissJob?.cancel()
        _toast.value = null
    }

    private companion object {
        const val SYNC_COOLDOWN_MS = 5 * 60_000L
    }
}

data class HomeUiState(
    val speedDialItems: List<SpeedDialEntry> = emptyList(),
    val userPlaylists: List<PlaylistSummary> = emptyList(),
    val quickPicks: List<Track> = emptyList(),
    val personalMixSections: List<HomeSection> = emptyList(),
    val communityTasteSections: List<HomeSection> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val isEmpty: Boolean
        get() = speedDialItems.isEmpty() &&
            userPlaylists.isEmpty() &&
            quickPicks.isEmpty() &&
            personalMixSections.isEmpty() &&
            communityTasteSections.isEmpty()
}

private data class HomeFeedInputs(
    val sections: List<HomeSection>,
    val playlists: List<PlaylistSummary>,
    val localSpeedDial: List<Track>,
    val pinnedEntries: List<SpeedDialEntry>
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MusicRepository
) : ViewModel() {
    private val rawHomeSections = MutableStateFlow<List<HomeSection>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private var continuation: String? = null

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            rawHomeSections,
            repository.observePlaylists(),
            repository.observeSpeedDialLocalTracks(),
            repository.observeSpeedDialPins(),
            ::HomeFeedInputs
        ),
        repository.observeHistoryTracks(),
        _isLoading,
        _error
    ) { feed, history, loading, err ->
        buildOrganizedHomeState(
            sections = feed.sections,
            userPlaylists = feed.playlists,
            localSpeedDial = feed.localSpeedDial,
            pinnedEntries = feed.pinnedEntries,
            historyTracks = history,
            loading = loading,
            error = err
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState(isLoading = true))

    init {
        viewModelScope.launch {
            repository.sessionFlow
                .map { it?.isValid == true }
                .distinctUntilChanged()
                .collectLatest { valid ->
                    if (valid) refresh(includeLibrary = false)
                }
        }
    }

    fun refresh(includeLibrary: Boolean = true) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            if (includeLibrary) {
                coroutineScope {
                    val homeJob = async { runCatching { repository.getHome() } }
                    val plJob = async { repository.refreshLibraryPlaylists() }
                    val histJob = async { repository.refreshHistory() }
                    plJob.await()
                    histJob.await()
                    homeJob.await()
                        .onSuccess { page ->
                            rawHomeSections.value = page.sections
                            continuation = page.continuation
                        }
                        .onFailure { _error.value = it.message ?: "Failed to load home feed" }
                }
            } else {
                runCatching { repository.getHome() }
                    .onSuccess { page ->
                        rawHomeSections.value = page.sections
                        continuation = page.continuation
                    }
                    .onFailure { _error.value = it.message ?: "Failed to load home feed" }
            }
            _isLoading.value = false
        }
    }

    fun loadMore() {
        val token = continuation ?: return
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            runCatching { repository.getHome(token) }.onSuccess { page ->
                rawHomeSections.value = rawHomeSections.value + page.sections
                continuation = page.continuation
            }
            _isLoading.value = false
        }
    }

    private fun buildOrganizedHomeState(
        sections: List<HomeSection>,
        userPlaylists: List<PlaylistSummary>,
        localSpeedDial: List<Track>,
        pinnedEntries: List<SpeedDialEntry>,
        historyTracks: List<Track>,
        loading: Boolean,
        error: String?
    ): HomeUiState {
        val ytmSpeedDial = mutableListOf<HomeItem>()
        val quickPicks = mutableListOf<Track>()
        val personal = mutableListOf<HomeSection>()
        val communityTaste = mutableListOf<HomeSection>()

        val excludedKeywords = listOf(
            "trending", "chart", "top songs", "top music video", "global top",
            "most popular", "moods and genres", "moods & genres", "podcasts", "episodes"
        )
        val personalKeywords = listOf(
            "mix", "listen again", "replay", "supermix", "discover", "forgotten",
            "favorite", "favourite", "from your library", "recommended", "new release",
            "albums for you", "your ", "picked for you", "radio", "daily", "archive", "artists you"
        )
        val communityKeywords = listOf(
            "community", "playlist", "similar to", "based on", "inspired by"
        )

        sections.forEach { sec ->
            val lower = sec.title.lowercase()
            if (excludedKeywords.any { lower.contains(it) }) return@forEach

            when {
                lower.contains("speed dial") -> {
                    ytmSpeedDial += sec.items
                }
                lower.contains("quick pick") || sec.isQuickPicks -> {
                    quickPicks += sec.items.mapNotNull { (it as? HomeItem.SongItem)?.track }
                }
                personalKeywords.any { lower.contains(it) } -> {
                    personal += sec
                }
                communityKeywords.any { lower.contains(it) } ||
                    sec.items.all { it is HomeItem.PlaylistItem } -> {
                    communityTaste += sec
                }
                else -> {
                    personal += sec
                }
            }
        }

        val listenAgainItems = personal
            .firstOrNull { it.title.contains("listen again", ignoreCase = true) }
            ?.items
            .orEmpty()

        val speedDialItems = buildSpeedDialEntries(
            pinnedEntries = pinnedEntries,
            localPlayedTracks = localSpeedDial,
            ytmSpeedDialItems = ytmSpeedDial,
            listenAgainItems = listenAgainItems,
            historyTracks = historyTracks
        )

        val sortedPlaylists = userPlaylists.sortedByDescending { it.id == "LM" }

        return HomeUiState(
            speedDialItems = speedDialItems,
            userPlaylists = sortedPlaylists,
            quickPicks = quickPicks.distinctBy { it.videoId }.take(16),
            personalMixSections = personal,
            communityTasteSections = communityTaste,
            isLoading = loading,
            error = error
        )
    }

    companion object {
        const val MAX_SPEED_DIAL_ITEMS = 27

        fun buildSpeedDialEntries(
            pinnedEntries: List<SpeedDialEntry>,
            localPlayedTracks: List<Track>,
            ytmSpeedDialItems: List<HomeItem>,
            listenAgainItems: List<HomeItem>,
            historyTracks: List<Track>,
            maxItems: Int = MAX_SPEED_DIAL_ITEMS
        ): List<SpeedDialEntry> {
            val result = mutableListOf<SpeedDialEntry>()
            val seen = mutableSetOf<String>()

            fun append(item: HomeItem, isPinned: Boolean) {
                if (result.size >= maxItems) return
                val key = item.speedDialKey()
                if (seen.add(key)) {
                    result += SpeedDialEntry(key = key, item = item, isPinned = isPinned)
                }
            }

            pinnedEntries.forEach { append(it.item, isPinned = true) }

            val localItems = localPlayedTracks.map { HomeItem.SongItem(it) }
            val maxPairCount = maxOf(ytmSpeedDialItems.size, localItems.size)
            for (i in 0 until maxPairCount) {
                ytmSpeedDialItems.getOrNull(i)?.let { append(it, isPinned = false) }
                localItems.getOrNull(i)?.let { append(it, isPinned = false) }
            }

            listenAgainItems.forEach { append(it, isPinned = false) }
            historyTracks.forEach { append(HomeItem.SongItem(it), isPinned = false) }

            return result
        }
    }
}

enum class SongsSubFilter(val label: String) {
    LIBRARY("library"),
    LIKED("liked"),
    HISTORY("history")
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: MusicRepository
) : ViewModel() {
    val libraryTracks: StateFlow<List<Track>> = repository.observeLibraryTracks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val likedTracks: StateFlow<List<Track>> = repository.observeLikedTracks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val downloadedTracks: StateFlow<List<Track>> = repository.observeDownloadedTracks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val smartDownloadedTracks: StateFlow<List<Track>> = repository.observeSmartDownloadedTracks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val recentlyPlayedDownloadedTracks: StateFlow<List<Track>> = repository.observeRecentlyPlayedDownloadedTracks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val historyTracks: StateFlow<List<Track>> = repository.observeHistoryTracks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val albums: StateFlow<List<AlbumSummary>> = repository.observeLibraryAlbums()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val downloadedAlbums: StateFlow<List<AlbumSummary>> = repository.observeDownloadedAlbums()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val artists = repository.observeSubscribedArtists()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val playlists: StateFlow<List<PlaylistSummary>> = repository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    private val lastTabRefreshMs = mutableMapOf<String, Long>()

    fun refreshForTab(
        tab: MainTab,
        librarySubTab: LibrarySubTab,
        subFilter: SongsSubFilter,
        force: Boolean = false
    ) {
        if (tab == MainTab.Home || (tab == MainTab.Library && librarySubTab == LibrarySubTab.DOWNLOADS)) {
            return
        }
        val key = "${tab}_${librarySubTab}_${subFilter}"
        val hasLocalData = when (tab) {
            MainTab.Home -> true
            MainTab.Library -> when (librarySubTab) {
                LibrarySubTab.SONGS -> when (subFilter) {
                    SongsSubFilter.LIBRARY -> libraryTracks.value.isNotEmpty()
                    SongsSubFilter.LIKED -> likedTracks.value.isNotEmpty()
                    SongsSubFilter.HISTORY -> historyTracks.value.isNotEmpty()
                }
                LibrarySubTab.ARTISTS -> artists.value.isNotEmpty()
                LibrarySubTab.ALBUMS -> albums.value.isNotEmpty()
                LibrarySubTab.DOWNLOADS -> true
            }
            MainTab.Playlists -> playlists.value.isNotEmpty()
        }
        val lastRefresh = maxOf(lastTabRefreshMs[key] ?: 0L, repository.lastFullSyncAtMs)
        if (!force && hasLocalData && (System.currentTimeMillis() - lastRefresh) < TAB_REFRESH_TTL_MS) {
            return
        }
        viewModelScope.launch {
            _isRefreshing.value = true
            _syncError.value = null
            val res: Result<*> = when (tab) {
                MainTab.Home -> Result.success(Unit)
                MainTab.Library -> when (librarySubTab) {
                    LibrarySubTab.SONGS -> when (subFilter) {
                        SongsSubFilter.LIBRARY -> repository.refreshLibrarySongs()
                        SongsSubFilter.LIKED -> repository.refreshLikedSongs()
                        SongsSubFilter.HISTORY -> repository.refreshHistory()
                    }
                    LibrarySubTab.ARTISTS -> repository.refreshLibraryArtists()
                    LibrarySubTab.ALBUMS -> repository.refreshLibraryAlbums()
                    LibrarySubTab.DOWNLOADS -> Result.success(Unit)
                }
                MainTab.Playlists -> repository.refreshLibraryPlaylists()
            }
            res.onSuccess { lastTabRefreshMs[key] = System.currentTimeMillis() }
            res.onFailure { _syncError.value = it.message ?: "Failed to fetch library" }
            _isRefreshing.value = false
        }
    }

    private companion object {
        const val TAB_REFRESH_TTL_MS = 5 * 60_000L
    }
}

enum class SortOption(val label: String) {
    CUSTOM("custom"),
    TITLE("title"),
    ARTIST("artist")
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: MusicRepository
) : ViewModel() {
    private val _album = MutableStateFlow<AlbumDetail?>(null)
    val album: StateFlow<AlbumDetail?> = _album.asStateFlow()

    private val _playlist = MutableStateFlow<PlaylistDetail?>(null)
    val playlist: StateFlow<PlaylistDetail?> = _playlist.asStateFlow()

    private val _artist = MutableStateFlow<ArtistDetail?>(null)
    val artist: StateFlow<ArtistDetail?> = _artist.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadAlbum(browseId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching { repository.getAlbum(browseId) }
                .onSuccess { _album.value = it }
                .onFailure { _error.value = it.message }
            _isLoading.value = false
        }
    }

    fun loadPlaylist(browseId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching { repository.getPlaylist(browseId) }
                .onSuccess { _playlist.value = it }
                .onFailure { _error.value = it.message }
            _isLoading.value = false
        }
    }

    fun loadArtist(browseId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching { repository.getArtist(browseId) }
                .onSuccess { _artist.value = it }
                .onFailure { _error.value = it.message }
            _isLoading.value = false
        }
    }

    fun toggleAlbumLibrary() {
        val current = _album.value ?: return
        viewModelScope.launch {
            repository.toggleAlbumLibrary(current).onSuccess { inLib ->
                _album.value = current.copy(inLibrary = inLib)
            }
        }
    }

    fun toggleArtistSubscribe() {
        val current = _artist.value ?: return
        viewModelScope.launch {
            repository.toggleArtistSubscription(current).onSuccess { sub ->
                _artist.value = current.copy(isSubscribed = sub)
            }
        }
    }

    fun updatePlaylistMeta(title: String, desc: String) {
        val current = _playlist.value ?: return
        viewModelScope.launch {
            repository.updatePlaylist(current.id, title, desc).onSuccess {
                _playlist.value = current.copy(title = title, description = desc)
            }
        }
    }

    fun deletePlaylist(onDeleted: () -> Unit) {
        val current = _playlist.value ?: return
        viewModelScope.launch {
            repository.deletePlaylist(current.id).onSuccess { onDeleted() }
        }
    }

    fun togglePlaylistLibrary(onMessage: (String) -> Unit) {
        val current = _playlist.value ?: return
        viewModelScope.launch {
            repository.togglePlaylistLibrary(current)
                .onSuccess { inLib ->
                    _playlist.value = current.copy(inLibrary = inLib)
                    onMessage(if (inLib) "saved to your library" else "removed from library")
                }
                .onFailure { onMessage("library update failed: ${it.message}") }
        }
    }

    fun clonePlaylistToOwned(onCloned: (String) -> Unit, onError: (String) -> Unit) {
        val current = _playlist.value ?: return
        viewModelScope.launch {
            repository.clonePlaylistToOwned(current)
                .onSuccess { newId -> onCloned(newId) }
                .onFailure { onError("copy failed: ${it.message}") }
        }
    }

    fun removePlaylistTrackLocal(track: Track) {
        val current = _playlist.value ?: return
        _playlist.value = current.copy(tracks = current.tracks.filterNot { it.videoId == track.videoId && it.setVideoId == track.setVideoId })
    }

    fun restorePlaylistTrackLocal(track: Track) {
        val current = _playlist.value ?: return
        _playlist.value = current.copy(tracks = current.tracks + track)
    }

    fun addTrackToCurrentPlaylist(track: Track) {
        val current = _playlist.value ?: return
        viewModelScope.launch {
            repository.addTrackToPlaylist(current.id, track).onSuccess {
                loadPlaylist(current.browseId)
            }
        }
    }

    fun moveTrackUpOrDown(index: Int, delta: Int) {
        val current = _playlist.value ?: return
        val targetIdx = index + delta
        if (targetIdx !in current.tracks.indices) return
        val list = current.tracks.toMutableList()
        val moving = list.removeAt(index)
        list.add(targetIdx, moving)
        _playlist.value = current.copy(tracks = list)

        val movingSetId = moving.setVideoId ?: return
        val successorSetId = list.getOrNull(targetIdx + 1)?.setVideoId
        viewModelScope.launch {
            repository.moveTrackInPlaylist(current.id, movingSetId, successorSetId)
        }
    }
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: MusicRepository
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(SearchFilter.ALL)
    val filter: StateFlow<SearchFilter> = _filter.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private val _results = MutableStateFlow(SearchResultPage())
    val results: StateFlow<SearchResultPage> = _results.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val searchHistory = repository.observeSearchHistory()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val libraryTracks = repository.observeLibraryTracks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private var suggestionJob: Job? = null
    private var searchJob: Job? = null

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
        suggestionJob?.cancel()
        if (newQuery.isBlank()) {
            _suggestions.value = emptyList()
            _results.value = SearchResultPage()
            return
        }
        if (_filter.value == SearchFilter.LIBRARY) {
            filterLibraryLocal(newQuery)
            return
        }
        suggestionJob = viewModelScope.launch {
            delay(220L)
            _suggestions.value = runCatching { repository.getSearchSuggestions(newQuery) }.getOrDefault(emptyList())
        }
    }

    fun selectFilter(newFilter: SearchFilter) {
        _filter.value = newFilter
        if (_query.value.isNotBlank()) submitSearch(_query.value)
    }

    fun submitSearch(q: String = _query.value) {
        val trimmed = q.trim()
        if (trimmed.isBlank()) return
        _query.value = trimmed
        _suggestions.value = emptyList()

        if (_filter.value == SearchFilter.LIBRARY) {
            filterLibraryLocal(trimmed)
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isLoading.value = true
            runCatching { repository.search(trimmed, _filter.value) }
                .onSuccess { _results.value = it }
            _isLoading.value = false
        }
    }

    private fun filterLibraryLocal(q: String) {
        val lower = q.lowercase()
        val matched = libraryTracks.value.filter { t ->
            t.title.lowercase().contains(lower) ||
                t.artistDisplay.lowercase().contains(lower) ||
                t.tags.any { it.contains(lower) }
        }
        _results.value = SearchResultPage(tracks = matched)
    }

    fun removeHistoryQuery(q: String) {
        viewModelScope.launch { repository.deleteSearchQuery(q) }
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesStore: PreferencesStore,
    private val repository: MusicRepository,
    private val smartDownloadManager: SmartDownloadManager,
    private val downloadController: DownloadController,
    private val trackPrecacher: TrackPrecacher
) : ViewModel() {
    val preferences: StateFlow<UserPreferences> = preferencesStore.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, UserPreferences())

    fun setDarkTheme(dark: Boolean) = update { it.copy(isDarkTheme = dark) }
    fun setAmbientMode(mode: AmbientMode) = update { it.copy(ambientMode = mode) }
    fun setAmbientReactivity(r: AmbientReactivity) = update { it.copy(ambientReactivity = r) }
    fun setAudioQuality(q: AudioQuality) = update { it.copy(audioQuality = q) }
    fun setDownloadQuality(q: AudioQuality) = update { it.copy(downloadQuality = q) }
    fun setDownloadWifiOnly(wifiOnly: Boolean) = update { it.copy(downloadWifiOnly = wifiOnly) }
    fun setSmartDownloadsEnabled(enabled: Boolean) =
        update(syncKind = DownloadSyncKind.SMART) { it.copy(smartDownloadsEnabled = enabled) }
    fun setSmartDownloadsLimit(limit: Int) =
        update(syncKind = DownloadSyncKind.SMART) { it.copy(smartDownloadsLimit = limit) }
    fun setRecentlyPlayedDownloadsEnabled(enabled: Boolean) =
        update(syncKind = DownloadSyncKind.RECENT) { it.copy(recentlyPlayedDownloadsEnabled = enabled) }
    fun setRecentlyPlayedDownloadsLimit(limit: Int) =
        update(syncKind = DownloadSyncKind.RECENT) { it.copy(recentlyPlayedDownloadsLimit = limit) }
    fun setCapitalizeLabels(capitalize: Boolean) = update { it.copy(capitalizeLabels = capitalize) }
    fun setNormalizeVolume(v: Boolean) = update { it.copy(normalizeVolume = v) }
    fun setSkipSilence(v: Boolean) = update { it.copy(skipSilence = v) }
    fun setAutoMix(v: Boolean) = update { it.copy(autoMix = v) }
    fun setSponsorBlock(v: Boolean) = update { it.copy(sponsorBlockEnabled = v) }
    fun setReportHistory(v: Boolean) = update { it.copy(reportPlaybackHistory = v) }
    fun setShowThumbnails(v: Boolean) = update { it.copy(showTrackThumbnails = v) }
    fun setCacheLimitMb(mb: Int) = update { it.copy(cacheLimitMb = mb) }
    fun setPlaybackSpeed(speed: Float) = update { it.copy(playbackSpeed = speed) }

    fun syncSmartDownloadsNow() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { smartDownloadManager.syncSmartDownloads(force = true) }
        }
    }

    fun syncRecentlyPlayedDownloadsNow() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { smartDownloadManager.syncRecentlyPlayedDownloads(force = true) }
        }
    }

    suspend fun clearStreamCache(): Long = trackPrecacher.clearNonDownloadedStreamCache()

    fun deleteAllDownloads() {
        downloadController.removeAllDownloads()
    }

    fun cacheSizeBytes(): Long = trackPrecacher.cacheSizeBytes()

    suspend fun exportBackup(): String = repository.exportBackupJson()
    suspend fun importBackup(json: String): Result<Unit> = repository.importBackupJson(json)

    private enum class DownloadSyncKind { NONE, SMART, RECENT }

    private fun update(
        syncKind: DownloadSyncKind = DownloadSyncKind.NONE,
        block: (UserPreferences) -> UserPreferences
    ) {
        viewModelScope.launch {
            preferencesStore.update(block)
            when (syncKind) {
                DownloadSyncKind.SMART -> withContext(Dispatchers.IO) {
                    runCatching { smartDownloadManager.syncSmartDownloads(force = true) }
                }
                DownloadSyncKind.RECENT -> withContext(Dispatchers.IO) {
                    runCatching { smartDownloadManager.syncRecentlyPlayedDownloads(force = true) }
                }
                DownloadSyncKind.NONE -> Unit
            }
        }
    }
}
