package com.lin.music.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin.music.core.designsystem.components.LinInvertedButton
import com.lin.music.core.designsystem.components.LinOutlinedButton
import com.lin.music.core.designsystem.components.LinSquareToggle
import com.lin.music.core.designsystem.icons.LinIcons
import com.lin.music.core.designsystem.sheets.LinUnderlineTextField
import com.lin.music.core.designsystem.theme.LinSharpShape
import com.lin.music.core.designsystem.theme.LinTextStyles
import com.lin.music.core.designsystem.theme.LinTheme
import com.lin.music.core.designsystem.theme.linLabel
import com.lin.music.core.model.BuiltInEqPresets
import com.lin.music.core.model.EQ_BAND_LABELS
import com.lin.music.core.model.EQ_MAX_GAIN_DB
import com.lin.music.core.model.EQ_MIN_GAIN_DB
import com.lin.music.core.model.EqualizerProfile
import com.lin.music.core.model.UserPreferences
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreen(
    prefs: UserPreferences,
    activeOutputDevice: Pair<String, String>?,
    accentColor: Color,
    onToggleEq: (Boolean) -> Unit,
    onSelectProfile: (String) -> Unit,
    onUpdateBandsLive: (bands: List<Float>, preampDb: Float) -> Unit,
    onCommitBandsLive: (bands: List<Float>, preampDb: Float) -> Unit = onUpdateBandsLive,
    onSaveProfileAs: (name: String, bands: List<Float>, preampDb: Float) -> Unit,
    onImportAutoEq: (name: String, rawText: String) -> Boolean,
    onDeleteCustomProfile: (String) -> Unit,
    onAssignDeviceProfile: (deviceKey: String, deviceName: String, profileId: String) -> Unit,
    onRemoveDeviceAssignment: (deviceKey: String) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val linColors = LinTheme.colors
    val assignedForDevice = activeOutputDevice?.let { dev ->
        prefs.bluetoothEqAssignments.firstOrNull { it.deviceKey == dev.first }
    }
    val effectiveProfileId = assignedForDevice?.profileId ?: prefs.activeEqProfileId
    val resolvedProfile = BuiltInEqPresets.resolve(effectiveProfileId, prefs.customEqProfiles)

    val bands = remember {
        mutableStateListOf<Float>().apply { addAll(resolvedProfile.normalizedBands()) }
    }
    var preampDb by remember { mutableFloatStateOf(resolvedProfile.preampDb) }
    var lastAppliedProfileId by remember { mutableStateOf(resolvedProfile.id) }

    LaunchedEffect(resolvedProfile.id) {
        if (resolvedProfile.id != lastAppliedProfileId && resolvedProfile.id != "custom_live") {
            bands.clear()
            bands.addAll(resolvedProfile.normalizedBands())
            preampDb = resolvedProfile.preampDb
            lastAppliedProfileId = resolvedProfile.id
        }
    }

    var showSaveDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }
    var importName by remember { mutableStateOf("") }
    var importText by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }

    val allProfiles = remember(prefs.customEqProfiles) {
        BuiltInEqPresets.ALL + prefs.customEqProfiles
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 180.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(LinIcons.ArrowLeft, "Back", tint = linColors.textSecondary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(linLabel("equalizer & dsp"), style = LinTextStyles.sectionHeading, color = linColors.textPrimary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (prefs.eqEnabled) "ON" else "OFF",
                        style = LinTextStyles.subtitle,
                        color = if (prefs.eqEnabled) accentColor else linColors.textSecondary
                    )
                    LinSquareToggle(checked = prefs.eqEnabled, onCheckedChange = onToggleEq)
                }
            }
        }

        // Bluetooth auto-switching device banner
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(linColors.glassBg)
                    .border(1.dp, linColors.glassBorder, LinSharpShape)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (activeOutputDevice != null) {
                    Text(
                        text = linLabel("output device: ${activeOutputDevice.second}"),
                        style = LinTextStyles.navItemActive,
                        color = linColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (assignedForDevice != null) {
                        val mappedProfile = BuiltInEqPresets.resolve(assignedForDevice.profileId, prefs.customEqProfiles)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = linLabel("auto-profile: ${mappedProfile.name}"),
                                style = LinTextStyles.subtitle,
                                color = accentColor
                            )
                            LinOutlinedButton(
                                text = "unlink",
                                onClick = { onRemoveDeviceAssignment(activeOutputDevice.first) }
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = linLabel("bind '${resolvedProfile.name}' to this device"),
                                style = LinTextStyles.subtitle,
                                color = linColors.textSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            LinInvertedButton(
                                text = "assign",
                                onClick = {
                                    onAssignDeviceProfile(
                                        activeOutputDevice.first,
                                        activeOutputDevice.second,
                                        resolvedProfile.id
                                    )
                                }
                            )
                        }
                    }
                } else {
                    Text(
                        text = linLabel("no external bluetooth / usb audio output connected — connect headphones to assign a per-device auto profile."),
                        style = LinTextStyles.subtitle,
                        color = linColors.textSecondary
                    )
                }
            }
        }

        // Presets strip
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(linLabel("presets & autoeq"), style = LinTextStyles.navItemActive, color = linColors.textPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinOutlinedButton(text = "import autoeq", onClick = {
                            importError = null
                            showImportDialog = true
                        })
                        LinOutlinedButton(text = "save as", onClick = {
                            saveName = ""
                            showSaveDialog = true
                        })
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(allProfiles, key = { it.id }) { preset ->
                        val active = preset.id == resolvedProfile.id
                        Row(
                            modifier = Modifier
                                .border(
                                    1.dp,
                                    if (active) accentColor else linColors.glassBorder,
                                    LinSharpShape
                                )
                                .background(if (active) accentColor.copy(alpha = 0.16f) else Color.Transparent)
                                .clickable {
                                    bands.clear()
                                    bands.addAll(preset.normalizedBands())
                                    preampDb = preset.preampDb
                                    lastAppliedProfileId = preset.id
                                    onSelectProfile(preset.id)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = preset.name,
                                style = LinTextStyles.subtitle.copy(
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (active) linColors.textPrimary else linColors.textSecondary
                            )
                            if (!preset.isBuiltIn && preset.id != "custom_live") {
                                Icon(
                                    imageVector = LinIcons.Close,
                                    contentDescription = "Delete preset",
                                    tint = linColors.textSecondary,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { onDeleteCustomProfile(preset.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 10-band Graphic EQ matrix
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, linColors.glassBorder, LinSharpShape)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(linLabel("10-band eq (${resolvedProfile.name})"), style = LinTextStyles.navItemActive, color = linColors.textPrimary)
                    Text(
                        text = linLabel("flatten"),
                        style = LinTextStyles.subtitle,
                        color = accentColor,
                        modifier = Modifier.clickable {
                            for (i in 0 until bands.size) bands[i] = 0f
                            preampDb = 0f
                            lastAppliedProfileId = "custom_live"
                            onCommitBandsLive(List(10) { 0f }, 0f)
                        }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (idx in 0 until bands.size.coerceAtMost(10)) {
                        val gain = bands[idx]
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = formatDb(gain),
                                style = LinTextStyles.subtitle.copy(fontSize = 10.sp),
                                color = if (gain != 0f) linColors.textPrimary else linColors.textSecondary
                            )
                            Spacer(Modifier.height(6.dp))
                            VerticalEqFader(
                                gainDb = gain,
                                enabled = prefs.eqEnabled,
                                accentColor = accentColor,
                                onGainChanged = { nextGain ->
                                    bands[idx] = nextGain
                                    lastAppliedProfileId = "custom_live"
                                    onUpdateBandsLive(bands.toList(), preampDb)
                                },
                                onCommit = {
                                    lastAppliedProfileId = "custom_live"
                                    onCommitBandsLive(bands.toList(), preampDb)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = EQ_BAND_LABELS[idx],
                                style = LinTextStyles.subtitle.copy(fontSize = 10.sp),
                                color = linColors.textSecondary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(linLabel("preamp"), style = LinTextStyles.subtitle, color = linColors.textSecondary)
                    Text("${formatDb(preampDb)} dB", style = LinTextStyles.subtitle, color = linColors.textPrimary)
                }
                LinSharpHorizontalSlider(
                    value = preampDb,
                    valueRange = -12f..6f,
                    onValueChange = {
                        val snapped = (it * 2f).roundToInt() / 2f
                        preampDb = snapped
                        lastAppliedProfileId = "custom_live"
                        onUpdateBandsLive(bands.toList(), snapped)
                    },
                    onValueChangeFinished = {
                        lastAppliedProfileId = "custom_live"
                        onCommitBandsLive(bands.toList(), preampDb)
                    },
                    accentColor = accentColor,
                    neutralValue = 0f,
                    dimmed = !prefs.eqEnabled
                )
            }
        }

        // Saved BT device assignments summary
        if (prefs.bluetoothEqAssignments.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(linLabel("saved device profiles"), style = LinTextStyles.navItemActive, color = linColors.textPrimary)
                    prefs.bluetoothEqAssignments.forEach { assignment ->
                        val profile = BuiltInEqPresets.resolve(assignment.profileId, prefs.customEqProfiles)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, linColors.glassBorder, LinSharpShape)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(assignment.deviceName, style = LinTextStyles.navItemActive, color = linColors.textPrimary)
                                Text(linLabel("profile: ${profile.name}"), style = LinTextStyles.subtitle, color = linColors.textSecondary)
                            }
                            IconButton(onClick = { onRemoveDeviceAssignment(assignment.deviceKey) }) {
                                Icon(LinIcons.Trash, "Remove assignment", tint = linColors.textSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        // Speed & Pitch controls
        item {
            var localSpeed by remember(prefs.playbackSpeed) { mutableFloatStateOf(prefs.playbackSpeed) }
            var localPitch by remember(prefs.playbackPitch) { mutableFloatStateOf(prefs.playbackPitch) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, linColors.glassBorder, LinSharpShape)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(linLabel("playback speed"), style = LinTextStyles.navItemActive, color = linColors.textPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(String.format("%.2fx", localSpeed), style = LinTextStyles.subtitle, color = accentColor)
                        Text(
                            text = linLabel("reset"),
                            style = LinTextStyles.subtitle,
                            color = linColors.textSecondary,
                            modifier = Modifier.clickable {
                                localSpeed = 1.0f
                                onSpeedChange(1.0f)
                            }
                        )
                    }
                }
                LinSharpHorizontalSlider(
                    value = localSpeed,
                    valueRange = 0.5f..2.0f,
                    onValueChange = { raw ->
                        val snapped = ((raw * 20f).roundToInt() / 20f).coerceIn(0.5f, 2.0f)
                        if (snapped != localSpeed) {
                            localSpeed = snapped
                            onSpeedChange(snapped)
                        }
                    },
                    onValueChangeFinished = {
                        onSpeedChange(localSpeed)
                    },
                    accentColor = accentColor,
                    neutralValue = 1.0f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(linLabel("playback pitch"), style = LinTextStyles.navItemActive, color = linColors.textPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(String.format("%.2fx", localPitch), style = LinTextStyles.subtitle, color = accentColor)
                        Text(
                            text = linLabel("reset"),
                            style = LinTextStyles.subtitle,
                            color = linColors.textSecondary,
                            modifier = Modifier.clickable {
                                localPitch = 1.0f
                                onPitchChange(1.0f)
                            }
                        )
                    }
                }
                LinSharpHorizontalSlider(
                    value = localPitch,
                    valueRange = 0.5f..2.0f,
                    onValueChange = { raw ->
                        val snapped = ((raw * 20f).roundToInt() / 20f).coerceIn(0.5f, 2.0f)
                        if (snapped != localPitch) {
                            localPitch = snapped
                            onPitchChange(snapped)
                        }
                    },
                    onValueChangeFinished = {
                        onPitchChange(localPitch)
                    },
                    accentColor = accentColor,
                    neutralValue = 1.0f
                )
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = linColors.background,
            title = { Text(linLabel("save eq preset"), style = LinTextStyles.sectionHeading, color = linColors.textPrimary) },
            text = {
                LinUnderlineTextField(
                    value = saveName,
                    onValueChange = { saveName = it },
                    placeholder = "preset name..."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = saveName.trim().ifBlank { "Custom ${prefs.customEqProfiles.size + 1}" }
                    onSaveProfileAs(trimmed, bands.toList(), preampDb)
                    showSaveDialog = false
                }) { Text(linLabel("save"), color = accentColor) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text(linLabel("cancel"), color = linColors.textSecondary) }
            }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            containerColor = linColors.background,
            title = { Text(linLabel("import autoeq profile"), style = LinTextStyles.sectionHeading, color = linColors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LinUnderlineTextField(
                        value = importName,
                        onValueChange = { importName = it },
                        placeholder = "headphone model name..."
                    )
                    LinUnderlineTextField(
                        value = importText,
                        onValueChange = { importText = it; importError = null },
                        placeholder = "paste GraphicEQ: or EqualizerAPO Filter lines..."
                    )
                    importError?.let { err ->
                        Text(err, style = LinTextStyles.subtitle, color = linColors.danger)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val ok = onImportAutoEq(importName.trim().ifBlank { "Imported AutoEQ" }, importText)
                    if (ok) {
                        showImportDialog = false
                        importName = ""
                        importText = ""
                    } else {
                        importError = "Could not recognize GraphicEQ or EqualizerAPO Filter lines."
                    }
                }) { Text(linLabel("import"), color = accentColor) }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text(linLabel("cancel"), color = linColors.textSecondary) }
            }
        )
    }
}

@Composable
private fun LinSharpHorizontalSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {},
    accentColor: Color,
    neutralValue: Float? = null,
    dimmed: Boolean = false,
    modifier: Modifier = Modifier
) {
    val linColors = LinTheme.colors
    val trackColor = linColors.glassBorder
    val activeFill = if (!dimmed) accentColor else linColors.textSecondary.copy(alpha = 0.5f)
    val thumbColor = if (!dimmed) linColors.textPrimary else linColors.textSecondary
    val grooveColor = linColors.background
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val latestRange by rememberUpdatedState(valueRange)
    var isDragging by remember { mutableStateOf(false) }

    fun computeValue(xPx: Float, widthPx: Int, thumbHalfPx: Float): Float {
        val span = (latestRange.endInclusive - latestRange.start).takeIf { it > 0f } ?: 1f
        val usable = (widthPx - thumbHalfPx * 2f).coerceAtLeast(1f)
        val fraction = ((xPx - thumbHalfPx) / usable).coerceIn(0f, 1f)
        return (latestRange.start + fraction * span).coerceIn(latestRange.start, latestRange.endInclusive)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val thumbHalf = 6.dp.toPx()
                    latestOnValueChange(computeValue(offset.x, size.width, thumbHalf))
                    latestOnValueChangeFinished()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val thumbHalf = 6.dp.toPx()
                        latestOnValueChange(computeValue(offset.x, size.width, thumbHalf))
                    },
                    onDragEnd = {
                        isDragging = false
                        latestOnValueChangeFinished()
                    },
                    onDragCancel = {
                        isDragging = false
                        latestOnValueChangeFinished()
                    }
                ) { change, _ ->
                    change.consume()
                    val thumbHalf = 6.dp.toPx()
                    latestOnValueChange(computeValue(change.position.x, size.width, thumbHalf))
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val cy = h / 2f
        val thumbW = 12.dp.toPx()
        val thumbH = 20.dp.toPx()
        val thumbHalf = thumbW / 2f
        val usable = (w - thumbW).coerceAtLeast(1f)
        val trackH = 4.dp.toPx()
        val span = (latestRange.endInclusive - latestRange.start).takeIf { it > 0f } ?: 1f

        fun valueToX(v: Float): Float {
            val frac = ((v - latestRange.start) / span).coerceIn(0f, 1f)
            return thumbHalf + frac * usable
        }

        // Background sharp track
        drawRect(
            color = trackColor,
            topLeft = Offset(thumbHalf, cy - trackH / 2f),
            size = Size(usable, trackH)
        )

        // Optional neutral detent hairline (0 dB / 1.0x)
        val neutralX = neutralValue?.let { valueToX(it) }
        if (neutralX != null) {
            val detentH = 16.dp.toPx()
            drawLine(
                color = trackColor.copy(alpha = 0.85f),
                start = Offset(neutralX, cy - detentH / 2f),
                end = Offset(neutralX, cy + detentH / 2f),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Sharp active fill bar
        val valX = valueToX(value)
        val fillStart = neutralX ?: thumbHalf
        val leftX = minOf(fillStart, valX)
        val fillW = kotlin.math.abs(valX - fillStart)
        if (fillW > 0.5f) {
            drawRect(
                color = activeFill,
                topLeft = Offset(leftX, cy - trackH / 2f),
                size = Size(fillW, trackH)
            )
        }

        // Boxy sharp rectangular thumb
        val thumbLeft = (valX - thumbHalf).coerceIn(0f, (w - thumbW).coerceAtLeast(0f))
        val thumbTop = (cy - thumbH / 2f).coerceIn(0f, (h - thumbH).coerceAtLeast(0f))
        drawRect(
            color = thumbColor,
            topLeft = Offset(thumbLeft, thumbTop),
            size = Size(thumbW, thumbH)
        )
        drawLine(
            color = grooveColor,
            start = Offset(thumbLeft + thumbHalf, thumbTop + 3.dp.toPx()),
            end = Offset(thumbLeft + thumbHalf, thumbTop + thumbH - 3.dp.toPx()),
            strokeWidth = 1.dp.toPx()
        )
        if (isDragging) {
            drawRect(
                color = accentColor,
                topLeft = Offset(thumbLeft, thumbTop),
                size = Size(thumbW, thumbH),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
    }
}

@Composable
private fun VerticalEqFader(
    gainDb: Float,
    enabled: Boolean,
    accentColor: Color,
    onGainChanged: (Float) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val linColors = LinTheme.colors
    val activeFill = if (enabled) accentColor else linColors.textSecondary.copy(alpha = 0.5f)
    val trackColor = linColors.glassBorder
    val thumbColor = if (enabled) linColors.textPrimary else linColors.textSecondary
    val grooveColor = linColors.background
    val latestOnGainChanged by rememberUpdatedState(onGainChanged)
    val latestOnCommit by rememberUpdatedState(onCommit)
    var isDragging by remember { mutableStateOf(false) }

    fun computeSnappedGain(yPx: Float, heightPx: Int): Float {
        val ratio = 1f - (yPx / heightPx.coerceAtLeast(1)).coerceIn(0f, 1f)
        val rawDb = EQ_MIN_GAIN_DB + ratio * (EQ_MAX_GAIN_DB - EQ_MIN_GAIN_DB)
        val snapped = (rawDb * 2f).roundToInt() / 2f
        return snapped.coerceIn(EQ_MIN_GAIN_DB, EQ_MAX_GAIN_DB)
    }

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    latestOnGainChanged(computeSnappedGain(offset.y, size.height))
                    latestOnCommit()
                }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        latestOnGainChanged(computeSnappedGain(offset.y, size.height))
                    },
                    onDragEnd = {
                        isDragging = false
                        latestOnCommit()
                    },
                    onDragCancel = {
                        isDragging = false
                        latestOnCommit()
                    }
                ) { change, _ ->
                    change.consume()
                    latestOnGainChanged(computeSnappedGain(change.position.y, size.height))
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val barWidth = 4.dp.toPx()
        val zeroY = h / 2f

        // Background track
        drawRect(
            color = trackColor,
            topLeft = Offset(cx - barWidth / 2f, 0f),
            size = Size(barWidth, h)
        )
        // Center 0 dB hairline
        drawLine(
            color = trackColor.copy(alpha = 0.85f),
            start = Offset(cx - 8.dp.toPx(), zeroY),
            end = Offset(cx + 8.dp.toPx(), zeroY),
            strokeWidth = 1.dp.toPx()
        )
        // Value fill from 0 dB line
        val norm = ((gainDb - EQ_MIN_GAIN_DB) / (EQ_MAX_GAIN_DB - EQ_MIN_GAIN_DB)).coerceIn(0f, 1f)
        val valY = h * (1f - norm)
        val topY = minOf(zeroY, valY)
        val segH = kotlin.math.abs(zeroY - valY)
        if (segH > 0.5f) {
            drawRect(
                color = activeFill,
                topLeft = Offset(cx - barWidth / 2f, topY),
                size = Size(barWidth, segH)
            )
        }
        // Crisp square hardware-fader cap
        val thumbW = 16.dp.toPx()
        val thumbH = 8.dp.toPx()
        val thumbLeft = cx - thumbW / 2f
        val thumbTop = (valY - thumbH / 2f).coerceIn(0f, (h - thumbH).coerceAtLeast(0f))
        drawRect(
            color = thumbColor,
            topLeft = Offset(thumbLeft, thumbTop),
            size = Size(thumbW, thumbH)
        )
        drawLine(
            color = grooveColor,
            start = Offset(thumbLeft + 2.dp.toPx(), thumbTop + thumbH / 2f),
            end = Offset(thumbLeft + thumbW - 2.dp.toPx(), thumbTop + thumbH / 2f),
            strokeWidth = 1.dp.toPx()
        )
        if (isDragging) {
            drawRect(
                color = accentColor,
                topLeft = Offset(thumbLeft, thumbTop),
                size = Size(thumbW, thumbH),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
    }
}

private fun formatDb(db: Float): String {
    val rounded = (db * 10f).roundToInt() / 10f
    return when {
        rounded > 0f -> "+${if (rounded % 1f == 0f) rounded.toInt() else rounded}"
        rounded < 0f -> "${if (rounded % 1f == 0f) rounded.toInt() else rounded}"
        else -> "0"
    }
}
