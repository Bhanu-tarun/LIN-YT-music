package com.lin.music.feature.player

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode as ComposeRepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.Size
import com.lin.music.core.designsystem.background.AnimatedAmbientCanvas
import com.lin.music.core.designsystem.components.LinLazyColumnScrollbar
import com.lin.music.core.designsystem.components.LinOutlinedButton
import com.lin.music.core.designsystem.components.LinScrubber
import com.lin.music.core.designsystem.components.LinStatusMarquee
import com.lin.music.core.designsystem.components.LinTagPill
import com.lin.music.core.designsystem.components.SwipeableTrackRow
import com.lin.music.core.designsystem.sheets.LinUnderlineTextField
import com.lin.music.core.lyrics.LyricsTextProcessor
import com.lin.music.core.model.LyricsSearchCandidate
import com.lin.music.core.designsystem.components.formatDurationMs
import com.lin.music.core.designsystem.components.highResArtworkUrl
import com.lin.music.core.designsystem.icons.LinIcons
import com.lin.music.core.designsystem.sheets.PlaylistEditorDialog
import com.lin.music.core.designsystem.theme.APP_BRAND_NAME
import com.lin.music.core.designsystem.theme.LinSharpShape
import com.lin.music.core.designsystem.theme.LinTextStyles
import com.lin.music.core.designsystem.theme.LinTheme
import com.lin.music.core.designsystem.theme.linLabel
import com.lin.music.core.lyrics.LrcParser
import com.lin.music.core.model.AmbientMode
import com.lin.music.core.model.AmbientReactivity
import com.lin.music.core.model.AudioEnergy
import com.lin.music.core.model.HomeItem
import com.lin.music.core.model.HomeSection
import com.lin.music.core.model.LyricsResult
import com.lin.music.core.model.MixChip
import com.lin.music.core.model.RepeatMode
import com.lin.music.core.model.SleepTimerState
import com.lin.music.core.model.StreamInfo
import com.lin.music.core.model.Track
import com.lin.music.core.model.TrackArtworkPalette
import com.lin.music.core.playback.queue.QueueSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun DockedMiniPlayer(
    track: Track,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMsFlow: StateFlow<Long>,
    durationMsFlow: StateFlow<Long>,
    accentColor: Color,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSwipeLeftNext: () -> Unit,
    onSwipeRightPrev: () -> Unit,
    modifier: Modifier = Modifier
) {
    val linColors = LinTheme.colors
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(linColors.glassBg)
            .border(1.dp, linColors.glassBorder)
            .pointerInput(track.videoId) {
                detectHorizontalDragGestures(
                    onDragStart = { dragAccumulator = 0f },
                    onHorizontalDrag = { _, dragAmount -> dragAccumulator += dragAmount },
                    onDragEnd = {
                        if (dragAccumulator < -90f) onSwipeLeftNext()
                        else if (dragAccumulator > 90f) onSwipeRightPrev()
                        dragAccumulator = 0f
                    }
                )
            }
            .clickable(onClick = onExpand)
    ) {
        MiniPlayerProgressHairline(
            positionMsFlow = positionMsFlow,
            durationMsFlow = durationMsFlow,
            fallbackDurationMs = track.durationMs,
            accentColor = accentColor
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(LinSharpShape)
                    .background(linColors.glassBorder)
            ) {
                if (!track.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = LinTextStyles.navItemActive,
                    color = linColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isBuffering) "buffering..." else track.artistDisplay,
                    style = LinTextStyles.subtitle,
                    color = linColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onTogglePlayPause) {
                Icon(
                    imageVector = if (isPlaying) LinIcons.Pause else LinIcons.Play,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = linColors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onSkipNext) {
                Icon(
                    imageVector = LinIcons.SkipForward,
                    contentDescription = "Next",
                    tint = linColors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerProgressHairline(
    positionMsFlow: StateFlow<Long>,
    durationMsFlow: StateFlow<Long>,
    fallbackDurationMs: Long,
    accentColor: Color
) {
    val linColors = LinTheme.colors
    val positionMs by positionMsFlow.collectAsStateWithLifecycle()
    val rawDurationMs by durationMsFlow.collectAsStateWithLifecycle()
    val effectiveDuration = rawDurationMs.coerceAtLeast(fallbackDurationMs)
    val progress = if (effectiveDuration > 0L) (positionMs.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f) else 0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(linColors.progressTrack)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(2.dp)
                .background(accentColor)
        )
    }
}

private enum class NowPlayingDockTab(val label: String) {
    PLAYER("player"),
    UP_NEXT("up next"),
    LYRICS("lyrics"),
    RELATED("related")
}

@Composable
fun NowPlayingSheet(
    queueSnapshot: QueueSnapshot,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMsFlow: StateFlow<Long>,
    durationMsFlow: StateFlow<Long>,
    streamInfo: StreamInfo?,
    palette: TrackArtworkPalette?,
    audioEnergyFlow: StateFlow<AudioEnergy>,
    ambientMode: AmbientMode,
    ambientReactivity: AmbientReactivity = AmbientReactivity.EXPRESSIVE,
    accentColor: Color,
    mixChips: List<MixChip>,
    sleepTimerState: SleepTimerState,
    lyricsResult: LyricsResult?,
    lyricsLoading: Boolean,
    relatedSections: List<HomeSection>,
    downloadMap: Map<String, Int>,
    isVisible: Boolean = true,
    statusMessage: String? = null,
    statusActionLabel: String? = null,
    onStatusAction: (() -> Unit)? = null,
    onDismissStatus: () -> Unit = {},
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleLike: (Track) -> Unit,
    onToggleLibrary: (Track) -> Unit,
    onToggleDownload: (Track) -> Unit,
    onOpenAddToPlaylist: (Track) -> Unit,
    onOpenTrackMenu: (Track) -> Unit,
    onGoToAlbum: (Track) -> Unit,
    onGoToArtist: (Track) -> Unit,
    onOpenSleepTimer: () -> Unit,
    onSelectMixChip: (MixChip) -> Unit,
    onJumpToQueueIndex: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onClearUpcoming: () -> Unit,
    onSaveQueueAsPlaylist: (String) -> Unit,
    onPlayTracks: (List<Track>, Int, String) -> Unit,
    onSwipeRightPlayNext: (Track) -> Unit,
    onSwipeLeftAddToQueue: (Track) -> Unit,
    onAlbumClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onSkipToPreviousTrack: () -> Unit = onSkipPrevious,
    onPrewarmSwipeTarget: (Track) -> Unit = {},
    onDockStateChanged: (isLyrics: Boolean, isRelated: Boolean) -> Unit = { _, _ -> },
    lyricsShowOriginal: Boolean = true,
    lyricsShowRomanized: Boolean = false,
    lyricsShowTranslation: Boolean = false,
    translatedLyricsLines: List<String> = emptyList(),
    translatingLyrics: Boolean = false,
    lyricsSearchResults: List<LyricsSearchCandidate> = emptyList(),
    lyricsSearching: Boolean = false,
    onAdjustLyricsOffset: (Long) -> Unit = {},
    onResetLyricsOffset: () -> Unit = {},
    onSearchLyrics: (String) -> Unit = {},
    onSelectLyricsCandidate: (LyricsSearchCandidate) -> Unit = {},
    onSetLyricsDisplayFlags: (Boolean, Boolean, Boolean) -> Unit = { _, _, _ -> },
    onShareLyricsCard: (Track, List<String>, List<String?>, Int) -> Unit = { _, _, _, _ -> },
    onLyricsModeToast: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val linColors = LinTheme.colors
    val currentTrack = queueSnapshot.currentTrack
    val prevTrack = remember(queueSnapshot) {
        queueSnapshot.previousTrackIndexOrNull()?.let { queueSnapshot.items.getOrNull(it) }
    }
    val nextTrack = remember(queueSnapshot) {
        queueSnapshot.nextTrackIndexOrNull()?.let { queueSnapshot.items.getOrNull(it) }
    }
    var activeDock by remember { mutableStateOf(NowPlayingDockTab.PLAYER) }
    var showSaveQueueDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val sheetOffsetY = remember { Animatable(0f) }
    var sheetHeightPx by remember { mutableFloatStateOf(1600f) }
    var isDismissing by remember { mutableStateOf(false) }
    var dismissJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(isVisible, activeDock) {
        onDockStateChanged(
            isVisible && activeDock == NowPlayingDockTab.LYRICS,
            isVisible && activeDock == NowPlayingDockTab.RELATED
        )
    }
    DisposableEffect(Unit) {
        onDispose { onDockStateChanged(false, false) }
    }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            dismissJob?.cancel()
            dismissJob = null
            isDismissing = false
            sheetOffsetY.snapTo(0f)
        }
    }

    // Opaque base layer + dedicated audio-reactive blob canvas; whole sheet translates cleanly on swipe-down
    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { sheetHeightPx = it.height.toFloat().coerceAtLeast(1f) }
            .graphicsLayer {
                translationY = sheetOffsetY.value
                val dragFrac = (sheetOffsetY.value / sheetHeightPx).coerceIn(0f, 1f)
                val s = 1f - dragFrac * 0.035f
                scaleX = s
                scaleY = s
            }
            .background(linColors.background)
    ) {
        AnimatedAmbientCanvas(
            palette = palette,
            audioEnergyFlow = audioEnergyFlow,
            mode = ambientMode,
            isPlaying = isPlaying,
            isActive = true,
            reactivity = ambientReactivity
        )

        // Absorb stray taps/swipes only while sheet is open (never trap touches on MiniPlayer during exit)
        if (isVisible && !isDismissing) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Header with Inline Marquee Between Minimize & Menu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onCollapse, modifier = Modifier.size(40.dp)) {
                    Icon(LinIcons.ChevronDown, "Collapse", tint = linColors.textPrimary, modifier = Modifier.size(22.dp))
                }
                Crossfade(
                    targetState = statusMessage != null,
                    animationSpec = tween(180),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    label = "npHeaderStatus"
                ) { hasStatus ->
                    if (hasStatus && statusMessage != null) {
                        LinStatusMarquee(
                            message = statusMessage,
                            accentColor = accentColor,
                            actionLabel = statusActionLabel,
                            onAction = onStatusAction,
                            onDismiss = onDismissStatus
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(APP_BRAND_NAME, style = LinTextStyles.brand, color = accentColor)
                            Text(
                                text = queueSnapshot.sourceLabel,
                                style = LinTextStyles.subtitle,
                                color = linColors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                IconButton(
                    onClick = { currentTrack?.let(onOpenTrackMenu) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(LinIcons.MoreVertical, "More options", tint = linColors.textPrimary, modifier = Modifier.size(20.dp))
                }
            }

            // Main Body with smooth AnimatedContent tab switching
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = activeDock,
                    transitionSpec = {
                        (fadeIn(tween(210, easing = FastOutSlowInEasing)) +
                            slideInVertically(tween(210, easing = FastOutSlowInEasing)) { it / 28 }) togetherWith
                            fadeOut(tween(150))
                    },
                    label = "npDockContent"
                ) { dock ->
                    when (dock) {
                        NowPlayingDockTab.PLAYER -> {
                            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                                if (currentTrack != null) {
                                    PlayerMainView(
                                    track = currentTrack,
                                    prevTrack = prevTrack,
                                    nextTrack = nextTrack,
                                    isPlaying = isPlaying,
                                    isBuffering = isBuffering,
                                    positionMsFlow = positionMsFlow,
                                    durationMsFlow = durationMsFlow,
                                    streamInfo = streamInfo,
                                    downloadState = downloadMap[currentTrack.videoId],
                                    accentColor = accentColor,
                                    shuffleEnabled = queueSnapshot.shuffleEnabled,
                                    repeatMode = queueSnapshot.repeatMode,
                                    onTogglePlayPause = onTogglePlayPause,
                                    onSkipNext = onSkipNext,
                                    onSkipToPreviousTrack = onSkipToPreviousTrack,
                                    onSkipPrevious = onSkipPrevious,
                                    onPrewarmSwipeTarget = onPrewarmSwipeTarget,
                                    onSeekTo = onSeekTo,
                                    onToggleShuffle = onToggleShuffle,
                                    onCycleRepeat = onCycleRepeat,
                                    onToggleLike = { onToggleLike(currentTrack) },
                                    onToggleLibrary = { onToggleLibrary(currentTrack) },
                                    onToggleDownload = { onToggleDownload(currentTrack) },
                                    onOpenAddToPlaylist = { onOpenAddToPlaylist(currentTrack) },
                                    onGoToAlbum = { onGoToAlbum(currentTrack) },
                                    onGoToArtist = { onGoToArtist(currentTrack) },
                                    onSheetVerticalDrag = { dy ->
                                        if (!isDismissing) {
                                            scope.launch {
                                                sheetOffsetY.snapTo((sheetOffsetY.value + dy).coerceAtLeast(0f))
                                            }
                                        }
                                    },
                                    onSheetVerticalDragEnd = {
                                        if (!isDismissing) {
                                            val threshold = minOf(with(density) { 110.dp.toPx() }, sheetHeightPx * 0.16f)
                                            dismissJob?.cancel()
                                            dismissJob = scope.launch {
                                                if (sheetOffsetY.value > threshold) {
                                                    isDismissing = true
                                                    sheetOffsetY.animateTo(
                                                        targetValue = sheetHeightPx,
                                                        animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing)
                                                    )
                                                    onCollapse()
                                                } else {
                                                    sheetOffsetY.animateTo(
                                                        targetValue = 0f,
                                                        animationSpec = spring(dampingRatio = 0.84f, stiffness = 360f)
                                                    )
                                                    dismissJob = null
                                                }
                                            }
                                        }
                                    },
                                    onSheetVerticalDragCancel = {
                                        if (!isDismissing) {
                                            dismissJob?.cancel()
                                            dismissJob = scope.launch {
                                                sheetOffsetY.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = spring(dampingRatio = 0.84f, stiffness = 360f)
                                                )
                                                dismissJob = null
                                            }
                                        }
                                    }
                                )
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(linLabel("not playing"), style = LinTextStyles.sectionHeading, color = linColors.textSecondary)
                                }
                            }
                            }
                        }

                        NowPlayingDockTab.UP_NEXT -> {
                            UpNextView(
                                queueSnapshot = queueSnapshot,
                                accentColor = accentColor,
                                mixChips = mixChips,
                                onSelectMixChip = onSelectMixChip,
                                onJumpTo = onJumpToQueueIndex,
                                onMoveItem = onMoveQueueItem,
                                onRemoveItem = onRemoveQueueItem,
                                onClearUpcoming = onClearUpcoming,
                                positionMsFlow = positionMsFlow,
                                onSaveAsPlaylist = { showSaveQueueDialog = true }
                            )
                        }

                        NowPlayingDockTab.LYRICS -> {
                            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                                LyricsView(
                                    track = currentTrack,
                                    lyrics = lyricsResult,
                                    isLoading = lyricsLoading,
                                    positionMsFlow = positionMsFlow,
                                    accentColor = accentColor,
                                    showOriginal = lyricsShowOriginal,
                                    showRomanized = lyricsShowRomanized,
                                    showTranslation = lyricsShowTranslation,
                                    translatedLines = translatedLyricsLines,
                                    translating = translatingLyrics,
                                    searchResults = lyricsSearchResults,
                                    searching = lyricsSearching,
                                    onSeekTo = onSeekTo,
                                    onAdjustOffset = onAdjustLyricsOffset,
                                    onResetOffset = onResetLyricsOffset,
                                    onSearchLyrics = onSearchLyrics,
                                    onSelectCandidate = onSelectLyricsCandidate,
                                    onSetDisplayFlags = onSetLyricsDisplayFlags,
                                    onShareCard = onShareLyricsCard,
                                    onModeToast = onLyricsModeToast
                                )
                            }
                        }

                        NowPlayingDockTab.RELATED -> {
                            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                                RelatedView(
                                    sections = relatedSections,
                                    currentVideoId = currentTrack?.videoId,
                                    accentColor = accentColor,
                                    downloadMap = downloadMap,
                                    onPlayTracks = onPlayTracks,
                                    onSwipeRightPlayNext = onSwipeRightPlayNext,
                                    onSwipeLeftAddToQueue = onSwipeLeftAddToQueue,
                                    onTrackMore = onOpenTrackMenu,
                                    onAlbumClick = onAlbumClick,
                                    onPlaylistClick = onPlaylistClick
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = linColors.glassBorder
            )

            // Tightened Bottom Lowercase Dock (no share; share lives in the top-right dot menu)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    NowPlayingDockTab.PLAYER to "player",
                    NowPlayingDockTab.UP_NEXT to "up next",
                    NowPlayingDockTab.LYRICS to "lyrics",
                    NowPlayingDockTab.RELATED to "related"
                ).forEach { (tab, label) ->
                    val selected = activeDock == tab
                    val tabColor by animateColorAsState(
                        targetValue = if (selected) linColors.textPrimary else linColors.textSecondary,
                        animationSpec = tween(180),
                        label = "npDockTabColor"
                    )
                    Text(
                        text = linLabel(label),
                        style = if (selected) LinTextStyles.navItemActive else LinTextStyles.navItem,
                        color = tabColor,
                        modifier = Modifier.clickable { activeDock = tab }.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }

                val sleepLabel = when (sleepTimerState) {
                    is SleepTimerState.Countdown -> linLabel("sleep ${formatDurationMs(sleepTimerState.remainingMs)}")
                    is SleepTimerState.EndOfTrack -> linLabel("sleep·end")
                    SleepTimerState.Off -> linLabel("sleep")
                }
                Text(
                    text = sleepLabel,
                    style = if (sleepTimerState !is SleepTimerState.Off) LinTextStyles.navItemActive else LinTextStyles.navItem,
                    color = if (sleepTimerState !is SleepTimerState.Off) accentColor else linColors.textSecondary,
                    modifier = Modifier.clickable(onClick = onOpenSleepTimer).padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }
    }

    if (showSaveQueueDialog) {
        PlaylistEditorDialog(
            title = "save queue as playlist",
            initialName = "LIN Queue",
            initialDescription = "",
            nameEditable = true,
            onDismiss = { showSaveQueueDialog = false },
            onConfirm = { name, _, _ ->
                onSaveQueueAsPlaylist(name)
                showSaveQueueDialog = false
            }
        )
    }
}

