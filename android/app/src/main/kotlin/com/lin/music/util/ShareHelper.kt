package com.lin.music.util

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.lin.music.core.model.Track

object ShareHelper {

    fun trackUrl(videoId: String, timestampSec: Long? = null): String {
        val base = "https://music.youtube.com/watch?v=$videoId"
        return if (timestampSec != null && timestampSec > 0L) "$base&t=$timestampSec" else base
    }

    fun albumUrl(browseId: String): String = "https://music.youtube.com/browse/$browseId"

    fun playlistUrl(playlistId: String): String =
        "https://music.youtube.com/playlist?list=${playlistId.removePrefix("VL")}"

    fun artistUrl(browseId: String): String = "https://music.youtube.com/channel/$browseId"

    fun shareTrack(context: Context, track: Track, timestampSec: Long? = null) {
        val url = trackUrl(track.videoId, timestampSec)
        val text = "${track.title} — ${track.artistDisplay}\n$url"
        shareText(context, text, track.title)
    }

    fun shareText(context: Context, text: String, subject: String? = null) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (!subject.isNullOrBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        val chooser = Intent.createChooser(intent, "Share via").apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        runCatching { context.startActivity(chooser) }
    }

    fun shareImageUri(
        context: Context,
        uri: Uri,
        title: String = "Share card",
        text: String? = null
    ): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            if (!text.isNullOrBlank()) {
                putExtra(Intent.EXTRA_TEXT, text)
            }
            clipData = ClipData.newRawUri(title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        return runCatching {
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
