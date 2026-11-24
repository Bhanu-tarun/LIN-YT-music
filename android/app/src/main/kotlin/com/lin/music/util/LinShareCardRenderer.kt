package com.lin.music.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.size.Size
import com.lin.music.core.designsystem.components.highResArtworkUrl
import com.lin.music.core.model.AlbumDetail
import com.lin.music.core.model.ArtistDetail
import com.lin.music.core.model.ListeningStatsSummary
import com.lin.music.core.model.PlaylistDetail
import com.lin.music.core.model.PlaylistSummary
import com.lin.music.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object LinShareCardRenderer {

    private const val CARD_WIDTH = 1080
    private const val CARD_HEIGHT = 1350

    suspend fun loadBitmap(context: Context, url: String?, sizePx: Int = 512): Bitmap? {
        if (url.isNullOrBlank()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val loader = SingletonImageLoader.get(context)
                val req = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .size(Size(sizePx, sizePx))
                    .precision(Precision.INEXACT)
                    .build()
                val res = loader.execute(req) as? SuccessResult ?: return@runCatching null
                (res.image as? BitmapImage)?.bitmap
            }.getOrNull()
        }
    }

    suspend fun renderTrackCardUri(
        context: Context,
        track: Track,
        accentColor: Int = Color.parseColor("#E85D04"),
        timestampSec: Long? = null
    ): Uri? {
        val badgeText = if (timestampSec != null && timestampSec > 0L) {
            val m = timestampSec / 60L
            val s = timestampSec % 60L
            "TRACK · $m:${s.toString().padStart(2, '0')}"
        } else {
            "TRACK"
        }
        val sub = listOfNotNull(
            track.artistDisplay.takeIf { it.isNotBlank() },
            track.album?.title?.takeIf { it.isNotBlank() }
        ).joinToString(" · ")
        return renderMediaCardUri(
            context = context,
            artworkUrl = track.thumbnailUrl,
            fallbackArtworkUrl = null,
            title = track.title.ifBlank { "Track" },
            subtitle = sub,
            badgeText = badgeText,
            footerRightText = track.formattedDuration.ifBlank { "LIN" },
            accentColor = accentColor,
            circularArtwork = false,
            filePrefix = "lin_track_${track.videoId}"
        )
    }

    suspend fun renderAlbumCardUri(
        context: Context,
        album: AlbumDetail,
        accentColor: Int = Color.parseColor("#E85D04")
    ): Uri? {
        val badge = if (album.isEpOrSingle) "EP / SINGLE" else "ALBUM"
        val sub = listOfNotNull(
            album.artistDisplay.takeIf { it.isNotBlank() },
            album.year?.takeIf { it.isNotBlank() }
        ).joinToString(" · ")
        val countText = if (album.tracks.isNotEmpty()) {
            "${album.tracks.size} ${if (album.tracks.size == 1) "TRACK" else "TRACKS"}"
        } else {
            badge
        }
        return renderMediaCardUri(
            context = context,
            artworkUrl = album.thumbnailUrl,
            fallbackArtworkUrl = album.tracks.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl,
            title = album.title.ifBlank { "Album" },
            subtitle = sub,
            badgeText = badge,
            footerRightText = countText,
            accentColor = accentColor,
            circularArtwork = false,
            filePrefix = "lin_album_${album.browseId}"
        )
    }

    suspend fun renderPlaylistCardUri(
        context: Context,
        playlist: PlaylistDetail,
        accentColor: Int = Color.parseColor("#E85D04")
    ): Uri? {
        val sub = playlist.author?.takeIf { it.isNotBlank() }
            ?: playlist.description?.takeIf { it.isNotBlank() }.orEmpty()
        val countText = if (playlist.tracks.isNotEmpty()) {
            "${playlist.tracks.size} ${if (playlist.tracks.size == 1) "TRACK" else "TRACKS"}"
        } else {
            "PLAYLIST"
        }
        return renderMediaCardUri(
            context = context,
            artworkUrl = playlist.thumbnailUrl,
            fallbackArtworkUrl = playlist.tracks.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl,
            title = playlist.title.ifBlank { "Playlist" },
            subtitle = sub,
            badgeText = "PLAYLIST",
            footerRightText = countText,
            accentColor = accentColor,
            circularArtwork = false,
            filePrefix = "lin_playlist_${playlist.id}"
        )
    }

    suspend fun renderPlaylistSummaryCardUri(
        context: Context,
        playlist: PlaylistSummary,
        accentColor: Int = Color.parseColor("#E85D04")
    ): Uri? {
        val countText = playlist.trackCount?.takeIf { it > 0 }?.let {
            "$it ${if (it == 1) "TRACK" else "TRACKS"}"
        } ?: "PLAYLIST"
        return renderMediaCardUri(
            context = context,
            artworkUrl = playlist.thumbnailUrl,
            fallbackArtworkUrl = null,
            title = playlist.title.ifBlank { "Playlist" },
            subtitle = playlist.subtitle.orEmpty(),
            badgeText = "PLAYLIST",
            footerRightText = countText,
            accentColor = accentColor,
            circularArtwork = false,
            filePrefix = "lin_playlist_${playlist.id}"
        )
    }

    suspend fun renderArtistCardUri(
        context: Context,
        artist: ArtistDetail,
        accentColor: Int = Color.parseColor("#E85D04")
    ): Uri? {
        val releaseCount = artist.albums.size + artist.singlesAndEps.size
        val footerText = if (releaseCount > 0) {
            "$releaseCount ${if (releaseCount == 1) "RELEASE" else "RELEASES"}"
        } else {
            "ARTIST"
        }
        return renderMediaCardUri(
            context = context,
            artworkUrl = artist.thumbnailUrl,
            fallbackArtworkUrl = null,
            title = artist.name.ifBlank { "Artist" },
            subtitle = artist.subscriberCount.orEmpty(),
            badgeText = "ARTIST",
            footerRightText = footerText,
            accentColor = accentColor,
            circularArtwork = true,
            filePrefix = "lin_artist_${artist.browseId}"
        )
    }

    private suspend fun renderMediaCardUri(
        context: Context,
        artworkUrl: String?,
        fallbackArtworkUrl: String?,
        title: String,
        subtitle: String,
        badgeText: String,
        footerRightText: String,
        accentColor: Int,
        circularArtwork: Boolean,
        filePrefix: String
    ): Uri? = withContext(Dispatchers.Default) {
        val primaryUrl = artworkUrl?.takeIf { it.isNotBlank() } ?: fallbackArtworkUrl?.takeIf { it.isNotBlank() }
        val hiResUrl = highResArtworkUrl(primaryUrl) ?: primaryUrl
        val artwork = loadBitmap(context, hiResUrl, sizePx = 1024)
            ?: if (hiResUrl != primaryUrl) loadBitmap(context, primaryUrl, sizePx = 512) else null
            ?: if (!fallbackArtworkUrl.isNullOrBlank() && fallbackArtworkUrl != primaryUrl) {
                loadBitmap(context, highResArtworkUrl(fallbackArtworkUrl) ?: fallbackArtworkUrl, sizePx = 1024)
            } else null

        val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        runCatching {
            val canvas = Canvas(bitmap)
            drawCardBackground(canvas, accentColor)

            // Header: LIN wordmark + type badge
            val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 40f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.22f
            }
            canvas.drawText("LIN", 84f, 114f, brandPaint)
            canvas.drawRect(84f, 128f, 152f, 134f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor })

            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(190, 255, 255, 255)
                textSize = 24f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                letterSpacing = 0.14f
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(badgeText, CARD_WIDTH - 84f, 110f, badgePaint)

            // Centered cover (sharp 840x840 square for tracks/albums/playlists, 780x780 circle for artists)
            val artRect = if (circularArtwork) {
                RectF(150f, 220f, 930f, 1000f)
            } else {
                RectF(120f, 190f, 960f, 1030f)
            }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(56, 255, 255, 255)
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
            }
            if (circularArtwork) {
                val clipPath = Path().apply { addOval(artRect, Path.Direction.CW) }
                canvas.save()
                canvas.clipPath(clipPath)
            }
            if (artwork != null && !artwork.isRecycled && artwork.width > 0 && artwork.height > 0) {
                drawCenterCropBitmap(canvas, artwork, artRect)
            } else {
                val phPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1D1E24") }
                canvas.drawRect(artRect, phPaint)
                val monoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = accentColor
                    textSize = 180f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                val initial = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "♫"
                canvas.drawText(initial, artRect.centerX(), artRect.centerY() + 60f, monoPaint)
            }
            if (circularArtwork) {
                canvas.restore()
                canvas.drawOval(artRect, borderPaint)
            } else {
                canvas.drawRect(artRect, borderPaint)
            }

            // Title & metadata below artwork
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 46f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(185, 255, 255, 255)
                textSize = 28f
                typeface = Typeface.DEFAULT
            }
            canvas.save()
            canvas.translate(120f, 1068f)
            val safeTitle = title.ifBlank { "LIN" }
            val titleLayout = StaticLayout.Builder.obtain(safeTitle, 0, safeTitle.length, titlePaint, CARD_WIDTH - 240)
                .setMaxLines(2)
                .build()
            titleLayout.draw(canvas)
            if (subtitle.isNotBlank()) {
                canvas.translate(0f, titleLayout.height + 10f)
                val subLayout = StaticLayout.Builder.obtain(subtitle, 0, subtitle.length, subPaint, CARD_WIDTH - 240)
                    .setMaxLines(1)
                    .build()
                subLayout.draw(canvas)
            }
            canvas.restore()

            val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(38, 255, 255, 255)
                strokeWidth = 2f
            }
            canvas.drawLine(84f, CARD_HEIGHT - 120f, CARD_WIDTH - 84f, CARD_HEIGHT - 120f, dividerPaint)
            val footerLeft = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(175, 255, 255, 255)
                textSize = 24f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                letterSpacing = 0.08f
            }
            canvas.drawText("LIN · MUSIC", 84f, CARD_HEIGHT - 70f, footerLeft)

            val footerRight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accentColor
                textSize = 24f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(footerRightText, CARD_WIDTH - 84f, CARD_HEIGHT - 70f, footerRight)

            val safePrefix = filePrefix.replace(Regex("[^A-Za-z0-9_-]"), "_")
            saveCardPng(context, bitmap, "${safePrefix}_${System.currentTimeMillis()}.png")
        }.onFailure {
            if (!bitmap.isRecycled) bitmap.recycle()
        }.getOrNull()
    }

    suspend fun renderLyricsCardUri(
        context: Context,
        track: Track,
        selectedLines: List<String>,
        romanizedLines: List<String?> = emptyList(),
        accentColor: Int = Color.parseColor("#E85D04")
    ): Uri? = withContext(Dispatchers.Default) {
        val artwork = loadBitmap(context, track.thumbnailUrl)
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        runCatching {
            val canvas = Canvas(bitmap)

            drawCardBackground(canvas, accentColor)

        // Header: LIN wordmark + LYRICS badge
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.22f
        }
        canvas.drawText("LIN", 84f, 114f, brandPaint)

        val accentBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
        canvas.drawRect(84f, 128f, 152f, 134f, accentBarPaint)

        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 255, 255, 255)
            textSize = 24f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.14f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("LYRICS CARD", CARD_WIDTH - 84f, 110f, badgePaint)

        // Track artwork & metadata block
        val artRect = RectF(84f, 184f, 244f, 344f)
        if (artwork != null && !artwork.isRecycled) {
            drawCenterCropBitmap(canvas, artwork, artRect)
        } else {
            val phPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1D1E24") }
            canvas.drawRect(artRect, phPaint)
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(45, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRect(artRect, borderPaint)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(185, 255, 255, 255)
            textSize = 27f
            typeface = Typeface.DEFAULT
        }
        canvas.save()
        canvas.translate(278f, 200f)
        val titleLayout = StaticLayout.Builder.obtain(track.title, 0, track.title.length, titlePaint, CARD_WIDTH - 362)
            .setMaxLines(2)
            .build()
        titleLayout.draw(canvas)
        canvas.translate(0f, titleLayout.height + 12f)
        val sub = listOfNotNull(
            track.artistDisplay.takeIf { it.isNotBlank() },
            track.album?.title?.takeIf { it.isNotBlank() }
        ).joinToString(" · ")
        val artistLayout = StaticLayout.Builder.obtain(sub, 0, sub.length, artistPaint, CARD_WIDTH - 362)
            .setMaxLines(2)
            .build()
        artistLayout.draw(canvas)
        canvas.restore()

        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(38, 255, 255, 255)
            strokeWidth = 2f
        }
        canvas.drawLine(84f, 392f, CARD_WIDTH - 84f, 392f, dividerPaint)

        // Lyrics body block
        val safeLines = selectedLines.take(6).ifEmpty { listOf(track.title) }
        val totalChars = safeLines.sumOf { it.length }
        val lyricSize = when {
            safeLines.size <= 2 && totalChars < 80 -> 56f
            safeLines.size <= 4 && totalChars < 170 -> 46f
            else -> 38f
        }
        val lyricPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = lyricSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val romPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(170, 255, 255, 255)
            textSize = (lyricSize * 0.56f).coerceAtLeast(22f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }

        val quoteBar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
        val lyricsTop = 446f
        val maxLyricsBottom = CARD_HEIGHT - 170f
        var cursorY = lyricsTop

        safeLines.forEachIndexed { idx, line ->
            if (cursorY > maxLyricsBottom) return@forEachIndexed
            val layout = StaticLayout.Builder.obtain(line, 0, line.length, lyricPaint, CARD_WIDTH - 196)
                .setLineSpacing(6f, 1.08f)
                .build()
            if (idx == 0) {
                canvas.drawRect(84f, cursorY + 8f, 92f, cursorY + layout.height - 6f, quoteBar)
            }
            canvas.save()
            canvas.translate(114f, cursorY)
            layout.draw(canvas)
            canvas.restore()
            cursorY += layout.height

            val rom = romanizedLines.getOrNull(idx)?.takeIf { !it.isNullOrBlank() }
            if (rom != null && cursorY < maxLyricsBottom - 36f) {
                cursorY += 8f
                val romLayout = StaticLayout.Builder.obtain(rom, 0, rom.length, romPaint, CARD_WIDTH - 196)
                    .setMaxLines(2)
                    .build()
                canvas.save()
                canvas.translate(114f, cursorY)
                romLayout.draw(canvas)
                canvas.restore()
                cursorY += romLayout.height
            }
            cursorY += 28f
        }

        // Footer
        canvas.drawLine(84f, CARD_HEIGHT - 134f, CARD_WIDTH - 84f, CARD_HEIGHT - 134f, dividerPaint)
        val footerLeft = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(170, 255, 255, 255)
            textSize = 24f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            letterSpacing = 0.08f
        }
        canvas.drawText("LIN · MUSIC CLIENT", 84f, CARD_HEIGHT - 80f, footerLeft)

        val footerRight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = 24f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(track.formattedDuration.ifBlank { "LIN" }, CARD_WIDTH - 84f, CARD_HEIGHT - 80f, footerRight)

        saveCardPng(context, bitmap, "lin_lyrics_${track.videoId}_${System.currentTimeMillis()}.png")
        }.onFailure {
            if (!bitmap.isRecycled) bitmap.recycle()
        }.getOrNull()
    }

    suspend fun renderStatsCardUri(
        context: Context,
        summary: ListeningStatsSummary,
        accentColor: Int = Color.parseColor("#E85D04")
    ): Uri? = withContext(Dispatchers.Default) {
        val topTrackUrl = summary.topTracks.firstOrNull()?.track?.thumbnailUrl
        val heroArt = loadBitmap(context, topTrackUrl)
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        runCatching {
            val canvas = Canvas(bitmap)

            drawCardBackground(canvas, accentColor)

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.22f
        }
        canvas.drawText("LIN", 84f, 114f, brandPaint)
        canvas.drawRect(84f, 128f, 152f, 134f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor })

        val rangePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 255, 255, 255)
            textSize = 24f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.14f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("LISTENING STATS · ${summary.range.label.uppercase()}", CARD_WIDTH - 84f, 110f, rangePaint)

        // Hero time number
        val totalMinutes = (summary.totalListenedMs / 60_000L).toInt()
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        val heroText = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
        val heroPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 112f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(heroText, 84f, 276f, heroPaint)

        val subMetricsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 255, 255, 255)
            textSize = 28f
            typeface = Typeface.DEFAULT
        }
        canvas.drawText(
            "${summary.totalPlays} plays · ${summary.uniqueArtists} artists · ${summary.activeStreakDays}d streak",
            84f,
            326f,
            subMetricsPaint
        )

        if (heroArt != null && !heroArt.isRecycled) {
            drawCenterCropBitmap(canvas, heroArt, RectF(CARD_WIDTH - 244f, 178f, CARD_WIDTH - 84f, 338f))
        }

        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(40, 255, 255, 255)
            strokeWidth = 2f
        }
        canvas.drawLine(84f, 376f, CARD_WIDTH - 84f, 376f, dividerPaint)

        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = 24f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.12f
        }
        val rowTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val rowMetaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(170, 255, 255, 255)
            textSize = 24f
            textAlign = Paint.Align.RIGHT
        }

        canvas.drawText("TOP ARTISTS", 84f, 434f, sectionPaint)
        var y = 488f
        summary.topArtists.take(4).forEachIndexed { idx, artist ->
            val label = "${idx + 1}.  ${artist.name}".take(28)
            canvas.drawText(label, 84f, y, rowTitlePaint)
            canvas.drawText("${artist.playCount} plays", CARD_WIDTH - 84f, y, rowMetaPaint)
            y += 56f
        }

        y += 24f
        canvas.drawLine(84f, y, CARD_WIDTH - 84f, y, dividerPaint)
        y += 54f
        canvas.drawText("TOP TRACKS", 84f, y, sectionPaint)
        y += 52f
        summary.topTracks.take(4).forEachIndexed { idx, stat ->
            val label = "${idx + 1}.  ${stat.track.title}".take(30)
            canvas.drawText(label, 84f, y, rowTitlePaint)
            canvas.drawText("${stat.playCount}x", CARD_WIDTH - 84f, y, rowMetaPaint)
            y += 56f
        }

        // 24h Sparkline footer
        val chartTop = CARD_HEIGHT - 270f
        val chartBottom = CARD_HEIGHT - 160f
        canvas.drawText("HOURLY RHYTHM", 84f, chartTop - 22f, sectionPaint)
        val maxVal = (summary.hourlyMs.maxOrNull() ?: 1L).coerceAtLeast(1L).toFloat()
        val barAreaWidth = (CARD_WIDTH - 168f) / 24f
        val barFill = Paint(Paint.ANTI_ALIAS_FLAG)
        summary.hourlyMs.forEachIndexed { hour, ms ->
            val ratio = (ms / maxVal).coerceIn(0.06f, 1f)
            val left = 84f + hour * barAreaWidth + 4f
            val right = left + barAreaWidth - 8f
            val top = chartBottom - ratio * (chartBottom - chartTop)
            barFill.color = if (hour == summary.peakHourOfDay) accentColor else Color.argb(120, 255, 255, 255)
            canvas.drawRect(left, top, right, chartBottom, barFill)
        }

        canvas.drawLine(84f, CARD_HEIGHT - 120f, CARD_WIDTH - 84f, CARD_HEIGHT - 120f, dividerPaint)
        canvas.drawText("LIN · PERSONAL LISTENING REPORT", 84f, CARD_HEIGHT - 70f, subMetricsPaint)

        saveCardPng(context, bitmap, "lin_stats_${summary.range.name.lowercase()}_${System.currentTimeMillis()}.png")
        }.onFailure {
            if (!bitmap.isRecycled) bitmap.recycle()
        }.getOrNull()
    }

    private fun drawCardBackground(canvas: Canvas, accentColor: Int) {
        canvas.drawColor(Color.parseColor("#0A0A0D"))
        val glowColor = Color.argb(
            52,
            Color.red(accentColor),
            Color.green(accentColor),
            Color.blue(accentColor)
        )
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT * 0.55f,
                intArrayOf(glowColor, Color.TRANSPARENT),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), glowPaint)
    }

    private fun drawCenterCropBitmap(canvas: Canvas, bitmap: Bitmap, dest: RectF) {
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()
        if (bw <= 0f || bh <= 0f) return
        val scale = maxOf(dest.width() / bw, dest.height() / bh)
        val sw = (dest.width() / scale).toInt()
        val sh = (dest.height() / scale).toInt()
        val left = ((bitmap.width - sw) / 2).coerceAtLeast(0)
        val top = ((bitmap.height - sh) / 2).coerceAtLeast(0)
        val src = Rect(left, top, (left + sw).coerceAtMost(bitmap.width), (top + sh).coerceAtMost(bitmap.height))
        canvas.drawBitmap(bitmap, src, dest, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    private fun saveCardPng(context: Context, bitmap: Bitmap, fileName: String): Uri? {
        return runCatching {
            val dir = File(context.cacheDir, "share_cards").apply { mkdirs() }
            // Keep cache tidy by pruning old share cards
            dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(8)?.forEach { it.delete() }
            val file = File(dir, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
            }
            bitmap.recycle()
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    }
}
