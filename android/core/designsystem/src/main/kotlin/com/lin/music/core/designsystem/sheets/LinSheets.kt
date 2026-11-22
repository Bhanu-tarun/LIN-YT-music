package com.lin.music.core.designsystem.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.lin.music.core.designsystem.components.LinInvertedButton
import com.lin.music.core.designsystem.components.LinOutlinedButton
import com.lin.music.core.designsystem.components.LinSquareToggle
import com.lin.music.core.designsystem.components.LinTagPill
import com.lin.music.core.designsystem.components.formatDurationMs
import com.lin.music.core.designsystem.icons.LinIcons
import com.lin.music.core.designsystem.theme.LinSharpShape
import com.lin.music.core.designsystem.theme.LinTextStyles
import com.lin.music.core.designsystem.theme.LinTheme
import com.lin.music.core.designsystem.theme.linLabel
import com.lin.music.core.model.ArtistRef
import com.lin.music.core.model.PlaylistSummary
import com.lin.music.core.model.SleepTimerState
import com.lin.music.core.model.Track

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionSheet(
    track: Track,
    isDownloaded: Boolean,
    editablePlaylistName: String? = null,
    onDismiss: () -> Unit,
    onToggleLike: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onStartMix: () -> Unit,
    onToggleLibrary: () -> Unit,
    onOpenAddToPlaylist: () -> Unit,
    onDeleteFromPlaylist: (() -> Unit)? = null,
    onGoToAlbum: () -> Unit,
    onGoToArtist: () -> Unit,
    onToggleDownload: () -> Unit,
    onManageTags: () -> Unit,
    onShare: () -> Unit,
    onShareAtTimestamp: (() -> Unit)? = null,
    shareTimestampLabel: String? = null,
    isPinnedToSpeedDial: Boolean = false,
    onTogglePinSpeedDial: (() -> Unit)? = null
) {
    val linColors = LinTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = LinSharpShape,
        containerColor = linColors.background,
        contentColor = linColors.textPrimary,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, linColors.glassBorder)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            // Track Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(linColors.glassBorder)
                ) {
                    if (!track.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = track.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = LinTextStyles.trackTitlePlaying,
                        color = linColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOfNotNull(
                            track.artistDisplay,
                            track.album?.title?.takeIf { it.isNotBlank() },
                            track.formattedDuration.takeIf { it.isNotBlank() }
                        ).joinToString(" · "),
                        style = LinTextStyles.subtitle,
                        color = linColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onToggleLike) {
                    Icon(
                        imageVector = if (track.isLiked) LinIcons.HeartFilled else LinIcons.Heart,
                        contentDescription = if (track.isLiked) "Unlike" else "Like",
                        tint = if (track.isLiked) linColors.defaultAccent else linColors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(color = linColors.glassBorder)

            SheetActionItem(
                icon = LinIcons.PlayNext,
                label = "play next",
                onClick = { onPlayNext(); onDismiss() }
            )
            SheetActionItem(
                icon = LinIcons.AddToQueue,
                label = "add to queue",
                onClick = { onAddToQueue(); onDismiss() }
            )
            SheetActionItem(
                icon = LinIcons.RadioMix,
                label = "start mix",
                onClick = { onStartMix(); onDismiss() }
            )

            HorizontalDivider(color = linColors.glassBorder)

            SheetActionItem(
                icon = if (track.inLibrary) LinIcons.Trash else LinIcons.Plus,
                label = if (track.inLibrary) "delete from library" else "add to library",
                tint = if (track.inLibrary) linColors.danger else linColors.textPrimary,
                onClick = { onToggleLibrary(); onDismiss() }
            )
            SheetActionItem(
                icon = LinIcons.PlaylistAdd,
                label = "add to playlist",
                onClick = { onDismiss(); onOpenAddToPlaylist() }
            )
            if (onDeleteFromPlaylist != null) {
                SheetActionItem(
                    icon = LinIcons.Trash,
                    label = editablePlaylistName?.let { "delete from \"$it\"" } ?: "delete from this playlist",
                    tint = linColors.danger,
                    onClick = { onDeleteFromPlaylist(); onDismiss() }
                )
            }

            HorizontalDivider(color = linColors.glassBorder)

            SheetActionItem(
                icon = LinIcons.Album,
                label = track.album?.title?.takeIf { it.isNotBlank() }?.let { "go to album · $it" } ?: "go to album",
                onClick = { onDismiss(); onGoToAlbum() }
            )
            SheetActionItem(
                icon = LinIcons.Artist,
                label = "go to artist",
                onClick = { onDismiss(); onGoToArtist() }
            )
            SheetActionItem(
                icon = if (isDownloaded) LinIcons.Trash else LinIcons.Download,
                label = if (isDownloaded) "remove download" else "download for offline",
                onClick = { onToggleDownload(); onDismiss() }
            )
            if (onTogglePinSpeedDial != null) {
                SheetActionItem(
                    icon = LinIcons.Pin,
                    label = if (isPinnedToSpeedDial) "unpin from speed dial" else "pin to speed dial",
                    tint = if (isPinnedToSpeedDial) linColors.defaultAccent else linColors.textPrimary,
                    onClick = { onTogglePinSpeedDial(); onDismiss() }
                )
            }
            SheetActionItem(
                icon = LinIcons.Tag,
                label = "manage #tags",
                onClick = { onDismiss(); onManageTags() }
            )
            SheetActionItem(
                icon = LinIcons.Share,
                label = "share",
                onClick = { onDismiss(); onShare() }
            )
            if (onShareAtTimestamp != null && !shareTimestampLabel.isNullOrBlank()) {
                SheetActionItem(
                    icon = LinIcons.Share,
                    label = "share · at $shareTimestampLabel",
                    onClick = { onDismiss(); onShareAtTimestamp() }
                )
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SheetActionItem(
    icon: ImageVector,
    label: String,
    tint: Color? = null,
    onClick: () -> Unit
) {
    val linColors = LinTheme.colors
    val color = tint ?: linColors.textPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(text = linLabel(label), style = LinTextStyles.navItem.copy(fontSize = 16.sp), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    track: Track,
    playlists: List<PlaylistSummary>,
    containingPlaylistIds: Set<String>,
    onDismiss: () -> Unit,
    onTogglePlaylistMembership: (PlaylistSummary, Boolean) -> Unit,
    onCreateNewPlaylist: (title: String, description: String, privacy: String) -> Unit
) {
    val linColors = LinTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var search by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }

    val editablePlaylists = remember(playlists, search) {
        playlists
            .filter { it.id != "LM" && it.browseId != "VLLM" }
            .filter { search.isBlank() || it.title.contains(search, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = LinSharpShape,
        containerColor = linColors.background,
        contentColor = linColors.textPrimary,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, linColors.glassBorder)
                .navigationBarsPadding()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(linLabel("add to playlist"), style = LinTextStyles.sectionHeading, color = linColors.textPrimary)
                LinInvertedButton(text = "+ new playlist", onClick = { showCreateDialog = true })
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = track.title,
                style = LinTextStyles.subtitle,
                color = linColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(14.dp))

            LinUnderlineTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = "filter playlists..."
            )
            Spacer(Modifier.height(14.dp))

            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                items(editablePlaylists, key = { it.id }) { pl ->
                    val checked = pl.id in containingPlaylistIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTogglePlaylistMembership(pl, !checked) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(linColors.glassBorder)
                        ) {
                            if (!pl.thumbnailUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = pl.thumbnailUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.matchParentSize()
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(pl.title, style = LinTextStyles.navItemActive, color = linColors.textPrimary)
                            pl.trackCount?.let {
                                Text("$it tracks", style = LinTextStyles.subtitle, color = linColors.textSecondary)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(if (checked) linColors.btnInvertBg else Color.Transparent)
                                .border(1.dp, linColors.textPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            if (checked) {
                                Icon(
                                    LinIcons.Check,
                                    contentDescription = "In playlist",
                                    tint = linColors.btnInvertText,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        PlaylistEditorDialog(
            title = "create playlist",
            initialName = "",
            initialDescription = "",
            nameEditable = true,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, desc, privacy ->
                onCreateNewPlaylist(name, desc, privacy)
                showCreateDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SleepTimerSheet(
    state: SleepTimerState,
    onStartCountdown: (minutes: Int, fadeOut: Boolean) -> Unit,
    onStartEndOfTrack: (fadeOut: Boolean) -> Unit,
    onExtend5Min: () -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val linColors = LinTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var fadeOut by remember { mutableStateOf(true) }
    var customMinutes by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = LinSharpShape,
        containerColor = linColors.background,
        contentColor = linColors.textPrimary,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, linColors.glassBorder)
                .navigationBarsPadding()
                .padding(20.dp)
        ) {
            Text(linLabel("sleep timer"), style = LinTextStyles.sectionHeading, color = linColors.textPrimary)
            Spacer(Modifier.height(12.dp))

            if (state !is SleepTimerState.Off) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(linColors.queueItemBg)
                        .border(1.dp, linColors.textPrimary.copy(alpha = 0.25f))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val label = when (state) {
                        is SleepTimerState.Countdown -> linLabel("stops in ${formatDurationMs(state.remainingMs)}")
                        is SleepTimerState.EndOfTrack -> linLabel("stops after current track")
                        SleepTimerState.Off -> ""
                    }
                    Text(label, style = LinTextStyles.navItemActive, color = linColors.textPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinOutlinedButton(text = "+5m", onClick = onExtend5Min)
                        LinInvertedButton(text = "cancel", onClick = { onCancelTimer(); onDismiss() })
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(5, 10, 15, 30, 45, 60).forEach { mins ->
                    LinOutlinedButton(
                        text = "${mins}m",
                        onClick = { onStartCountdown(mins, fadeOut); onDismiss() }
                    )
                }
                LinOutlinedButton(
                    text = "end of track",
                    onClick = { onStartEndOfTrack(fadeOut); onDismiss() }
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    LinUnderlineTextField(
                        value = customMinutes,
                        onValueChange = { input -> customMinutes = input.filter { it.isDigit() }.take(3) },
                        placeholder = "custom minutes...",
                        keyboardType = KeyboardType.Number
                    )
                }
                Spacer(Modifier.width(12.dp))
                LinInvertedButton(
                    text = "set",
                    enabled = (customMinutes.toIntOrNull() ?: 0) > 0,
                    onClick = {
                        val mins = customMinutes.toIntOrNull() ?: return@LinInvertedButton
                        onStartCountdown(mins, fadeOut)
                        onDismiss()
                    }
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { fadeOut = !fadeOut }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(linLabel("10s fade-out before pause"), style = LinTextStyles.subtitle, color = linColors.textSecondary)
                LinSquareToggle(checked = fadeOut, onCheckedChange = { fadeOut = it })
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistPickerSheet(
    artists: List<ArtistRef>,
    onSelectArtist: (ArtistRef) -> Unit,
    onDismiss: () -> Unit
) {
    val linColors = LinTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = LinSharpShape,
        containerColor = linColors.background,
        contentColor = linColors.textPrimary,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, linColors.glassBorder)
                .navigationBarsPadding()
                .padding(20.dp)
        ) {
            Text(linLabel("choose artist"), style = LinTextStyles.sectionHeading, color = linColors.textPrimary)
            Spacer(Modifier.height(12.dp))
            artists.forEach { artist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectArtist(artist); onDismiss() }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(LinIcons.Artist, contentDescription = null, tint = linColors.textPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(artist.name, style = LinTextStyles.navItem, color = linColors.textPrimary)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagEditorSheet(
    itemTitle: String,
    tags: List<String>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val linColors = LinTheme.colors
    var input by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = LinSharpShape,
        containerColor = linColors.background,
        contentColor = linColors.textPrimary,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, linColors.glassBorder)
                .navigationBarsPadding()
                .padding(20.dp)
        ) {
            Text("#tags · $itemTitle", style = LinTextStyles.sectionHeading, color = linColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))

            if (tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tags.forEach { tag ->
                        LinTagPill(text = "#$tag  ×", active = true, onClick = { onRemoveTag(tag) })
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    LinUnderlineTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = "add tag (e.g. night, synth)..."
                    )
                }
                Spacer(Modifier.width(12.dp))
                LinInvertedButton(
                    text = "+ tag",
                    enabled = input.trim().isNotEmpty(),
                    onClick = {
                        onAddTag(input.trim())
                        input = ""
                    }
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun PlaylistEditorDialog(
    title: String,
    initialName: String,
    initialDescription: String,
    nameEditable: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, privacy: String) -> Unit
) {
    val linColors = LinTheme.colors
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    var privacy by remember { mutableStateOf("PRIVATE") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(linColors.background)
                .border(1.dp, linColors.textPrimary.copy(alpha = 0.25f))
                .padding(22.dp)
        ) {
            Text(linLabel(title), style = LinTextStyles.sectionHeading, color = linColors.textPrimary)
            Spacer(Modifier.height(16.dp))

            LinUnderlineTextField(
                value = name,
                onValueChange = { if (nameEditable) name = it },
                placeholder = "playlist name"
            )
            Spacer(Modifier.height(14.dp))
            LinUnderlineTextField(
                value = description,
                onValueChange = { description = it },
                placeholder = "description (optional)"
            )
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("PRIVATE", "UNLISTED", "PUBLIC").forEach { p ->
                    LinTagPill(
                        text = p.lowercase(),
                        active = privacy == p,
                        onClick = { privacy = p }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                LinOutlinedButton(text = "cancel", onClick = onDismiss)
                Spacer(Modifier.width(10.dp))
                LinInvertedButton(
                    text = "save",
                    enabled = name.trim().isNotEmpty(),
                    onClick = { onConfirm(name.trim(), description.trim(), privacy) }
                )
            }
        }
    }
}

@Composable
fun LinUnderlineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val linColors = LinTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            if (value.isEmpty()) {
                Text(placeholder, style = LinTextStyles.navItem, color = linColors.textSecondary)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LinTextStyles.navItem.copy(
                    color = linColors.textPrimary,
                    fontWeight = FontWeight.Normal
                ),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                cursorBrush = SolidColor(linColors.textPrimary),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(linColors.textPrimary.copy(alpha = 0.7f))
        )
    }
}