@Composable
private fun PlayerMainView(
    track: Track,
    prevTrack: Track?,
    nextTrack: Track?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMsFlow: StateFlow<Long>,
    durationMsFlow: StateFlow<Long>,
    streamInfo: StreamInfo?,
    downloadState: Int?,
    accentColor: Color,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipToPreviousTrack: () -> Unit,
    onSkipPrevious: () -> Unit,
    onPrewarmSwipeTarget: (Track) -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleLibrary: () -> Unit,
    onToggleDownload: () -> Unit,
    onOpenAddToPlaylist: () -> Unit,
    onGoToAlbum: () -> Unit,
    onGoToArtist: () -> Unit,
    onSheetVerticalDrag: (Float) -> Unit,
    onSheetVerticalDragEnd: () -> Unit,
    onSheetVerticalDragCancel: () -> Unit
) {
    val linColors = LinTheme.colors
    val density = LocalDensity.current
    val artScope = rememberCoroutineScope()
    val artDragX = remember { Animatable(0f) }

    val latestTrack by rememberUpdatedState(track)
    val latestPrevTrack by rememberUpdatedState(prevTrack)
    val latestNextTrack by rememberUpdatedState(nextTrack)
    val latestShuffle by rememberUpdatedState(shuffleEnabled)
    val latestOnSkipNext by rememberUpdatedState(onSkipNext)
    val latestOnSkipToPrev by rememberUpdatedState(onSkipToPreviousTrack)
    val latestOnSkipPrevFallback by rememberUpdatedState(onSkipPrevious)
    val latestOnPrewarm by rememberUpdatedState(onPrewarmSwipeTarget)
    val latestOnVerticalDrag by rememberUpdatedState(onSheetVerticalDrag)
    val latestOnVerticalDragEnd by rememberUpdatedState(onSheetVerticalDragEnd)
    val latestOnVerticalDragCancel by rememberUpdatedState(onSheetVerticalDragCancel)

    val artPlayScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.96f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 240f),
        label = "artPlayScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp)
            .padding(bottom = 42.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Centered swipeable carousel container: renders prev/current/next covers at 1:1 finger tracking
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter
        ) {
            val artSide = minOf(maxWidth * 0.96f, (maxHeight - 16.dp).coerceAtLeast(180.dp))
            val artSidePx = with(density) { artSide.toPx() }.coerceAtLeast(1f)
            val stridePx = artSidePx + with(density) { 18.dp.toPx() }

            Box(
                modifier = Modifier
                    .padding(bottom = 14.dp)
                    .fillMaxWidth()
                    .height(artSide)
                    .clipToBounds()
                    .pointerInput(artSidePx) {
                        var totalDragX = 0f
                        var totalDragY = 0f
                        var dragAxis = 0 // 0=none, 1=horizontal, 2=vertical
                        var prewarmedDir = 0 // -1=left/next, +1=right/prev

                        detectDragGestures(
                            onDragStart = {
                                totalDragX = 0f
                                totalDragY = 0f
                                dragAxis = 0
                                prewarmedDir = 0
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                totalDragX += dragAmount.x
                                totalDragY += dragAmount.y
                                if (dragAxis == 0 && (abs(totalDragX) > 14f || abs(totalDragY) > 14f)) {
                                    dragAxis = if (abs(totalDragX) >= abs(totalDragY)) 1 else 2
                                }
                                when (dragAxis) {
                                    1 -> {
                                        val curPrev = latestPrevTrack
                                        val curNext = latestNextTrack
                                        if (totalDragX < -18f && prewarmedDir != -1 && curNext != null) {
                                            prewarmedDir = -1
                                            latestOnPrewarm(curNext)
                                        } else if (totalDragX > 18f && prewarmedDir != 1 && curPrev != null) {
                                            prewarmedDir = 1
                                            latestOnPrewarm(curPrev)
                                        }
                                        val effectiveX = when {
                                            totalDragX < 0f && curNext == null -> totalDragX * 0.28f
                                            totalDragX > 0f && curPrev == null -> totalDragX * 0.28f
                                            else -> totalDragX.coerceIn(-stridePx, stridePx)
                                        }
                                        artScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                                            artDragX.snapTo(effectiveX)
                                        }
                                    }
                                    2 -> latestOnVerticalDrag(dragAmount.y)
                                }
                            },
                            onDragEnd = {
                                when (dragAxis) {
                                    1 -> {
                                        val skipThreshold = minOf(with(density) { 84.dp.toPx() }, artSidePx * 0.22f)
                                        val curPrev = latestPrevTrack
                                        val curNext = latestNextTrack
                                        when {
                                            totalDragX < -skipThreshold && curNext != null -> {
                                                val incomingStartX = (artDragX.value + stridePx).coerceIn(0f, stridePx)
                                                latestOnSkipNext()
                                                artScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                                                    artDragX.snapTo(incomingStartX)
                                                    artDragX.animateTo(
                                                        0f,
                                                        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 420f)
                                                    )
                                                }
                                            }
                                            totalDragX < -skipThreshold && latestShuffle -> {
                                                latestOnSkipNext()
                                                artScope.launch {
                                                    artDragX.animateTo(
                                                        0f,
                                                        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 420f)
                                                    )
                                                }
                                            }
                                            totalDragX > skipThreshold && curPrev != null -> {
                                                val incomingStartX = (artDragX.value - stridePx).coerceIn(-stridePx, 0f)
                                                latestOnSkipToPrev()
                                                artScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                                                    artDragX.snapTo(incomingStartX)
                                                    artDragX.animateTo(
                                                        0f,
                                                        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 420f)
                                                    )
                                                }
                                            }
                                            totalDragX > skipThreshold && latestShuffle -> {
                                                latestOnSkipPrevFallback()
                                                artScope.launch {
                                                    artDragX.animateTo(
                                                        0f,
                                                        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 420f)
                                                    )
                                                }
                                            }
                                            else -> {
                                                artScope.launch {
                                                    artDragX.animateTo(
                                                        0f,
                                                        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 420f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    2 -> latestOnVerticalDragEnd()
                                }
                            },
                            onDragCancel = {
                                if (dragAxis == 2) latestOnVerticalDragCancel()
                                artScope.launch {
                                    artDragX.animateTo(
                                        0f,
                                        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 420f)
                                    )
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val dragX = artDragX.value
                val safeArtScale = artPlayScale.coerceAtLeast(0.5f)

                if (prevTrack != null && dragX > 0.5f) {
                    AlbumCoverTile(
                        track = prevTrack,
                        artSide = artSide,
                        modifier = Modifier.graphicsLayer {
                            translationX = dragX - stridePx
                        }
                    )
                }

                if (nextTrack != null && dragX < -0.5f) {
                    AlbumCoverTile(
                        track = nextTrack,
                        artSide = artSide,
                        modifier = Modifier.graphicsLayer {
                            translationX = dragX + stridePx
                        }
                    )
                }

                AlbumCoverTile(
                    track = latestTrack,
                    artSide = artSide,
                    modifier = Modifier.graphicsLayer {
                        translationX = dragX
                        scaleX = safeArtScale
                        scaleY = safeArtScale
                    }
                )
            }
        }

        // Full-width controls block with comfortable bottom spacing above dock bar
        Column(modifier = Modifier.fillMaxWidth()) {
            AnimatedContent(
                targetState = track,
                contentKey = { it.videoId },
                transitionSpec = {
                    fadeIn(tween(150, easing = FastOutSlowInEasing)) togetherWith fadeOut(tween(100))
                },
                label = "npTrackHeader"
            ) { displayTrack ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = displayTrack.title,
                        style = LinTextStyles.sectionHeading.copy(
                            fontSize = 24.sp,
                            lineHeight = 28.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = linColors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(onClick = onGoToAlbum)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = displayTrack.artistDisplay,
                        style = LinTextStyles.navItem.copy(fontSize = 16.sp),
                        color = linColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(onClick = onGoToArtist)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Dedicated Action Strip (Download, Library, Playlist, Like)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val downloadLabel = when (downloadState) {
                    2 -> "saved"
                    0, 1 -> "saving"
                    else -> "download"
                }
                val isDownloadedOrActive = downloadState == 0 || downloadState == 1 || downloadState == 2
                val dlTint by animateColorAsState(
                    targetValue = if (isDownloadedOrActive) accentColor else linColors.textSecondary,
                    animationSpec = tween(220),
                    label = "dlTint"
                )
                val libTint by animateColorAsState(
                    targetValue = if (track.inLibrary) accentColor else linColors.textSecondary,
                    animationSpec = tween(220),
                    label = "libTint"
                )
                val likeTint by animateColorAsState(
                    targetValue = if (track.isLiked) accentColor else linColors.textSecondary,
                    animationSpec = tween(220),
                    label = "likeTint"
                )

                PlayerQuickActionButton(
                    icon = LinIcons.Download,
                    label = downloadLabel,
                    tint = dlTint,
                    onClick = onToggleDownload
                )
                PlayerQuickActionButton(
                    icon = if (track.inLibrary) LinIcons.Check else LinIcons.Plus,
                    label = if (track.inLibrary) "in library" else "library",
                    tint = libTint,
                    onClick = onToggleLibrary
                )
                PlayerQuickActionButton(
                    icon = LinIcons.PlaylistAdd,
                    label = "playlist",
                    tint = linColors.textSecondary,
                    onClick = onOpenAddToPlaylist
                )
                PlayerQuickActionButton(
                    icon = if (track.isLiked) LinIcons.HeartFilled else LinIcons.Heart,
                    label = if (track.isLiked) "liked" else "like",
                    tint = likeTint,
                    onClick = onToggleLike
                )
            }

            Spacer(Modifier.height(12.dp))

            PlayerScrubberBlock(
                positionMsFlow = positionMsFlow,
                durationMsFlow = durationMsFlow,
                fallbackDurationMs = track.durationMs,
                isBuffering = isBuffering,
                streamInfo = streamInfo,
                onSeekTo = onSeekTo
            )

            Spacer(Modifier.height(16.dp))

            // Prominent Transport Controls with Animated Accent Indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val shuffleBg by animateColorAsState(
                    targetValue = if (shuffleEnabled) accentColor.copy(alpha = 0.16f) else Color.Transparent,
                    animationSpec = tween(220),
                    label = "shuffleBg"
                )
                val shuffleBorder by animateColorAsState(
                    targetValue = if (shuffleEnabled) accentColor.copy(alpha = 0.50f) else Color.Transparent,
                    animationSpec = tween(220),
                    label = "shuffleBorder"
                )
                val shuffleTint by animateColorAsState(
                    targetValue = if (shuffleEnabled) accentColor else linColors.textSecondary,
                    animationSpec = tween(220),
                    label = "shuffleTint"
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(shuffleBg)
                        .border(1.dp, shuffleBorder)
                        .clickable(onClick = onToggleShuffle),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            LinIcons.Shuffle,
                            "Shuffle",
                            tint = shuffleTint,
                            modifier = Modifier.size(22.dp)
                        )
                        if (shuffleEnabled) {
                            Spacer(Modifier.height(3.dp))
                            Box(modifier = Modifier.size(4.dp).background(accentColor))
                        }
                    }
                }

                IconButton(onClick = onSkipPrevious, modifier = Modifier.size(52.dp)) {
                    Icon(LinIcons.SkipBack, "Previous", tint = linColors.textPrimary, modifier = Modifier.size(28.dp))
                }

                val playButtonScale by animateFloatAsState(
                    targetValue = if (isPlaying) 1f else 0.95f,
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f),
                    label = "playBtnScale"
                )
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .graphicsLayer {
                            val safeBtnScale = playButtonScale.coerceAtLeast(0.5f)
                            scaleX = safeBtnScale
                            scaleY = safeBtnScale
                        }
                        .background(linColors.btnInvertBg)
                        .clickable(onClick = onTogglePlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) LinIcons.Pause else LinIcons.Play,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = linColors.btnInvertText,
                        modifier = Modifier.size(26.dp)
                    )
                }

                IconButton(onClick = onSkipNext, modifier = Modifier.size(52.dp)) {
                    Icon(LinIcons.SkipForward, "Next", tint = linColors.textPrimary, modifier = Modifier.size(28.dp))
                }

                val repeatActive = repeatMode != RepeatMode.OFF
                val repeatBg by animateColorAsState(
                    targetValue = if (repeatActive) accentColor.copy(alpha = 0.16f) else Color.Transparent,
                    animationSpec = tween(220),
                    label = "repeatBg"
                )
                val repeatBorder by animateColorAsState(
                    targetValue = if (repeatActive) accentColor.copy(alpha = 0.50f) else Color.Transparent,
                    animationSpec = tween(220),
                    label = "repeatBorder"
                )
                val repeatTint by animateColorAsState(
                    targetValue = if (repeatActive) accentColor else linColors.textSecondary,
                    animationSpec = tween(220),
                    label = "repeatTint"
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(repeatBg)
                        .border(1.dp, repeatBorder)
                        .clickable(onClick = onCycleRepeat),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                LinIcons.Repeat,
                                "Repeat",
                                tint = repeatTint,
                                modifier = Modifier.size(22.dp)
                            )
                            if (repeatMode == RepeatMode.ONE) {
                                Text("1", style = LinTextStyles.tagPill, color = accentColor)
                            }
                        }
                        if (repeatActive) {
                            Spacer(Modifier.height(3.dp))
                            Box(modifier = Modifier.size(4.dp).background(accentColor))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumCoverTile(
    track: Track,
    artSide: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val linColors = LinTheme.colors
    val context = LocalContext.current
    Box(
        modifier = modifier
            .size(artSide)
            .clip(LinSharpShape)
            .background(linColors.glassBorder)
    ) {
        val hiResUrl = remember(track.thumbnailUrl) { highResArtworkUrl(track.thumbnailUrl) }
        if (!hiResUrl.isNullOrBlank()) {
            val request = remember(hiResUrl) {
                ImageRequest.Builder(context)
                    .data(hiResUrl)
                    .size(Size.ORIGINAL)
                    .precision(Precision.EXACT)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun PlayerScrubberBlock(
    positionMsFlow: StateFlow<Long>,
    durationMsFlow: StateFlow<Long>,
    fallbackDurationMs: Long,
    isBuffering: Boolean,
    streamInfo: StreamInfo?,
    onSeekTo: (Long) -> Unit
) {
    val linColors = LinTheme.colors
    val positionMs by positionMsFlow.collectAsStateWithLifecycle()
    val rawDurationMs by durationMsFlow.collectAsStateWithLifecycle()
    val effectiveDuration = rawDurationMs.coerceAtLeast(fallbackDurationMs)

    Column {
        LinScrubber(
            positionMs = positionMs,
            durationMs = effectiveDuration,
            onSeekTo = onSeekTo,
            trackHeight = 3.dp,
            modifier = Modifier.height(28.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(formatDurationMs(positionMs), style = LinTextStyles.subtitle, color = linColors.textSecondary)
            Text(
                text = when {
                    isBuffering -> linLabel("buffering")
                    streamInfo != null -> streamInfo.codecLabel
                    else -> ""
                },
                style = LinTextStyles.tagPill,
                color = linColors.textSecondary
            )
            Text(
                formatDurationMs(effectiveDuration),
                style = LinTextStyles.subtitle,
                color = linColors.textSecondary
            )
        }
    }
}

@Composable
private fun PlayerQuickActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text = linLabel(label),
            style = LinTextStyles.tagPill.copy(fontSize = 11.5.sp),
            color = tint
        )
    }
}

@Composable
private fun UpNextView(
    queueSnapshot: QueueSnapshot,
    accentColor: Color,
    mixChips: List<MixChip>,
    onSelectMixChip: (MixChip) -> Unit,
    onJumpTo: (Int) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onClearUpcoming: () -> Unit,
    positionMsFlow: StateFlow<Long>,
    onSaveAsPlaylist: () -> Unit
) {
    val linColors = LinTheme.colors
    val positionMs by positionMsFlow.collectAsStateWithLifecycle()
    val remainingTotalMs = queueSnapshot.remainingDurationMs(positionMs)
    Column(modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Up Next (${queueSnapshot.items.size})", style = LinTextStyles.sectionHeading, color = linColors.textPrimary)
                if (remainingTotalMs > 0L) {
                    Text(
                        text = linLabel("${formatDurationMs(remainingTotalMs)} remaining"),
                        style = LinTextStyles.subtitle,
                        color = linColors.textSecondary
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LinOutlinedButton("save", onClick = onSaveAsPlaylist)
                LinOutlinedButton("clear", onClick = onClearUpcoming)
            }
        }

        if (mixChips.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                items(mixChips) { chip ->
                    LinTagPill(
                        text = chip.title,
                        active = chip.isSelected,
                        onClick = { onSelectMixChip(chip) }
                    )
                }
            }
        }

        if (queueSnapshot.items.isEmpty()) {
            Text(
                linLabel("queue is empty."),
                style = LinTextStyles.navItem,
                color = linColors.textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            return
        }

        val queueListState = rememberLazyListState()
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = queueListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                itemsIndexed(queueSnapshot.items, key = { idx, t -> "q_${idx}_${t.videoId}" }) { idx, track ->
                    val isCurrent = idx == queueSnapshot.currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(linColors.queueItemBg)
                            .border(width = if (isCurrent) 2.dp else 0.dp, color = if (isCurrent) accentColor else Color.Transparent)
                            .clickable { onJumpTo(idx) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.padding(end = 10.dp)) {
                            Text(
                                "↑",
                                style = LinTextStyles.subtitle,
                                color = if (idx > 0) linColors.textSecondary else Color.Transparent,
                                modifier = Modifier.clickable(enabled = idx > 0) { onMoveItem(idx, idx - 1) }.padding(2.dp)
                            )
                            Text(
                                "↓",
                                style = LinTextStyles.subtitle,
                                color = if (idx < queueSnapshot.items.lastIndex) linColors.textSecondary else Color.Transparent,
                                modifier = Modifier.clickable(enabled = idx < queueSnapshot.items.lastIndex) { onMoveItem(idx, idx + 1) }.padding(2.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                track.title,
                                style = if (isCurrent) LinTextStyles.navItemActive else LinTextStyles.navItem,
                                color = if (isCurrent) accentColor else linColors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                track.artistDisplay,
                                style = LinTextStyles.subtitle,
                                color = linColors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { onRemoveItem(idx) }) {
                            Icon(LinIcons.Trash, "Remove", tint = linColors.textSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            LinLazyColumnScrollbar(
                listState = queueListState,
                accentColor = accentColor,
                modifier = Modifier.align(Alignment.CenterEnd),
                topPadding = 8.dp,
                bottomPadding = 16.dp,
                labelProvider = { idx ->
                    if (queueSnapshot.items.isNotEmpty()) {
                        "${idx.coerceIn(0, queueSnapshot.items.lastIndex) + 1} / ${queueSnapshot.items.size}"
                    } else null
                }
            )
        }
    }
}

@Composable
private fun LyricsView(
    track: Track?,
    lyrics: LyricsResult?,
    isLoading: Boolean,
    positionMsFlow: StateFlow<Long>,
    accentColor: Color,
    showOriginal: Boolean,
    showRomanized: Boolean,
    showTranslation: Boolean,
    translatedLines: List<String>,
    translating: Boolean,
    searchResults: List<LyricsSearchCandidate>,
    searching: Boolean,
    onSeekTo: (Long) -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onSearchLyrics: (String) -> Unit,
    onSelectCandidate: (LyricsSearchCandidate) -> Unit,
    onSetDisplayFlags: (Boolean, Boolean, Boolean) -> Unit,
    onShareCard: (Track, List<String>, List<String?>, Int) -> Unit,
    onModeToast: (String) -> Unit = {}
) {
    val linColors = LinTheme.colors
    val listState = rememberLazyListState()
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    var lastManualDragEndMs by remember { mutableLongStateOf(0L) }
    var wasDragged by remember { mutableStateOf(false) }

    var showSearchPicker by remember { mutableStateOf(false) }
    var searchQuery by remember(track?.videoId) {
        mutableStateOf(listOfNotNull(track?.title, track?.artistDisplay).joinToString(" "))
    }
    var cardSelectMode by remember(track?.videoId) { mutableStateOf(false) }
    var selectedLineIndices by remember(track?.videoId, lyrics?.source) { mutableStateOf<List<Int>>(emptyList()) }
    var plainReadingMode by remember(lyrics?.videoId) { mutableStateOf(false) }

    var lyricsTapCount by remember { mutableIntStateOf(0) }
    var lastLyricsTapMs by remember { mutableLongStateOf(0L) }
    var pendingSeekJob by remember { mutableStateOf<Job?>(null) }
    val lyricsScope = rememberCoroutineScope()
    val areaInteraction = remember { MutableInteractionSource() }
    val latestHasSynced by rememberUpdatedState(lyrics != null && lyrics.syncedLines.isNotEmpty())

    DisposableEffect(Unit) {
        onDispose { pendingSeekJob?.cancel() }
    }

    fun handleLyricsTap(onSingleTapSeek: (() -> Unit)? = null) {
        val now = System.currentTimeMillis()
        val nextCount = if (now - lastLyricsTapMs <= 550L) lyricsTapCount + 1 else 1
        lastLyricsTapMs = now
        if (nextCount >= 3) {
            lyricsTapCount = 0
            pendingSeekJob?.cancel()
            pendingSeekJob = null
            if (latestHasSynced) {
                plainReadingMode = !plainReadingMode
                onModeToast(if (plainReadingMode) "plain lyrics view" else "synced lyrics view")
            }
        } else {
            lyricsTapCount = nextCount
            if (nextCount >= 2) {
                pendingSeekJob?.cancel()
                pendingSeekJob = null
            } else if (onSingleTapSeek != null) {
                pendingSeekJob?.cancel()
                pendingSeekJob = lyricsScope.launch {
                    delay(260L)
                    if (lyricsTapCount == 1) {
                        onSingleTapSeek()
                    }
                }
            }
        }
    }

    LaunchedEffect(isDragged) {
        if (wasDragged && !isDragged) {
            lastManualDragEndMs = System.currentTimeMillis()
        }
        wasDragged = isDragged
    }

    val stateKey = when {
        isLoading -> "loading"
        lyrics == null || (lyrics.syncedLines.isEmpty() && lyrics.plainText.isNullOrBlank()) -> "empty"
        lyrics.syncedLines.isNotEmpty() && !plainReadingMode -> "synced_${lyrics.videoId}_${lyrics.source}"
        else -> "plain_${lyrics.videoId}_${lyrics.source}_$plainReadingMode"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Minimal Borderless Editorial Top Bar
        if (track != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (lyrics != null && lyrics.syncedLines.isNotEmpty() && !plainReadingMode) {
                    val offSec = lyrics.syncOffsetMs / 1000.0
                    val offLabel = when {
                        lyrics.syncOffsetMs > 0L -> String.format("+%.1fs", offSec)
                        lyrics.syncOffsetMs < 0L -> String.format("%.1fs", offSec)
                        else -> "0.0s"
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "−",
                            style = LinTextStyles.navItem,
                            color = linColors.textSecondary,
                            modifier = Modifier
                                .clickable { onAdjustOffset(-100L) }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                        Text(
                            text = offLabel,
                            style = LinTextStyles.tagPill,
                            color = if (lyrics.syncOffsetMs != 0L) accentColor else linColors.textSecondary,
                            modifier = Modifier
                                .clickable { onResetOffset() }
                                .padding(horizontal = 2.dp, vertical = 2.dp)
                        )
                        Text(
                            text = "+",
                            style = LinTextStyles.navItem,
                            color = linColors.textSecondary,
                            modifier = Modifier
                                .clickable { onAdjustOffset(100L) }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showRomanized || showTranslation || !showOriginal) {
                        LyricsTopBarAction(
                            label = "orig",
                            active = showOriginal,
                            accentColor = accentColor,
                            onClick = {
                                val nextOrig = !showOriginal
                                val safeOrig = if (!nextOrig && !showRomanized && !showTranslation) true else nextOrig
                                onSetDisplayFlags(safeOrig, showRomanized, showTranslation)
                            }
                        )
                    }
                    LyricsTopBarAction(
                        label = "rom",
                        active = showRomanized,
                        accentColor = accentColor,
                        onClick = { onSetDisplayFlags(showOriginal, !showRomanized, showTranslation) }
                    )
                    LyricsTopBarAction(
                        label = if (translating) "tr..." else "tr",
                        active = showTranslation,
                        accentColor = accentColor,
                        onClick = { onSetDisplayFlags(showOriginal, showRomanized, !showTranslation) }
                    )
                    LyricsTopBarAction(
                        label = "fix",
                        active = false,
                        accentColor = accentColor,
                        onClick = {
                            onSearchLyrics(searchQuery)
                            showSearchPicker = true
                        }
                    )
                    if (lyrics != null && (lyrics.syncedLines.isNotEmpty() || !lyrics.plainText.isNullOrBlank())) {
                        LyricsTopBarAction(
                            label = if (cardSelectMode) "share (${selectedLineIndices.size})" else "card",
                            active = cardSelectMode,
                            accentColor = accentColor,
                            onClick = {
                                if (!cardSelectMode) {
                                    cardSelectMode = true
                                    selectedLineIndices = emptyList()
                                } else if (selectedLineIndices.isNotEmpty()) {
                                    val allLines = if (lyrics.syncedLines.isNotEmpty()) {
                                        lyrics.syncedLines.map { it.text }
                                    } else {
                                        lyrics.plainText?.lines().orEmpty()
                                    }
                                    val sortedIdx = selectedLineIndices.sorted()
                                    val chosenText = sortedIdx.mapNotNull { allLines.getOrNull(it) }.filter { it.isNotBlank() }
                                    val chosenRom = chosenText.map { if (showRomanized) LyricsTextProcessor.romanizeIfSupported(it) else null }
                                    if (chosenText.isNotEmpty()) {
                                        onShareCard(track, chosenText, chosenRom, accentColor.toArgb())
                                    }
                                    cardSelectMode = false
                                    selectedLineIndices = emptyList()
                                } else {
                                    cardSelectMode = false
                                }
                            }
                        )
                    }
                }
            }
            HorizontalDivider(
                thickness = 0.5.dp,
                color = linColors.glassBorder.copy(alpha = 0.45f)
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { viewportHeightPx = it.height }
                .clickable(interactionSource = areaInteraction, indication = null) {
                    handleLyricsTap(onSingleTapSeek = null)
                }
        ) {
            AnimatedContent(
                targetState = stateKey,
                transitionSpec = {
                    fadeIn(tween(220, easing = FastOutSlowInEasing)) togetherWith fadeOut(tween(160))
                },
                modifier = Modifier.fillMaxSize(),
                label = "lyricsContent"
            ) { key ->
                when {
                    key == "loading" -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(linLabel("loading lyrics..."), style = LinTextStyles.navItem, color = linColors.textSecondary)
                        }
                    }

                    key == "empty" || lyrics == null -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(linLabel("no lyrics found for this track."), style = LinTextStyles.navItem, color = linColors.textSecondary)
                            if (track != null) {
                                Spacer(Modifier.height(12.dp))
                                LinOutlinedButton("search lrclib manually", onClick = {
                                    onSearchLyrics(searchQuery)
                                    showSearchPicker = true
                                })
                            }
                        }
                    }

                    lyrics.syncedLines.isNotEmpty() && !plainReadingMode -> {
                        val positionMs by positionMsFlow.collectAsStateWithLifecycle()
                        val activeIdx = LrcParser.findActiveLineIndex(lyrics.syncedLines, positionMs, lyrics.syncOffsetMs)

                        LaunchedEffect(lyrics.videoId, plainReadingMode) {
                            lastManualDragEndMs = 0L
                            listState.scrollToItem(0)
                        }

                        LaunchedEffect(activeIdx, viewportHeightPx) {
                            val userRecentlyScrolled = isDragged || (System.currentTimeMillis() - lastManualDragEndMs < 3_500L)
                            if (activeIdx >= 0 && !userRecentlyScrolled && !cardSelectMode) {
                                val focusOffsetPx = if (viewportHeightPx > 0) -(viewportHeightPx * 0.28f).roundToInt() else -160
                                listState.animateScrollToItem(index = activeIdx, scrollOffset = focusOffsetPx)
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 24.dp, bottom = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            itemsIndexed(lyrics.syncedLines, key = { idx, line -> "lyr_${idx}_${line.timeMs}" }) { idx, line ->
                                val isActive = idx == activeIdx
                                val isPickedForCard = idx in selectedLineIndices
                                val distance = if (activeIdx < 0) 0 else abs(idx - activeIdx)
                                val isPast = activeIdx >= 0 && idx < activeIdx

                                val targetScale = when {
                                    cardSelectMode -> 1.0f
                                    isActive -> 1.0f
                                    distance == 1 -> 0.94f
                                    else -> 0.90f
                                }
                                val targetAlpha = when {
                                    cardSelectMode -> if (isPickedForCard) 1.0f else 0.72f
                                    isActive -> 1.0f
                                    distance == 1 -> if (isPast) 0.48f else 0.64f
                                    else -> if (isPast) 0.26f else 0.38f
                                }
                                val targetLift = if (isActive && !cardSelectMode) -3f else 0f

                                val lineColor by animateColorAsState(
                                    targetValue = when {
                                        isPickedForCard -> accentColor
                                        isActive && !cardSelectMode -> accentColor
                                        cardSelectMode -> linColors.textPrimary
                                        else -> linColors.textSecondary
                                    },
                                    animationSpec = tween(260),
                                    label = "lyricColor"
                                )
                                val lineScale by animateFloatAsState(
                                    targetValue = targetScale,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 300f),
                                    label = "lyricScale"
                                )
                                val lineAlpha by animateFloatAsState(
                                    targetValue = targetAlpha,
                                    animationSpec = tween(260),
                                    label = "lyricAlpha"
                                )
                                val lineLift by animateFloatAsState(
                                    targetValue = targetLift,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 280f),
                                    label = "lyricLift"
                                )
                                val barAlpha by animateFloatAsState(
                                    targetValue = if (isPickedForCard || (isActive && !cardSelectMode)) 1f else 0f,
                                    animationSpec = tween(220),
                                    label = "lyricBarAlpha"
                                )
                                val distantBlurMod = if (!cardSelectMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && distance >= 2) {
                                    Modifier.blur(1.5.dp)
                                } else {
                                    Modifier
                                }
                                val romanized = remember(line.text, showRomanized) {
                                    if (showRomanized) LyricsTextProcessor.romanizeIfSupported(line.text) else null
                                }
                                val translated = if (showTranslation) translatedLines.getOrNull(idx)?.takeIf { it.isNotBlank() } else null
                                val rowInteraction = remember(idx) { MutableInteractionSource() }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min)
                                        .graphicsLayer {
                                            transformOrigin = TransformOrigin(0f, 0.5f)
                                            val safeScale = lineScale.coerceAtLeast(0.5f)
                                            scaleX = safeScale
                                            scaleY = safeScale
                                            translationY = lineLift
                                            alpha = lineAlpha.coerceIn(0f, 1f)
                                        }
                                        .clickable(interactionSource = rowInteraction, indication = null) {
                                            if (cardSelectMode) {
                                                if (line.text.isNotBlank()) {
                                                    selectedLineIndices = if (idx in selectedLineIndices) {
                                                        selectedLineIndices - idx
                                                    } else if (selectedLineIndices.size < 6) {
                                                        selectedLineIndices + idx
                                                    } else {
                                                        selectedLineIndices
                                                    }
                                                }
                                            } else {
                                                handleLyricsTap {
                                                    lastManualDragEndMs = 0L
                                                    onSeekTo((line.timeMs - lyrics.syncOffsetMs).coerceAtLeast(0L))
                                                }
                                            }
                                        }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.5.dp)
                                            .fillMaxHeight()
                                            .background(accentColor.copy(alpha = barAlpha.coerceIn(0f, 1f)))
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    if (line.text.isBlank()) {
                                        LyricsInstrumentalDots(color = lineColor, active = isActive)
                                    } else {
                                        Column(modifier = Modifier.weight(1f).then(distantBlurMod), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            if (showOriginal || (romanized == null && translated == null)) {
                                                Text(
                                                    text = line.text,
                                                    style = LinTextStyles.sectionHeading.copy(
                                                        fontSize = 23.sp,
                                                        lineHeight = 29.sp,
                                                        fontWeight = if (isActive || isPickedForCard) FontWeight.SemiBold else FontWeight.Normal
                                                    ),
                                                    color = lineColor
                                                )
                                            }
                                            romanized?.let { rom ->
                                                Text(
                                                    text = rom,
                                                    style = LinTextStyles.navItem.copy(fontSize = 15.sp, lineHeight = 20.sp),
                                                    color = if (showOriginal) lineColor.copy(alpha = 0.78f) else lineColor
                                                )
                                            }
                                            translated?.let { tr ->
                                                Text(
                                                    text = tr,
                                                    style = LinTextStyles.subtitle.copy(fontSize = 14.sp, lineHeight = 19.sp),
                                                    color = if (isActive || isPickedForCard) linColors.textPrimary else linColors.textSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            item {
                                Text(
                                    "source: ${lyrics.source}",
                                    style = LinTextStyles.tagPill,
                                    color = linColors.textSecondary,
                                    modifier = Modifier.padding(start = 12.dp, top = 8.dp)
                                )
                            }
                        }
                    }

                    else -> {
                        val plainLines = remember(lyrics.videoId, lyrics.source, lyrics.syncedLines, lyrics.plainText) {
                            if (lyrics.syncedLines.isNotEmpty()) {
                                lyrics.syncedLines.map { it.text }
                            } else {
                                lyrics.plainText?.lines().orEmpty()
                            }
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 18.dp, bottom = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(plainLines, key = { idx, _ -> "pl_lyr_$idx" }) { idx, textLine ->
                                if (textLine.isBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                } else {
                                    val isPicked = idx in selectedLineIndices
                                    val rom = remember(textLine, showRomanized) {
                                        if (showRomanized) LyricsTextProcessor.romanizeIfSupported(textLine) else null
                                    }
                                    val tr = if (showTranslation) translatedLines.getOrNull(idx)?.takeIf { it.isNotBlank() } else null
                                    val plainInteraction = remember(idx) { MutableInteractionSource() }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(interactionSource = plainInteraction, indication = null) {
                                                if (cardSelectMode) {
                                                    selectedLineIndices = if (idx in selectedLineIndices) {
                                                        selectedLineIndices - idx
                                                    } else if (selectedLineIndices.size < 6) {
                                                        selectedLineIndices + idx
                                                    } else selectedLineIndices
                                                } else {
                                                    handleLyricsTap(onSingleTapSeek = null)
                                                }
                                            },
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        if (showOriginal || (rom == null && tr == null)) {
                                            Text(
                                                text = textLine,
                                                style = LinTextStyles.navItem.copy(fontSize = 18.sp, lineHeight = 26.sp),
                                                color = if (isPicked) accentColor else linColors.textPrimary
                                            )
                                        }
                                        rom?.let {
                                            Text(
                                                text = it,
                                                style = LinTextStyles.subtitle.copy(fontSize = 14.sp),
                                                color = linColors.textSecondary
                                            )
                                        }
                                        tr?.let {
                                            Text(
                                                text = it,
                                                style = LinTextStyles.subtitle.copy(fontSize = 14.sp),
                                                color = accentColor.copy(alpha = 0.9f)
                                            )
                                        }
                                    }
                                }
                            }
                            item {
                                Spacer(Modifier.height(12.dp))
                                Text("source: ${lyrics.source}", style = LinTextStyles.tagPill, color = linColors.textSecondary)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSearchPicker) {
        AlertDialog(
            onDismissRequest = { showSearchPicker = false },
            containerColor = linColors.background,
            title = { Text(linLabel("pick lyrics from lrclib"), style = LinTextStyles.sectionHeading, color = linColors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            LinUnderlineTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = "song artist..."
                            )
                        }
                        LinOutlinedButton("search", onClick = { onSearchLyrics(searchQuery) })
                    }
                    if (searching) {
                        Text(linLabel("searching lrclib..."), style = LinTextStyles.subtitle, color = linColors.textSecondary)
                    } else if (searchResults.isEmpty()) {
                        Text(linLabel("no matches found"), style = LinTextStyles.subtitle, color = linColors.textSecondary)
                    } else {
                        LazyColumn(modifier = Modifier.height(260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(searchResults.take(15), key = { it.id }) { candidate ->
                                val hasSynced = !candidate.syncedLyrics.isNullOrBlank()
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, linColors.glassBorder, LinSharpShape)
                                        .clickable {
                                            onSelectCandidate(candidate)
                                            showSearchPicker = false
                                        }
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = candidate.trackName,
                                        style = LinTextStyles.navItemActive,
                                        color = linColors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${candidate.artistName} · ${if (hasSynced) "synced LRC" else "plain text"}",
                                        style = LinTextStyles.subtitle,
                                        color = if (hasSynced) accentColor else linColors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearchPicker = false }) {
                    Text(linLabel("close"), color = linColors.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun LyricsTopBarAction(
    label: String,
    active: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val linColors = LinTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp)
    ) {
        Text(
            text = linLabel(label),
            style = LinTextStyles.tagPill,
            color = if (active) accentColor else linColors.textSecondary
        )
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(1.5.dp)
                .background(if (active) accentColor else Color.Transparent)
        )
    }
}

@Composable
private fun LyricsInstrumentalDots(color: Color, active: Boolean) {
    val pulseAlpha = if (active) {
        val transition = rememberInfiniteTransition(label = "lyricDotsPulse")
        transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                repeatMode = ComposeRepeatMode.Reverse
            ),
            label = "dotsAlpha"
        ).value
    } else {
        0.55f
    }
    Text(
        text = "• • •",
        style = LinTextStyles.sectionHeading.copy(fontSize = 22.sp, letterSpacing = 4.sp),
        color = color.copy(alpha = pulseAlpha.coerceIn(0f, 1f))
    )
}

@Composable
private fun RelatedView(
    sections: List<HomeSection>,
    currentVideoId: String?,
    accentColor: Color,
    downloadMap: Map<String, Int>,
    onPlayTracks: (List<Track>, Int, String) -> Unit,
    onSwipeRightPlayNext: (Track) -> Unit,
    onSwipeLeftAddToQueue: (Track) -> Unit,
    onTrackMore: (Track) -> Unit,
    onAlbumClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit
) {
    val linColors = LinTheme.colors
    if (sections.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(linLabel("no related items loaded."), style = LinTextStyles.navItem, color = linColors.textSecondary)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        sections.forEach { sec ->
            item {
                Text(
                    linLabel(sec.title.lowercase()),
                    style = LinTextStyles.sectionHeading,
                    color = linColors.textPrimary,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                )
            }
            val songs = sec.items.mapNotNull { (it as? HomeItem.SongItem)?.track }
            if (songs.isNotEmpty()) {
                itemsIndexed(songs) { idx, track ->
                    SwipeableTrackRow(
                        track = track,
                        isCurrentTrack = track.videoId == currentVideoId,
                        accentColor = accentColor,
                        isDownloaded = downloadMap[track.videoId] == 2,
                        showThumbnail = true,
                        onClick = { onPlayTracks(songs, idx, sec.title.lowercase()) },
                        onSwipeRightPlayNext = { onSwipeRightPlayNext(track) },
                        onSwipeLeftAddToQueue = { onSwipeLeftAddToQueue(track) },
                        onMoreClick = { onTrackMore(track) }
                    )
                }
            }
            val albums = sec.items.mapNotNull { (it as? HomeItem.AlbumItem)?.album }
            if (albums.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(albums) { alb ->
                            LinOutlinedButton(alb.title, onClick = { onAlbumClick(alb.browseId) })
                        }
                    }
                }
            }
            val pls = sec.items.mapNotNull { (it as? HomeItem.PlaylistItem)?.playlist }
            if (pls.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(pls) { pl ->
                            LinOutlinedButton(pl.title, onClick = { onPlaylistClick(pl.browseId) })
                        }
                    }
                }
            }
        }
    }
}
