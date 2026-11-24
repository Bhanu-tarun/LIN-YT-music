package com.lin.music.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin.music.core.designsystem.components.formatDurationMs
import com.lin.music.core.designsystem.icons.LinIcons
import com.lin.music.core.designsystem.theme.LinSharpShape
import com.lin.music.core.designsystem.theme.LinTextStyles
import com.lin.music.core.designsystem.theme.linLabel
import com.lin.music.core.model.Track

@Composable
fun DrivingModeScreen(
    currentTrack: Track?,
    nextTrack: Track?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    accentColor: Color,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleLike: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header: high-contrast OLED strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    imageVector = LinIcons.Driving,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = linLabel("driving mode"),
                    style = LinTextStyles.sectionHeading,
                    color = Color.White
                )
            }
            Box(
                modifier = Modifier
                    .border(2.dp, Color.White, LinSharpShape)
                    .clickable(onClick = onExit)
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Text(
                    text = linLabel("exit"),
                    style = LinTextStyles.navItemActive,
                    color = Color.White
                )
            }
        }

        // Oversized Track Metadata
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = currentTrack?.title ?: linLabel("no track loaded"),
                style = LinTextStyles.drilldownTitle.copy(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = currentTrack?.artistDisplay.orEmpty().ifBlank { "LIN" },
                style = LinTextStyles.sectionHeading.copy(fontSize = 22.sp),
                color = Color(0xFFB8B8C2),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (durationMs > 0L) {
                val frac = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(Color(0xFF23232A))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(frac)
                            .height(6.dp)
                            .background(accentColor)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatDurationMs(positionMs), style = LinTextStyles.navItemActive, color = Color.White)
                    Text(formatDurationMs(durationMs), style = LinTextStyles.navItemActive, color = Color(0xFF9A9AA6))
                }
            }
        }

        // Oversized Primary Controls (minimum 88dp targets for eyes-free touch)
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(110.dp)
                        .border(2.dp, Color(0xFF3A3A44), LinSharpShape)
                        .background(Color(0xFF101014))
                        .clickable(onClick = onSkipPrevious),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = LinIcons.SkipBack,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1.3f)
                        .height(110.dp)
                        .background(accentColor, LinSharpShape)
                        .clickable(onClick = onTogglePlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) LinIcons.Pause else LinIcons.Play,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(50.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(110.dp)
                        .border(2.dp, Color(0xFF3A3A44), LinSharpShape)
                        .background(Color(0xFF101014))
                        .clickable(onClick = onSkipNext),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = LinIcons.SkipForward,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val liked = currentTrack?.isLiked == true
                Box(
                    modifier = Modifier
                        .height(68.dp)
                        .border(
                            2.dp,
                            if (liked) accentColor else Color(0xFF3A3A44),
                            LinSharpShape
                        )
                        .clickable(enabled = currentTrack != null, onClick = onToggleLike)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(
                            imageVector = if (liked) LinIcons.HeartFilled else LinIcons.Heart,
                            contentDescription = "Like",
                            tint = if (liked) accentColor else Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                        Text(
                            text = linLabel(if (liked) "liked" else "like"),
                            style = LinTextStyles.navItemActive,
                            color = Color.White
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(68.dp)
                        .border(1.dp, Color(0xFF26262E), LinSharpShape)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = linLabel("up next"),
                        style = LinTextStyles.subtitle.copy(fontSize = 11.sp),
                        color = accentColor
                    )
                    Text(
                        text = nextTrack?.title ?: linLabel("end of queue"),
                        style = LinTextStyles.navItemActive,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
