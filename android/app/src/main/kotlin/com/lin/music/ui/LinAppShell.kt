package com.lin.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin.music.core.designsystem.background.AnimatedAmbientCanvas
import com.lin.music.core.designsystem.components.LinStatusMarquee
import com.lin.music.core.designsystem.components.formatDurationMs
import com.lin.music.core.designsystem.icons.LinIcons
import com.lin.music.core.designsystem.sheets.AddToPlaylistSheet
import com.lin.music.core.designsystem.sheets.ArtistPickerSheet
import com.lin.music.core.designsystem.sheets.SleepTimerSheet
import com.lin.music.core.designsystem.sheets.TagEditorSheet
import com.lin.music.core.designsystem.sheets.TrackActionSheet
import com.lin.music.core.designsystem.theme.APP_BRAND_NAME
import com.lin.music.core.designsystem.theme.LinSharpShape
import com.lin.music.core.designsystem.theme.LinTextStyles
import com.lin.music.core.designsystem.theme.LinTheme
import com.lin.music.core.designsystem.theme.linLabel
import com.lin.music.core.model.HomeItem
import com.lin.music.core.model.PlaylistDetail
import com.lin.music.core.model.speedDialKey
import com.lin.music.core.model.toSummary
import com.lin.music.feature.auth.YtmLoginScreen
import com.lin.music.feature.detail.AlbumDetailScreen
import com.lin.music.feature.detail.ArtistDetailScreen
import com.lin.music.feature.detail.PlaylistDetailScreen
import com.lin.music.feature.home.HomeScreen
import com.lin.music.feature.library.LibraryTabScreen
import com.lin.music.feature.library.PlaylistsScreen
import com.lin.music.feature.player.DockedMiniPlayer
import com.lin.music.feature.player.NowPlayingSheet
import com.lin.music.feature.search.SearchOverlay
import com.lin.music.feature.settings.SettingsScreen
import com.lin.music.util.ShareHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun LinAppShell(
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val session by mainViewModel.session.collectAsStateWithLifecycle()
    val sessionInitialized by mainViewModel.sessionInitialized.collectAsStateWithLifecycle()
    val preferences by mainViewModel.preferences.collectAsStateWithLifecycle()
    val activeTab by mainViewModel.activeTab.collectAsStateWithLifecycle()
    val detailStack by mainViewModel.detailStack.collectAsStateWithLifecycle()
    val isSearchOpen by mainViewModel.isSearchOpen.collectAsStateWithLifecycle()
    val isPlayerExpanded by mainViewModel.isPlayerExpanded.collectAsStateWithLifecycle()
    val isSyncing by mainViewModel.isSyncing.collectAsStateWithLifecycle()
    val activeTagFilter by mainViewModel.activeTagFilter.collectAsStateWithLifecycle()
    val palette by mainViewModel.artworkPalette.collectAsStateWithLifecycle()
    val toast by mainViewModel.toast.collectAsStateWithLifecycle()
    val playlists by mainViewModel.playlists.collectAsStateWithLifecycle()
    val downloadMap by mainViewModel.downloadMap.collectAsStateWithLifecycle()
    val distinctTags by mainViewModel.distinctTags.collectAsStateWithLifecycle()
    val pinnedSpeedDialKeys by mainViewModel.pinnedSpeedDialKeys.collectAsStateWithLifecycle()

    val queueSnapshot by mainViewModel.playerController.queueSnapshot.collectAsStateWithLifecycle()
    val isPlaying by mainViewModel.playerController.isPlaying.collectAsStateWithLifecycle()
    val isBuffering by mainViewModel.playerController.isBuffering.collectAsStateWithLifecycle()
    val streamInfo by mainViewModel.playerController.activeStreamInfo.collectAsStateWithLifecycle()
    val mixChips by mainViewModel.playerController.mixChips.collectAsStateWithLifecycle()
    val sleepTimerState by mainViewModel.playerController.sleepTimerState.collectAsStateWithLifecycle()
    val audioSessionId by mainViewModel.playerController.audioSessionId.collectAsStateWithLifecycle()

    val actionSheetTrack by mainViewModel.actionSheetTrack.collectAsStateWithLifecycle()
    val addToPlaylistTrack by mainViewModel.addToPlaylistTrack.collectAsStateWithLifecycle()
    val containingPlaylistIds by mainViewModel.containingPlaylistIds.collectAsStateWithLifecycle()
    val showSleepTimerSheet by mainViewModel.showSleepTimerSheet.collectAsStateWithLifecycle()
    val artistPickerArtists by mainViewModel.artistPickerArtists.collectAsStateWithLifecycle()
    val tagEditorTarget by mainViewModel.tagEditorTarget.collectAsStateWithLifecycle()
    val tagEditorTags by mainViewModel.tagEditorTags.collectAsStateWithLifecycle()
    val lyricsResult by mainViewModel.lyricsResult.collectAsStateWithLifecycle()
    val lyricsLoading by mainViewModel.lyricsLoading.collectAsStateWithLifecycle()
    val relatedSections by mainViewModel.relatedSections.collectAsStateWithLifecycle()

    LinTheme(isDark = preferences.isDarkTheme, capitalizeLabels = preferences.capitalizeLabels) {
        val linColors = LinTheme.colors
        val accentColor by animateColorAsState(
            targetValue = palette?.brightestAccent?.let { Color(it) } ?: linColors.defaultAccent,
            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            label = "linAccent"
        )
        val activityContext = LocalContext.current
        var minSplashElapsed by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            delay(850L)
            minSplashElapsed = true
        }
        val showLaunchSplash = !sessionInitialized || !minSplashElapsed

        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Living Audio-Reactive Ambient Background (paused when opaque Now Playing covers it)
            AnimatedAmbientCanvas(
                palette = palette,
                audioEnergyFlow = mainViewModel.playerController.audioEnergy,
                mode = preferences.ambientMode,
                isPlaying = isPlaying,
                isActive = !isPlayerExpanded,
                reactivity = preferences.ambientReactivity
            )

            // Gate behind YouTube Music login only once DataStore session check finishes
            if (sessionInitialized && (session == null || !session!!.isValid)) {
                YtmLoginScreen(
                    accentColor = accentColor,
                    onLoggedIn = mainViewModel::onWebViewLoggedIn
                )
            } else if (sessionInitialized) {
                BackHandler(enabled = isPlayerExpanded || isSearchOpen || detailStack.isNotEmpty()) {
                    when {
                        isPlayerExpanded -> mainViewModel.setPlayerExpanded(false)
                        isSearchOpen -> mainViewModel.setSearchOpen(false)
                        else -> mainViewModel.popDetail()
                    }
                }

                val homeViewModel: HomeViewModel = hiltViewModel()
                val libraryViewModel: LibraryViewModel = hiltViewModel()
                val searchViewModel: SearchViewModel = hiltViewModel()
                val settingsViewModel: SettingsViewModel = hiltViewModel()

                var librarySubTab by remember { mutableStateOf(LibrarySubTab.SONGS) }
                var songsSubFilter by remember { mutableStateOf(SongsSubFilter.LIBRARY) }
                val currentVideoId = queueSnapshot.currentTrack?.videoId
                val libSyncError by libraryViewModel.syncError.collectAsStateWithLifecycle()

                LaunchedEffect(activeTab, librarySubTab, songsSubFilter) {
                    if (activeTab != MainTab.Home) {
                        libraryViewModel.refreshForTab(activeTab, librarySubTab, songsSubFilter)
                    }
                }
                LaunchedEffect(libSyncError) {
                    libSyncError?.let { mainViewModel.showToast("library fetch failed: $it") }
                }

                // Main Shell Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    // Top Bar (LIN + Inline Status Marquee + Search + Settings + Floating Circular Theme Toggle)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(APP_BRAND_NAME, style = LinTextStyles.brand, color = accentColor)
                            Spacer(Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isSyncing) accentColor else linColors.textSecondary.copy(alpha = 0.5f))
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = toast != null && !isPlayerExpanded,
                                enter = fadeIn(tween(180)),
                                exit = fadeOut(tween(150))
                            ) {
                                toast?.let { msg ->
                                    LinStatusMarquee(
                                        message = msg.text,
                                        accentColor = accentColor,
                                        actionLabel = msg.actionLabel,
                                        onAction = msg.onAction,
                                        onDismiss = mainViewModel::dismissToast
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(onClick = { mainViewModel.setSearchOpen(true) }, modifier = Modifier.size(40.dp)) {
                                Icon(LinIcons.Search, "Search", tint = linColors.textPrimary, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { mainViewModel.openDetail(DetailRoute.Settings) }, modifier = Modifier.size(40.dp)) {
                                Icon(LinIcons.Settings, "Settings", tint = linColors.textSecondary, modifier = Modifier.size(20.dp))
                            }
                            // Floating circular theme toggle matching .theme-toggle in styles.css
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(linColors.glassBg)
                                    .border(1.dp, linColors.glassBorder, CircleShape)
                                    .clickable { mainViewModel.toggleTheme() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (preferences.isDarkTheme) LinIcons.Sun else LinIcons.Moon,
                                    contentDescription = "Toggle theme",
                                    tint = linColors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Active Content Area (either top detail stack or active main tab)
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        val contentState: Any = detailStack.lastOrNull() ?: activeTab
                        AnimatedContent(
                            targetState = contentState,
                            contentKey = { state ->
                                when (state) {
                                    is DetailRoute.Album -> "album_${state.browseId}"
                                    is DetailRoute.Playlist -> "pl_${state.browseId}"
                                    is DetailRoute.LocalPlaylist -> "lp_${state.kind}"
                                    is DetailRoute.Artist -> "artist_${state.browseId}"
                                    DetailRoute.Settings -> "settings"
                                    is MainTab -> "tab_${state}"
                                    else -> state.hashCode()
                                }
                            },
                            transitionSpec = {
                                (fadeIn(tween(180, easing = FastOutSlowInEasing)) +
                                    slideInVertically(tween(200, easing = FastOutSlowInEasing)) { it / 32 }) togetherWith
                                    fadeOut(tween(140))
                            },
                            label = "linMainContent"
                        ) { target ->
                            when (target) {
                                is DetailRoute.Album -> {
                                    val detailVm: DetailViewModel = hiltViewModel(key = "album_${target.browseId}")
                                    LaunchedEffect(target.browseId) { detailVm.loadAlbum(target.browseId) }
                                    val albumData by detailVm.album.collectAsStateWithLifecycle()
                                    val loading by detailVm.isLoading.collectAsStateWithLifecycle()
                                    val loadingMore by detailVm.isLoadingMore.collectAsStateWithLifecycle()
                                    val err by detailVm.error.collectAsStateWithLifecycle()
                                    val albumPinItem = albumData?.let { HomeItem.AlbumItem(it.toSummary()) }
                                    AlbumDetailScreen(
                                        album = albumData,
                                        isLoading = loading,
                                        isLoadingMore = loadingMore,
                                        error = err,
                                        currentVideoId = currentVideoId,
                                        accentColor = accentColor,
                                        downloadMap = downloadMap,
                                        onBack = { mainViewModel.popDetail() },
                                        onRetry = { detailVm.loadAlbum(target.browseId) },
                                        onPlayTracks = mainViewModel.playerController::playContext,
                                        onShuffle = mainViewModel.playerController::shuffleContext,
                                        onStartMix = mainViewModel::startMixForTrack,
                                        onToggleSaveLibrary = { detailVm.toggleAlbumLibrary() },
                                        onDownloadAll = { tracks ->
                                            albumData?.let { mainViewModel.downloadAlbum(it) }
                                                ?: mainViewModel.downloadController.downloadAll(tracks)
                                            mainViewModel.showToast("downloading ${tracks.size} tracks")
                                        },
                                        onGoToArtist = { id -> mainViewModel.openDetail(DetailRoute.Artist(id)) },
                                        onSwipeRightPlayNext = mainViewModel::playNext,
                                        onSwipeLeftAddToQueue = mainViewModel::addToQueue,
                                        onTrackMore = { t -> mainViewModel.openTrackActions(t) },
                                        onPreviewTrack = mainViewModel::prefetchTrack,
                                        isPinnedToSpeedDial = albumPinItem?.let { it.speedDialKey() in pinnedSpeedDialKeys } == true,
                                        onTogglePinSpeedDial = albumPinItem?.let { item -> { mainViewModel.toggleSpeedDialPin(item) } }
                                    )
                                }

                                is DetailRoute.Playlist -> {
                                    val detailVm: DetailViewModel = hiltViewModel(key = "pl_${target.browseId}")
                                    LaunchedEffect(target.browseId) { detailVm.loadPlaylist(target.browseId) }
                                    val plData by detailVm.playlist.collectAsStateWithLifecycle()
                                    val loading by detailVm.isLoading.collectAsStateWithLifecycle()
                                    val loadingMore by detailVm.isLoadingMore.collectAsStateWithLifecycle()
                                    val err by detailVm.error.collectAsStateWithLifecycle()
                                    val libraryTracks by libraryViewModel.libraryTracks.collectAsStateWithLifecycle()
                                    val plPinItem = plData?.let { HomeItem.PlaylistItem(it.toSummary()) }
                                    PlaylistDetailScreen(
                                        playlist = plData,
                                        isLoading = loading,
                                        isLoadingMore = loadingMore,
                                        error = err,
                                        currentVideoId = currentVideoId,
                                        accentColor = accentColor,
                                        downloadMap = downloadMap,
                                        libraryTracks = libraryTracks,
                                        onBack = { mainViewModel.popDetail() },
                                        onRetry = { detailVm.loadPlaylist(target.browseId) },
                                        onPlayTracks = mainViewModel.playerController::playContext,
                                        onShuffle = mainViewModel.playerController::shuffleContext,
                                        onStartMix = mainViewModel::startMixForPlaylist,
                                        onDownloadAll = { tracks ->
                                            plData?.let { mainViewModel.downloadPlaylist(it) }
                                                ?: mainViewModel.downloadController.downloadAll(tracks)
                                            mainViewModel.showToast("downloading ${tracks.size} tracks")
                                        },
                                        onToggleSaveLibrary = {
                                            detailVm.togglePlaylistLibrary { msg -> mainViewModel.showToast(msg) }
                                        },
                                        onCloneToEditable = {
                                            detailVm.clonePlaylistToOwned(
                                                onCloned = { newId ->
                                                    mainViewModel.popDetail()
                                                    mainViewModel.openDetail(DetailRoute.Playlist("VL$newId"))
                                                    mainViewModel.showToast("copied to your playlists")
                                                },
                                                onError = { errMsg -> mainViewModel.showToast(errMsg) }
                                            )
                                        },
                                        onUpdatePlaylistMeta = detailVm::updatePlaylistMeta,
                                        onDeletePlaylist = {
                                            detailVm.deletePlaylist {
                                                mainViewModel.showToast("playlist deleted")
                                                mainViewModel.popDetail()
                                            }
                                        },
                                        onAddSongToPlaylist = { t ->
                                            detailVm.addTrackToCurrentPlaylist(t)
                                            mainViewModel.showToast("added ${t.title}")
                                        },
                                        onMoveTrack = detailVm::moveTrackUpOrDown,
                                        onSwipeRightPlayNext = mainViewModel::playNext,
                                        onSwipeLeftAddToQueue = mainViewModel::addToQueue,
                                        onTrackMore = { t, ctx -> mainViewModel.openTrackActions(t, ctx) },
                                        onTrackRemovedLocal = detailVm::removePlaylistTrackLocal,
                                        onTrackRestoredLocal = detailVm::restorePlaylistTrackLocal,
                                        onPreviewTrack = mainViewModel::prefetchTrack,
                                        isPinnedToSpeedDial = plPinItem?.let { it.speedDialKey() in pinnedSpeedDialKeys } == true,
                                        onTogglePinSpeedDial = plPinItem?.let { item -> { mainViewModel.toggleSpeedDialPin(item) } }
                                    )
                                }

                                is DetailRoute.LocalPlaylist -> {
                                    val smartDownloadedTracks by libraryViewModel.smartDownloadedTracks.collectAsStateWithLifecycle()
                                    val recentlyPlayedDownloadedTracks by libraryViewModel.recentlyPlayedDownloadedTracks.collectAsStateWithLifecycle()
                                    val libraryTracks by libraryViewModel.libraryTracks.collectAsStateWithLifecycle()
                                    val tracks = when (target.kind) {
                                        LocalDownloadPlaylistKind.SMART_DOWNLOADS -> smartDownloadedTracks
                                        LocalDownloadPlaylistKind.RECENTLY_PLAYED_DOWNLOADS -> recentlyPlayedDownloadedTracks
                                    }
                                    val localTitle = linLabel(target.kind.title)
                                    val localDesc = linLabel(target.kind.subtitle)
                                    val localDetail = remember(target.kind, localTitle, localDesc, tracks) {
                                        PlaylistDetail(
                                            id = "LOCAL_${target.kind.name}",
                                            browseId = "LOCAL_${target.kind.name}",
                                            title = localTitle,
                                            description = localDesc,
                                            author = "LIN Offline",
                                            thumbnailUrl = tracks.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl,
                                            tracks = tracks,
                                            isEditable = false,
                                            inLibrary = true
                                        )
                                    }
                                    PlaylistDetailScreen(
                                        playlist = localDetail,
                                        isLoading = false,
                                        error = null,
                                        currentVideoId = currentVideoId,
                                        accentColor = accentColor,
                                        downloadMap = downloadMap,
                                        libraryTracks = libraryTracks,
                                        onBack = { mainViewModel.popDetail() },
                                        onRetry = {},
                                        onPlayTracks = mainViewModel.playerController::playContext,
                                        onShuffle = mainViewModel.playerController::shuffleContext,
                                        onStartMix = { _, _ -> },
                                        onDownloadAll = {},
                                        onToggleSaveLibrary = {},
                                        onCloneToEditable = {},
                                        onUpdatePlaylistMeta = { _, _ -> },
                                        onDeletePlaylist = {},
                                        onAddSongToPlaylist = {},
                                        onMoveTrack = { _, _ -> },
                                        onSwipeRightPlayNext = mainViewModel::playNext,
                                        onSwipeLeftAddToQueue = mainViewModel::addToQueue,
                                        onTrackMore = { t, ctx -> mainViewModel.openTrackActions(t, ctx) },
                                        onTrackRemovedLocal = {},
                                        onTrackRestoredLocal = {},
                                        onPreviewTrack = mainViewModel::prefetchTrack
                                    )
                                }

                                is DetailRoute.Artist -> {
                                    val detailVm: DetailViewModel = hiltViewModel(key = "artist_${target.browseId}")
                                    LaunchedEffect(target.browseId) { detailVm.loadArtist(target.browseId) }
                                    val artistData by detailVm.artist.collectAsStateWithLifecycle()
                                    val loading by detailVm.isLoading.collectAsStateWithLifecycle()
                                    val err by detailVm.error.collectAsStateWithLifecycle()
                                    val artistPinItem = artistData?.let { HomeItem.ArtistItem(it.toSummary()) }
                                    ArtistDetailScreen(
                                        artist = artistData,
                                        isLoading = loading,
                                        error = err,
                                        currentVideoId = currentVideoId,
                                        accentColor = accentColor,
                                        downloadMap = downloadMap,
                                        onBack = { mainViewModel.popDetail() },
                                        onRetry = { detailVm.loadArtist(target.browseId) },
                                        onToggleSubscribe = detailVm::toggleArtistSubscribe,
                                        onPlayTracks = mainViewModel.playerController::playContext,
                                        onStartMix = mainViewModel::startMixForPlaylist,
                                        onAlbumClick = { id -> mainViewModel.openDetail(DetailRoute.Album(id)) },
                                        onArtistClick = { id -> mainViewModel.openDetail(DetailRoute.Artist(id)) },
                                        onSwipeRightPlayNext = mainViewModel::playNext,
                                        onSwipeLeftAddToQueue = mainViewModel::addToQueue,
                                        onTrackMore = { t -> mainViewModel.openTrackActions(t) },
                                        isPinnedToSpeedDial = artistPinItem?.let { it.speedDialKey() in pinnedSpeedDialKeys } == true,
                                        onTogglePinSpeedDial = artistPinItem?.let { item -> { mainViewModel.toggleSpeedDialPin(item) } }
                                    )
                                }

                                DetailRoute.Settings -> {
                                    val downloadedTracks by libraryViewModel.downloadedTracks.collectAsStateWithLifecycle()
                                    var cacheSizeBytes by remember { mutableLongStateOf(0L) }
                                    LaunchedEffect(preferences, downloadedTracks.size) {
                                        cacheSizeBytes = withContext(Dispatchers.IO) {
                                            settingsViewModel.cacheSizeBytes()
                                        }
                                    }
                                    SettingsScreen(
                                        session = session,
                                        preferences = preferences,
                                        accentColor = accentColor,
                                        audioSessionId = audioSessionId,
                                        downloadedCount = downloadedTracks.size,
                                        cacheSizeBytes = cacheSizeBytes,
                                        onBack = { mainViewModel.popDetail() },
                                        onSyncNow = { mainViewModel.syncAccount(force = true) },
                                        onLogout = { mainViewModel.logout() },
                                        onSetContentRegion = settingsViewModel::setContentRegion,
                                        onSetDarkTheme = settingsViewModel::setDarkTheme,
                                        onSetCapitalizeLabels = settingsViewModel::setCapitalizeLabels,
                                        onSetAmbientMode = settingsViewModel::setAmbientMode,
                                        onSetAmbientReactivity = settingsViewModel::setAmbientReactivity,
                                        onSetAudioQuality = settingsViewModel::setAudioQuality,
                                        onSetDownloadQuality = settingsViewModel::setDownloadQuality,
                                        onSetDownloadWifiOnly = settingsViewModel::setDownloadWifiOnly,
                                        onSetSmartDownloadsEnabled = settingsViewModel::setSmartDownloadsEnabled,
                                        onSetSmartDownloadsLimit = settingsViewModel::setSmartDownloadsLimit,
                                        onSetRecentlyPlayedDownloadsEnabled = settingsViewModel::setRecentlyPlayedDownloadsEnabled,
                                        onSetRecentlyPlayedDownloadsLimit = settingsViewModel::setRecentlyPlayedDownloadsLimit,
                                        onSyncSmartDownloadsNow = settingsViewModel::syncSmartDownloadsNow,
                                        onSyncRecentlyPlayedDownloadsNow = settingsViewModel::syncRecentlyPlayedDownloadsNow,
                                        onClearStreamCache = settingsViewModel::clearStreamCache,
                                        onDeleteAllDownloads = settingsViewModel::deleteAllDownloads,
                                        onSetNormalizeVolume = settingsViewModel::setNormalizeVolume,
                                        onSetSkipSilence = settingsViewModel::setSkipSilence,
                                        onSetAutoMix = settingsViewModel::setAutoMix,
                                        onSetSponsorBlock = settingsViewModel::setSponsorBlock,
                                        onSetReportHistory = settingsViewModel::setReportHistory,
                                        onSetShowThumbnails = settingsViewModel::setShowThumbnails,
                                        onSetPlaybackSpeed = settingsViewModel::setPlaybackSpeed,
                                        onExportBackup = settingsViewModel::exportBackup,
                                        onImportBackup = settingsViewModel::importBackup,
                                        onToast = { msg -> mainViewModel.showToast(msg) }
                                    )
                                }

                                MainTab.Home -> {
                                    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
                                    HomeScreen(
                                        uiState = homeState,
                                        currentVideoId = currentVideoId,
                                        accentColor = accentColor,
                                        downloadMap = downloadMap,
                                        onRefresh = { homeViewModel.refresh(includeLibrary = true) },
                                        onLoadMore = homeViewModel::loadMore,
                                        onSeeAllPlaylists = { mainViewModel.selectTab(MainTab.Playlists) },
                                        onPlayTracks = mainViewModel.playerController::playContext,
                                        onStartMixForTrack = mainViewModel::startMixForTrack,
                                        onTogglePinSpeedDial = mainViewModel::toggleSpeedDialPin,
                                        onTrackMore = { t -> mainViewModel.openTrackActions(t) },
                                        onAlbumClick = { id -> mainViewModel.openDetail(DetailRoute.Album(id)) },
                                        onPlaylistClick = { id -> mainViewModel.openDetail(DetailRoute.Playlist(id)) },
                                        onArtistClick = { id -> mainViewModel.openDetail(DetailRoute.Artist(id)) }
                                    )
                                }

                                MainTab.Library -> {
                                    val libraryTracks by libraryViewModel.libraryTracks.collectAsStateWithLifecycle()
                                    val likedTracks by libraryViewModel.likedTracks.collectAsStateWithLifecycle()
                                    val downloadedTracks by libraryViewModel.downloadedTracks.collectAsStateWithLifecycle()
                                    val smartDownloadedTracks by libraryViewModel.smartDownloadedTracks.collectAsStateWithLifecycle()
                                    val recentlyPlayedDownloadedTracks by libraryViewModel.recentlyPlayedDownloadedTracks.collectAsStateWithLifecycle()
                                    val historyTracks by libraryViewModel.historyTracks.collectAsStateWithLifecycle()
                                    val albums by libraryViewModel.albums.collectAsStateWithLifecycle()
                                    val downloadedAlbums by libraryViewModel.downloadedAlbums.collectAsStateWithLifecycle()
                                    val artists by libraryViewModel.artists.collectAsStateWithLifecycle()

                                    LibraryTabScreen(
                                        subTab = librarySubTab,
                                        onSubTabChange = { librarySubTab = it },
                                        songsSubFilter = songsSubFilter,
                                        onSongsSubFilterChange = { songsSubFilter = it },
                                        libraryTracks = libraryTracks,
                                        likedTracks = likedTracks,
                                        downloadedTracks = downloadedTracks,
                                        smartDownloadedTracks = smartDownloadedTracks,
                                        recentlyPlayedDownloadedTracks = recentlyPlayedDownloadedTracks,
                                        smartDownloadsEnabled = preferences.smartDownloadsEnabled,
                                        recentlyPlayedDownloadsEnabled = preferences.recentlyPlayedDownloadsEnabled,
                                        historyTracks = historyTracks,
                                        artists = artists,
                                        albums = albums,
                                        downloadedAlbums = downloadedAlbums,
                                        distinctTags = distinctTags,
                                        activeTagFilter = activeTagFilter,
                                        onTagClick = mainViewModel::setActiveTagFilter,
                                        onTagClear = { mainViewModel.setActiveTagFilter(null) },
                                        currentVideoId = currentVideoId,
                                        accentColor = accentColor,
                                        downloadMap = downloadMap,
                                        showThumbnails = preferences.showTrackThumbnails,
                                        onPlayTracks = mainViewModel.playerController::playContext,
                                        onSwipeRightPlayNext = mainViewModel::playNext,
                                        onSwipeLeftAddToQueue = mainViewModel::addToQueue,
                                        onTrackMore = { t -> mainViewModel.openTrackActions(t) },
                                        onArtistClick = { id -> mainViewModel.openDetail(DetailRoute.Artist(id)) },
                                        onAlbumClick = { id -> mainViewModel.openDetail(DetailRoute.Album(id)) },
                                        onOpenLocalPlaylist = { kind -> mainViewModel.openDetail(DetailRoute.LocalPlaylist(kind)) },
                                        onPreviewTrack = mainViewModel::prefetchTrack
                                    )
                                }

                                MainTab.Playlists -> PlaylistsScreen(
                                    playlists = playlists,
                                    activeTagFilter = activeTagFilter,
                                    accentColor = accentColor,
                                    onTagClick = mainViewModel::setActiveTagFilter,
                                    onPlaylistClick = { id -> mainViewModel.openDetail(DetailRoute.Playlist(id)) },
                                    onSwipeRightPlayNext = mainViewModel::playPlaylistNext,
                                    onSwipeLeftAddToQueue = mainViewModel::addPlaylistToQueue,
                                    onCreatePlaylist = { title, desc, privacy ->
                                        mainViewModel.createPlaylist(title, desc, privacy)
                                    }
                                )
                            }
                        }
                    }
                }

                // Bottom Docked Mini-Player + Lowercase Navigation Rail (opaque + consumes touches so home never leaks through)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(linColors.background)
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent().changes.forEach { it.consume() }
                                    }
                                }
                            }
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    ) {
                        queueSnapshot.currentTrack?.let { current ->
                            DockedMiniPlayer(
                                track = current,
                                isPlaying = isPlaying,
                                isBuffering = isBuffering,
                                positionMsFlow = mainViewModel.playerController.positionMs,
                                durationMsFlow = mainViewModel.playerController.durationMs,
                                accentColor = accentColor,
                                onExpand = { mainViewModel.setPlayerExpanded(true) },
                                onTogglePlayPause = mainViewModel.playerController::togglePlayPause,
                                onSkipNext = mainViewModel.playerController::skipNext,
                                onSwipeLeftNext = mainViewModel.playerController::skipNext,
                                onSwipeRightPrev = mainViewModel.playerController::skipPrevious
                            )
                        }

                        // Lowercase Editorial Navigation Rail matching frontend_css .left-menu
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, linColors.glassBorder)
                                .padding(vertical = 14.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val tabs = listOf(
                                MainTab.Home to "home",
                                MainTab.Library to "library",
                                MainTab.Playlists to "playlists"
                            )
                            tabs.forEach { (tab, label) ->
                                val selected = detailStack.isEmpty() && activeTab == tab
                                val navColor by animateColorAsState(
                                    targetValue = if (selected) linColors.textPrimary else linColors.textSecondary,
                                    animationSpec = tween(180),
                                    label = "bottomNavTab"
                                )
                                Text(
                                    text = linLabel(label),
                                    style = if (selected) LinTextStyles.navItemActive else LinTextStyles.navItem,
                                    color = navColor,
                                    modifier = Modifier
                                        .clickable { mainViewModel.selectTab(tab) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Full-Screen Expandable Now Playing Sheet with Spring Entry
                AnimatedVisibility(
                    visible = isPlayerExpanded,
                    enter = slideInVertically(
                        animationSpec = spring(dampingRatio = 0.88f, stiffness = 340f)
                    ) { it } + fadeIn(tween(220, easing = FastOutSlowInEasing)),
                    exit = slideOutVertically(
                        animationSpec = tween(240, easing = FastOutSlowInEasing)
                    ) { it } + fadeOut(tween(180))
                ) {
                    NowPlayingSheet(
                        queueSnapshot = queueSnapshot,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        positionMsFlow = mainViewModel.playerController.positionMs,
                        durationMsFlow = mainViewModel.playerController.durationMs,
                        streamInfo = streamInfo,
                        palette = palette,
                        audioEnergyFlow = mainViewModel.playerController.audioEnergy,
                        ambientMode = preferences.ambientMode,
                        ambientReactivity = preferences.ambientReactivity,
                        accentColor = accentColor,
                        mixChips = mixChips,
                        sleepTimerState = sleepTimerState,
                        lyricsResult = lyricsResult,
                        lyricsLoading = lyricsLoading,
                        relatedSections = relatedSections,
                        downloadMap = downloadMap,
                        isVisible = isPlayerExpanded,
                        statusMessage = toast?.text,
                        statusActionLabel = toast?.actionLabel,
                        onStatusAction = toast?.onAction,
                        onDismissStatus = mainViewModel::dismissToast,
                        onCollapse = { mainViewModel.setPlayerExpanded(false) },
                        onTogglePlayPause = mainViewModel.playerController::togglePlayPause,
                        onSkipNext = mainViewModel.playerController::skipNext,
                        onSkipPrevious = mainViewModel.playerController::skipPrevious,
                        onSeekTo = mainViewModel.playerController::seekTo,
                        onToggleShuffle = mainViewModel.playerController::toggleShuffle,
                        onCycleRepeat = mainViewModel.playerController::cycleRepeatMode,
                        onToggleLike = mainViewModel::toggleLike,
                        onToggleLibrary = mainViewModel::toggleLibrary,
                        onToggleDownload = mainViewModel::toggleDownload,
                        onOpenAddToPlaylist = mainViewModel::openAddToPlaylist,
                        onOpenTrackMenu = { t -> mainViewModel.openTrackActions(t) },
                        onGoToAlbum = mainViewModel::goToAlbum,
                        onGoToArtist = mainViewModel::goToArtist,
                        onOpenSleepTimer = mainViewModel::openSleepTimerSheet,
                        onSelectMixChip = mainViewModel.playerController::selectMixChip,
                        onJumpToQueueIndex = mainViewModel.playerController::jumpToQueueIndex,
                        onMoveQueueItem = mainViewModel.playerController::moveQueueItem,
                        onRemoveQueueItem = { idx -> mainViewModel.playerController.removeQueueItem(idx) },
                        onClearUpcoming = { mainViewModel.playerController.clearUpcomingQueue() },
                        onSaveQueueAsPlaylist = mainViewModel::saveQueueAsPlaylist,
                        onPlayTracks = mainViewModel.playerController::playContext,
                        onSwipeRightPlayNext = mainViewModel::playNext,
                        onSwipeLeftAddToQueue = mainViewModel::addToQueue,
                        onAlbumClick = { id -> mainViewModel.openDetail(DetailRoute.Album(id)) },
                        onPlaylistClick = { id -> mainViewModel.openDetail(DetailRoute.Playlist(id)) },
                        onSkipToPreviousTrack = mainViewModel.playerController::skipToPreviousTrack,
                        onPrewarmSwipeTarget = mainViewModel::prefetchTrack,
                        onDockStateChanged = { isLyrics, isRelated ->
                            mainViewModel.onNowPlayingDockChanged(isPlayerExpanded, isLyrics, isRelated)
                        }
                    )
                }

                // Full-Screen Frosted Search Overlay
                AnimatedVisibility(
                    visible = isSearchOpen,
                    enter = fadeIn(tween(200, easing = FastOutSlowInEasing)) +
                        slideInVertically(tween(220, easing = FastOutSlowInEasing)) { -it / 16 },
                    exit = fadeOut(tween(160)) +
                        slideOutVertically(tween(180, easing = FastOutSlowInEasing)) { -it / 20 }
                ) {
                    val query by searchViewModel.query.collectAsStateWithLifecycle()
                    val filter by searchViewModel.filter.collectAsStateWithLifecycle()
                    val suggestions by searchViewModel.suggestions.collectAsStateWithLifecycle()
                    val history by searchViewModel.searchHistory.collectAsStateWithLifecycle()
                    val results by searchViewModel.results.collectAsStateWithLifecycle()
                    val searchLoading by searchViewModel.isLoading.collectAsStateWithLifecycle()

                    SearchOverlay(
                        query = query,
                        filter = filter,
                        suggestions = suggestions,
                        history = history,
                        results = results,
                        isLoading = searchLoading,
                        currentVideoId = currentVideoId,
                        accentColor = accentColor,
                        downloadMap = downloadMap,
                        onQueryChange = searchViewModel::onQueryChanged,
                        onFilterChange = searchViewModel::selectFilter,
                        onSubmitSearch = searchViewModel::submitSearch,
                        onDeleteHistory = searchViewModel::removeHistoryQuery,
                        onClose = { mainViewModel.setSearchOpen(false) },
                        onPlayTracks = { list, idx, src ->
                            mainViewModel.playerController.playContext(list, idx, src)
                            mainViewModel.setSearchOpen(false)
                        },
                        onSwipeRightPlayNext = mainViewModel::playNext,
                        onSwipeLeftAddToQueue = mainViewModel::addToQueue,
                        onTrackMore = { t -> mainViewModel.openTrackActions(t) },
                        onAlbumClick = { id -> mainViewModel.openDetail(DetailRoute.Album(id)) },
                        onArtistClick = { id -> mainViewModel.openDetail(DetailRoute.Artist(id)) },
                        onPlaylistClick = { id -> mainViewModel.openDetail(DetailRoute.Playlist(id)) }
                    )
                }

                // --- Modal Action Sheets ---

                actionSheetTrack?.let { (track, editableCtx) ->
                    val snapPositionMs = remember(actionSheetTrack) {
                        mainViewModel.playerController.positionMs.value
                    }
                    TrackActionSheet(
                        track = track,
                        isDownloaded = downloadMap[track.videoId] == 2,
                        editablePlaylistName = editableCtx?.playlistTitle,
                        onDismiss = mainViewModel::dismissTrackActions,
                        onToggleLike = { mainViewModel.toggleLike(track) },
                        onPlayNext = { mainViewModel.playNext(track) },
                        onAddToQueue = { mainViewModel.addToQueue(track) },
                        onStartMix = { mainViewModel.startMixForTrack(track) },
                        onToggleLibrary = { mainViewModel.toggleLibrary(track) },
                        onOpenAddToPlaylist = { mainViewModel.openAddToPlaylist(track) },
                        onDeleteFromPlaylist = editableCtx?.let { ctx ->
                            { mainViewModel.deleteTrackFromEditablePlaylist(track, ctx) }
                        },
                        onGoToAlbum = { mainViewModel.goToAlbum(track) },
                        onGoToArtist = { mainViewModel.goToArtist(track) },
                        onToggleDownload = { mainViewModel.toggleDownload(track) },
                        onManageTags = {
                            mainViewModel.openTagEditor(TagEditorTarget(track.videoId, "track", track.title))
                        },
                        onShare = { ShareHelper.shareTrack(activityContext, track) },
                        onShareAtTimestamp = if (isPlayerExpanded && track.videoId == currentVideoId && snapPositionMs > 1000L) {
                            { ShareHelper.shareTrack(activityContext, track, snapPositionMs / 1000L) }
                        } else null,
                        shareTimestampLabel = if (isPlayerExpanded && track.videoId == currentVideoId && snapPositionMs > 1000L) {
                            formatDurationMs(snapPositionMs)
                        } else null,
                        isPinnedToSpeedDial = HomeItem.SongItem(track).speedDialKey() in pinnedSpeedDialKeys,
                        onTogglePinSpeedDial = { mainViewModel.toggleSpeedDialPin(HomeItem.SongItem(track)) }
                    )
                }

                addToPlaylistTrack?.let { track ->
                    AddToPlaylistSheet(
                        track = track,
                        playlists = playlists.filter { it.isEditable && it.id != "LM" },
                        containingPlaylistIds = containingPlaylistIds,
                        onDismiss = mainViewModel::dismissAddToPlaylist,
                        onTogglePlaylistMembership = { pl, shouldAdd ->
                            mainViewModel.togglePlaylistMembership(pl, track, shouldAdd)
                        },
                        onCreateNewPlaylist = { name, desc, privacy ->
                            mainViewModel.createPlaylist(name, desc, privacy, initialTrack = track)
                        }
                    )
                }

                if (showSleepTimerSheet) {
                    SleepTimerSheet(
                        state = sleepTimerState,
                        onStartCountdown = { mins, fade ->
                            mainViewModel.playerController.startSleepCountdown(mins * 60_000L, fade)
                            mainViewModel.showToast("sleep timer set for ${mins}m")
                        },
                        onStartEndOfTrack = { fade ->
                            mainViewModel.playerController.startSleepEndOfTrack(fade)
                            mainViewModel.showToast("will pause after current track")
                        },
                        onExtend5Min = { mainViewModel.playerController.extendSleepTimer() },
                        onCancelTimer = { mainViewModel.playerController.cancelSleepTimer() },
                        onDismiss = mainViewModel::dismissSleepTimerSheet
                    )
                }

                artistPickerArtists?.let { artists ->
                    ArtistPickerSheet(
                        artists = artists,
                        onSelectArtist = { art ->
                            art.id?.let { id -> mainViewModel.openDetail(DetailRoute.Artist(id)) }
                        },
                        onDismiss = mainViewModel::dismissArtistPicker
                    )
                }

                tagEditorTarget?.let { target ->
                    TagEditorSheet(
                        itemTitle = target.title,
                        tags = tagEditorTags,
                        onAddTag = { raw -> mainViewModel.addTag(target, raw) },
                        onRemoveTag = { tag -> mainViewModel.removeTag(target, tag) },
                        onDismiss = mainViewModel::dismissTagEditor
                    )
                }
            }

            AnimatedVisibility(
                visible = showLaunchSplash,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(340, easing = FastOutSlowInEasing)) +
                    scaleOut(tween(340, easing = FastOutSlowInEasing), targetScale = 1.04f)
            ) {
                LinLaunchOverlay(accentColor = accentColor)
            }
        }
    }
}

@Composable
private fun LinLaunchOverlay(accentColor: Color) {
    val linColors = LinTheme.colors
    val infiniteTransition = rememberInfiniteTransition(label = "linLaunchBar")
    val sweepFraction by infiniteTransition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 950, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "accentBarSweep"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(linColors.background.copy(alpha = 0.68f))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = APP_BRAND_NAME,
                style = LinTextStyles.brand.copy(fontSize = 24.sp, letterSpacing = 5.sp),
                color = accentColor
            )
            Spacer(Modifier.height(14.dp))
            BoxWithConstraints(
                modifier = Modifier
                    .width(88.dp)
                    .height(1.5.dp)
                    .clip(LinSharpShape)
                    .background(linColors.progressTrack)
            ) {
                val indicatorWidth = maxWidth * 0.42f
                Box(
                    modifier = Modifier
                        .offset(x = maxWidth * sweepFraction)
                        .width(indicatorWidth)
                        .fillMaxHeight()
                        .background(accentColor)
                )
            }
        }
    }
}
