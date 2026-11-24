package com.lin.music.core.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Geometric 2dp square-cap, miter-join icon set ported directly from frontend_css/app.jsx.
 */
object LinIcons {

    private fun buildIcon(
        name: String,
        fillOnly: Boolean = false,
        block: PathBuilder.() -> Unit
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = if (fillOnly) SolidColor(Color.Black) else null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Square,
            strokeLineJoin = StrokeJoin.Miter
        ) {
            block()
        }
    }.build()

    val Play: ImageVector by lazy {
        buildIcon("Play", fillOnly = true) {
            moveTo(6f, 4f); lineTo(20f, 12f); lineTo(6f, 20f); close()
        }
    }

    val Pause: ImageVector by lazy {
        buildIcon("Pause", fillOnly = true) {
            moveTo(6f, 4f); lineTo(10f, 4f); lineTo(10f, 20f); lineTo(6f, 20f); close()
            moveTo(14f, 4f); lineTo(18f, 4f); lineTo(18f, 20f); lineTo(14f, 20f); close()
        }
    }

    val SkipBack: ImageVector by lazy {
        buildIcon("SkipBack") {
            moveTo(19f, 20f); lineTo(9f, 12f); lineTo(19f, 4f); close()
            moveTo(5f, 19f); lineTo(5f, 5f)
        }
    }

    val SkipForward: ImageVector by lazy {
        buildIcon("SkipForward") {
            moveTo(5f, 4f); lineTo(15f, 12f); lineTo(5f, 20f); close()
            moveTo(19f, 5f); lineTo(19f, 19f)
        }
    }

    val GripHorizontal: ImageVector by lazy {
        buildIcon("GripHorizontal", fillOnly = true) {
            moveTo(4f, 8f); lineTo(6f, 8f); lineTo(6f, 10f); lineTo(4f, 10f); close()
            moveTo(11f, 8f); lineTo(13f, 8f); lineTo(13f, 10f); lineTo(11f, 10f); close()
            moveTo(18f, 8f); lineTo(20f, 8f); lineTo(20f, 10f); lineTo(18f, 10f); close()
            moveTo(4f, 14f); lineTo(6f, 14f); lineTo(6f, 16f); lineTo(4f, 16f); close()
            moveTo(11f, 14f); lineTo(13f, 14f); lineTo(13f, 16f); lineTo(11f, 16f); close()
            moveTo(18f, 14f); lineTo(20f, 14f); lineTo(20f, 16f); lineTo(18f, 16f); close()
        }
    }

    val Close: ImageVector by lazy {
        buildIcon("Close") {
            moveTo(18f, 6f); lineTo(6f, 18f)
            moveTo(6f, 6f); lineTo(18f, 18f)
        }
    }

    val Trash: ImageVector by lazy {
        buildIcon("Trash") {
            moveTo(3f, 6f); lineTo(21f, 6f)
            moveTo(19f, 6f); lineTo(19f, 20f); lineTo(5f, 20f); lineTo(5f, 6f)
            moveTo(9f, 6f); lineTo(9f, 3f); lineTo(15f, 3f); lineTo(15f, 6f)
        }
    }

    val ArrowLeft: ImageVector by lazy {
        buildIcon("ArrowLeft") {
            moveTo(19f, 12f); lineTo(5f, 12f)
            moveTo(12f, 19f); lineTo(5f, 12f); lineTo(12f, 5f)
        }
    }

    val ChevronDown: ImageVector by lazy {
        buildIcon("ChevronDown") {
            moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f)
        }
    }

    val Edit: ImageVector by lazy {
        buildIcon("Edit") {
            moveTo(12f, 20f); lineTo(21f, 20f)
            moveTo(16.5f, 3.5f); lineTo(20.5f, 7.5f); lineTo(7f, 21f); lineTo(3f, 21f); lineTo(3f, 17f); close()
        }
    }

    val Sun: ImageVector by lazy {
        buildIcon("Sun") {
            moveTo(12f, 7f); arcTo(5f, 5f, 0f, true, true, 11.99f, 7f)
            moveTo(12f, 1f); lineTo(12f, 3f)
            moveTo(12f, 21f); lineTo(12f, 23f)
            moveTo(4.22f, 4.22f); lineTo(5.64f, 5.64f)
            moveTo(18.36f, 18.36f); lineTo(19.78f, 19.78f)
            moveTo(1f, 12f); lineTo(3f, 12f)
            moveTo(21f, 12f); lineTo(23f, 12f)
            moveTo(4.22f, 19.78f); lineTo(5.64f, 18.36f)
            moveTo(18.36f, 5.64f); lineTo(19.78f, 4.22f)
        }
    }

    val Moon: ImageVector by lazy {
        buildIcon("Moon") {
            moveTo(21f, 12.79f)
            arcTo(9f, 9f, 0f, true, true, 11.21f, 3f)
            arcTo(7f, 7f, 0f, false, false, 21f, 12.79f)
            close()
        }
    }

    val Search: ImageVector by lazy {
        buildIcon("Search") {
            moveTo(11f, 3f); arcTo(8f, 8f, 0f, true, true, 10.99f, 3f)
            moveTo(21f, 21f); lineTo(16.65f, 16.65f)
        }
    }

    val MoreVertical: ImageVector by lazy {
        buildIcon("MoreVertical", fillOnly = true) {
            moveTo(11f, 4f); lineTo(13f, 4f); lineTo(13f, 6f); lineTo(11f, 6f); close()
            moveTo(11f, 11f); lineTo(13f, 11f); lineTo(13f, 13f); lineTo(11f, 13f); close()
            moveTo(11f, 18f); lineTo(13f, 18f); lineTo(13f, 20f); lineTo(11f, 20f); close()
        }
    }

    val PlayNext: ImageVector by lazy {
        buildIcon("PlayNext") {
            moveTo(4f, 6f); lineTo(16f, 6f)
            moveTo(4f, 12f); lineTo(16f, 12f)
            moveTo(4f, 18f); lineTo(12f, 18f)
            moveTo(16f, 15f); lineTo(21f, 18f); lineTo(16f, 21f); close()
        }
    }

    val AddToQueue: ImageVector by lazy {
        buildIcon("AddToQueue") {
            moveTo(4f, 6f); lineTo(20f, 6f)
            moveTo(4f, 12f); lineTo(14f, 12f)
            moveTo(4f, 18f); lineTo(14f, 18f)
            moveTo(19f, 12f); lineTo(19f, 20f)
            moveTo(15f, 16f); lineTo(23f, 16f)
        }
    }

    val PlaylistAdd: ImageVector by lazy {
        buildIcon("PlaylistAdd") {
            moveTo(3f, 6f); lineTo(15f, 6f)
            moveTo(3f, 12f); lineTo(15f, 12f)
            moveTo(3f, 18f); lineTo(11f, 18f)
            moveTo(18f, 14f); lineTo(18f, 22f)
            moveTo(14f, 18f); lineTo(22f, 18f)
        }
    }

    val Heart: ImageVector by lazy {
        buildIcon("Heart") {
            moveTo(12f, 20f)
            lineTo(4f, 12f)
            lineTo(4f, 7f)
            lineTo(7f, 4f)
            lineTo(10f, 4f)
            lineTo(12f, 6f)
            lineTo(14f, 4f)
            lineTo(17f, 4f)
            lineTo(20f, 7f)
            lineTo(20f, 12f)
            close()
        }
    }

    val HeartFilled: ImageVector by lazy {
        buildIcon("HeartFilled", fillOnly = true) {
            moveTo(12f, 20f)
            lineTo(4f, 12f)
            lineTo(4f, 7f)
            lineTo(7f, 4f)
            lineTo(10f, 4f)
            lineTo(12f, 6f)
            lineTo(14f, 4f)
            lineTo(17f, 4f)
            lineTo(20f, 7f)
            lineTo(20f, 12f)
            close()
        }
    }

    val Shuffle: ImageVector by lazy {
        buildIcon("Shuffle") {
            moveTo(16f, 4f); lineTo(20f, 4f); lineTo(20f, 8f)
            moveTo(4f, 20f); lineTo(20f, 4f)
            moveTo(16f, 20f); lineTo(20f, 20f); lineTo(20f, 16f)
            moveTo(14f, 14f); lineTo(20f, 20f)
            moveTo(4f, 4f); lineTo(10f, 10f)
        }
    }

    val Repeat: ImageVector by lazy {
        buildIcon("Repeat") {
            moveTo(17f, 2f); lineTo(21f, 6f); lineTo(17f, 10f)
            moveTo(3f, 11f); lineTo(3f, 6f); lineTo(21f, 6f)
            moveTo(7f, 22f); lineTo(3f, 18f); lineTo(7f, 14f)
            moveTo(21f, 13f); lineTo(21f, 18f); lineTo(3f, 18f)
        }
    }

    val Download: ImageVector by lazy {
        buildIcon("Download") {
            moveTo(12f, 3f); lineTo(12f, 15f)
            moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f)
            moveTo(4f, 19f); lineTo(20f, 19f)
        }
    }

    val Check: ImageVector by lazy {
        buildIcon("Check") {
            moveTo(4f, 12f); lineTo(9f, 17f); lineTo(20f, 6f)
        }
    }

    val Plus: ImageVector by lazy {
        buildIcon("Plus") {
            moveTo(12f, 4f); lineTo(12f, 20f)
            moveTo(4f, 12f); lineTo(20f, 12f)
        }
    }

    val Album: ImageVector by lazy {
        buildIcon("Album") {
            moveTo(3f, 3f); lineTo(21f, 3f); lineTo(21f, 21f); lineTo(3f, 21f); close()
            moveTo(12f, 9f); arcTo(3f, 3f, 0f, true, true, 11.99f, 9f)
        }
    }

    val Artist: ImageVector by lazy {
        buildIcon("Artist") {
            moveTo(12f, 4f); arcTo(4f, 4f, 0f, true, true, 11.99f, 4f)
            moveTo(4f, 20f); lineTo(4f, 17f); lineTo(8f, 15f); lineTo(16f, 15f); lineTo(20f, 17f); lineTo(20f, 20f)
        }
    }

    val RadioMix: ImageVector by lazy {
        buildIcon("RadioMix") {
            moveTo(12f, 10f); arcTo(2f, 2f, 0f, true, true, 11.99f, 10f)
            moveTo(7f, 7f); lineTo(7f, 17f)
            moveTo(17f, 7f); lineTo(17f, 17f)
            moveTo(3f, 4f); lineTo(3f, 20f)
            moveTo(21f, 4f); lineTo(21f, 20f)
        }
    }

    val SleepTimer: ImageVector by lazy {
        buildIcon("SleepTimer") {
            moveTo(12f, 4f); arcTo(8f, 8f, 0f, true, true, 11.99f, 4f)
            moveTo(12f, 7f); lineTo(12f, 12f); lineTo(16f, 14f)
        }
    }

    val Share: ImageVector by lazy {
        buildIcon("Share") {
            moveTo(12f, 14f); lineTo(12f, 3f)
            moveTo(8f, 7f); lineTo(12f, 3f); lineTo(16f, 7f)
            moveTo(4f, 14f); lineTo(4f, 20f); lineTo(20f, 20f); lineTo(20f, 14f)
        }
    }

    val Tag: ImageVector by lazy {
        buildIcon("Tag") {
            moveTo(4f, 9f); lineTo(20f, 9f)
            moveTo(4f, 15f); lineTo(20f, 15f)
            moveTo(10f, 3f); lineTo(8f, 21f)
            moveTo(16f, 3f); lineTo(14f, 21f)
        }
    }

    val Pin: ImageVector by lazy {
        buildIcon("Pin") {
            moveTo(12f, 17f); lineTo(12f, 22f)
            moveTo(9f, 4f); lineTo(15f, 4f)
            lineTo(14f, 11f); lineTo(17f, 14f); lineTo(17f, 17f)
            lineTo(7f, 17f); lineTo(7f, 14f); lineTo(10f, 11f); close()
        }
    }

    val Settings: ImageVector by lazy {
        buildIcon("Settings") {
            moveTo(4f, 6f); lineTo(20f, 6f)
            moveTo(4f, 12f); lineTo(20f, 12f)
            moveTo(4f, 18f); lineTo(20f, 18f)
            moveTo(9f, 4f); lineTo(9f, 8f)
            moveTo(15f, 10f); lineTo(15f, 14f)
            moveTo(10f, 16f); lineTo(10f, 20f)
        }
    }

    val Equalizer: ImageVector by lazy {
        buildIcon("Equalizer") {
            moveTo(4f, 20f); lineTo(4f, 10f)
            moveTo(9f, 20f); lineTo(9f, 4f)
            moveTo(14f, 20f); lineTo(14f, 13f)
            moveTo(19f, 20f); lineTo(19f, 7f)
        }
    }

    val BarChart: ImageVector by lazy {
        buildIcon("BarChart") {
            moveTo(3f, 20f); lineTo(21f, 20f)
            moveTo(6f, 20f); lineTo(6f, 12f)
            moveTo(11f, 20f); lineTo(11f, 6f)
            moveTo(16f, 20f); lineTo(16f, 9f)
        }
    }

    val Driving: ImageVector by lazy {
        buildIcon("Driving") {
            moveTo(12f, 3f); arcTo(9f, 9f, 0f, true, true, 11.99f, 3f)
            moveTo(12f, 10f); arcTo(2f, 2f, 0f, true, true, 11.99f, 10f)
            moveTo(5f, 11f); lineTo(10f, 11f)
            moveTo(14f, 11f); lineTo(19f, 11f)
            moveTo(12f, 14f); lineTo(12f, 21f)
        }
    }

    val Offline: ImageVector by lazy {
        buildIcon("Offline") {
            moveTo(2f, 2f); lineTo(22f, 22f)
            moveTo(8.5f, 16.5f); lineTo(12f, 20f); lineTo(15.5f, 16.5f)
            moveTo(5f, 13f); lineTo(12f, 13f)
            moveTo(16f, 9f); lineTo(20f, 9f)
        }
    }
}
