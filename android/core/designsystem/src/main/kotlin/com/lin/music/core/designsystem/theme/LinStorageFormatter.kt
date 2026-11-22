package com.lin.music.core.designsystem.theme

import java.util.Locale

private const val BYTES_PER_KB = 1024L
private const val BYTES_PER_MB = 1024L * 1024L
private const val BYTES_PER_GB = 1024L * 1024L * 1024L

fun formatStorageBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 mb"
    return when {
        bytes < BYTES_PER_MB -> "${(bytes / BYTES_PER_KB).coerceAtLeast(1L)} kb"
        bytes < BYTES_PER_GB -> "${bytes / BYTES_PER_MB} mb"
        else -> String.format(Locale.US, "%.2f gb", bytes / BYTES_PER_GB.toDouble())
    }
}
