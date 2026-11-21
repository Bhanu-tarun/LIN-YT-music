package com.lin.music

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.lin.music.core.playback.download.LinDownloadService
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class LinApplication : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var okHttpClient: OkHttpClient

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                if (::okHttpClient.isInitialized) {
                    add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
                }
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(context.cacheDir, "coil_image_cache"))
                    .maxSizeBytes(40L * 1024L * 1024L)
                    .build()
            }
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                LinDownloadService.CHANNEL_ID,
                "Offline Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background audio downloads for offline playback"
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}
