package com.lin.music.core.playback.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PriorityTaskManager
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.DownloadManager
import com.lin.music.core.data.database.CacheDao
import com.lin.music.core.data.database.toEntity
import com.lin.music.core.innertube.stream.StreamResolver
import com.lin.music.core.model.AudioQuality
import com.lin.music.core.playback.queue.PlaybackStateHub
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StreamCacheDataSourceFactory

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PrecacheDataSourceFactory

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadCache

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StreamCache

@OptIn(UnstableApi::class)
@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {

    const val STREAM_URI_PREFIX = "lin://stream/"
    private const val STREAM_CACHE_MAX_BYTES = 256L * 1024L * 1024L

    fun streamCacheKey(videoId: String, quality: AudioQuality): String =
        "$videoId:${quality.name}"

    fun isStreamCacheKeyForVideo(cacheKey: String, videoId: String): Boolean =
        cacheKey == videoId || cacheKey.startsWith("$videoId:")

    fun extractVideoId(dataSpec: DataSpec): String? {
        val rawUri = dataSpec.uri.toString()
        return when {
            rawUri.startsWith(STREAM_URI_PREFIX) -> rawUri.removePrefix(STREAM_URI_PREFIX)
            !dataSpec.key.isNullOrBlank() -> dataSpec.key!!.substringBefore(':')
            else -> null
        }
    }

    private fun streamCacheKeyFactory(stateHub: PlaybackStateHub): CacheKeyFactory =
        CacheKeyFactory { spec ->
            val vid = extractVideoId(spec)
            if (!vid.isNullOrBlank()) {
                streamCacheKey(vid, stateHub.currentQuality)
            } else {
                spec.key ?: spec.uri.toString()
            }
        }

    @Provides
    @Singleton
    fun providePriorityTaskManager(): PriorityTaskManager = PriorityTaskManager()

    @Provides
    @Singleton
    fun provideDatabaseProvider(@ApplicationContext context: Context): DatabaseProvider =
        StandaloneDatabaseProvider(context)

    @Provides
    @Singleton
    @DownloadCache
    fun provideDownloadCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider
    ): Cache {
        val dir = File(context.noBackupFilesDir, "lin_downloads_cache")
        return createSafeSimpleCache(dir, NoOpCacheEvictor(), databaseProvider)
    }

    @Provides
    @Singleton
    @StreamCache
    fun provideStreamCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider
    ): Cache {
        val dir = File(context.noBackupFilesDir, "lin_stream_cache")
        val evictor = LeastRecentlyUsedCacheEvictor(STREAM_CACHE_MAX_BYTES)
        return createSafeSimpleCache(dir, evictor, databaseProvider)
    }

    private fun createSafeSimpleCache(
        cacheDir: File,
        evictor: CacheEvictor,
        databaseProvider: DatabaseProvider
    ): Cache {
        if (!cacheDir.exists()) cacheDir.mkdirs()
        return try {
            SimpleCache(cacheDir, evictor, databaseProvider)
        } catch (_: Throwable) {
            cacheDir.deleteRecursively()
            cacheDir.mkdirs()
            SimpleCache(cacheDir, evictor, databaseProvider)
        }
    }

    private fun createResolvingFactory(
        okHttpClient: OkHttpClient,
        streamResolver: StreamResolver,
        cacheDao: CacheDao,
        stateHub: PlaybackStateHub,
        qualityProvider: () -> AudioQuality,
        allowStreamUrlCache: Boolean,
        notifyCurrentStream: Boolean,
        streamCacheToGuard: Cache? = null
    ): DataSource.Factory {
        // Do NOT hardcode a fixed User-Agent here; each stream sets its matching client UA on DataSpec.
        val upstreamHttp = OkHttpDataSource.Factory(okHttpClient)

        return ResolvingDataSource.Factory(upstreamHttp) { dataSpec ->
            val videoId = extractVideoId(dataSpec) ?: return@Factory dataSpec
            val now = System.currentTimeMillis()
            val requestedQuality = qualityProvider()
            val stream = try {
                runBlocking {
                    val canReadCached = allowStreamUrlCache && requestedQuality == stateHub.currentQuality
                    val cached = if (canReadCached) cacheDao.getStream(videoId)?.toModel() else null
                    val isValidCached = cached != null &&
                        cached.userAgent.isNotBlank() &&
                        cached.clientSource != "IOS" &&
                        cached.clientSource != "ANDROID_VR" &&
                        cached.expiresAtEpochMs > now + 60_000L
                    if (isValidCached && cached != null) {
                        cached
                    } else {
                        val fresh = streamResolver.resolveStream(videoId, requestedQuality)
                        if (allowStreamUrlCache) {
                            cacheDao.putStream(fresh.toEntity())
                        }
                        if (streamCacheToGuard != null &&
                            (cached == null ||
                                cached.mimeType != fresh.mimeType ||
                                cached.bitrate != fresh.bitrate ||
                                cached.clientSource != fresh.clientSource)
                        ) {
                            runCatching {
                                streamCacheToGuard.removeResource(streamCacheKey(videoId, requestedQuality))
                            }
                        }
                        fresh
                    }
                }
            } catch (e: IOException) {
                throw e
            } catch (c: CancellationException) {
                throw InterruptedIOException("Stream resolution cancelled for $videoId").apply { initCause(c) }
            } catch (t: Throwable) {
                throw IOException("Stream resolution failed for $videoId: ${t.message}", t)
            }

            if (notifyCurrentStream && stateHub.queueEngine.snapshot.currentTrack?.videoId == videoId) {
                stateHub.updateStreamInfo(stream)
            }
            val headers = buildMap {
                if (stream.userAgent.isNotBlank()) {
                    put("User-Agent", stream.userAgent)
                }
            }
            dataSpec.buildUpon()
                .setUri(stream.url)
                .setKey(videoId)
                .setHttpRequestHeaders(headers)
                .build()
        }
    }

    @Provides
    @Singleton
    @StreamCacheDataSourceFactory
    fun provideCacheDataSourceFactory(
        @DownloadCache downloadCache: Cache,
        @StreamCache streamCache: Cache,
        okHttpClient: OkHttpClient,
        streamResolver: StreamResolver,
        cacheDao: CacheDao,
        stateHub: PlaybackStateHub
    ): DataSource.Factory {
        val resolvingFactory = createResolvingFactory(
            okHttpClient = okHttpClient,
            streamResolver = streamResolver,
            cacheDao = cacheDao,
            stateHub = stateHub,
            qualityProvider = { stateHub.currentQuality },
            allowStreamUrlCache = true,
            notifyCurrentStream = true,
            streamCacheToGuard = streamCache
        )
        // Ephemeral stream layer reads & caches played bytes into @StreamCache keyed by videoId:quality
        val streamLayer = CacheDataSource.Factory()
            .setCache(streamCache)
            .setCacheKeyFactory(streamCacheKeyFactory(stateHub))
            .setUpstreamDataSourceFactory(resolvingFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        // Outer read-only layer serves offline @DownloadCache first without evicting downloads
        return CacheDataSource.Factory()
            .setCache(downloadCache)
            .setCacheWriteDataSinkFactory(null)
            .setUpstreamDataSourceFactory(streamLayer)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    @Provides
    @Singleton
    @PrecacheDataSourceFactory
    fun providePrecacheDataSourceFactory(
        @StreamCache streamCache: Cache,
        okHttpClient: OkHttpClient,
        streamResolver: StreamResolver,
        cacheDao: CacheDao,
        stateHub: PlaybackStateHub,
        priorityTaskManager: PriorityTaskManager
    ): CacheDataSource.Factory {
        val resolvingFactory = createResolvingFactory(
            okHttpClient = okHttpClient,
            streamResolver = streamResolver,
            cacheDao = cacheDao,
            stateHub = stateHub,
            qualityProvider = { stateHub.currentQuality },
            allowStreamUrlCache = true,
            notifyCurrentStream = false,
            streamCacheToGuard = streamCache
        )
        return CacheDataSource.Factory()
            .setCache(streamCache)
            .setCacheKeyFactory(streamCacheKeyFactory(stateHub))
            .setUpstreamDataSourceFactory(resolvingFactory)
            .setUpstreamPriorityTaskManager(priorityTaskManager)
            .setUpstreamPriority(C.PRIORITY_DOWNLOAD)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    @Provides
    @Singleton
    fun provideDownloadManager(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
        @DownloadCache downloadCache: Cache,
        okHttpClient: OkHttpClient,
        streamResolver: StreamResolver,
        cacheDao: CacheDao,
        stateHub: PlaybackStateHub
    ): DownloadManager {
        val resolvingFactory = createResolvingFactory(
            okHttpClient = okHttpClient,
            streamResolver = streamResolver,
            cacheDao = cacheDao,
            stateHub = stateHub,
            qualityProvider = { stateHub.downloadQuality },
            allowStreamUrlCache = false,
            notifyCurrentStream = false
        )
        val executor = ThreadPoolExecutor(
            2,
            2,
            30L,
            TimeUnit.SECONDS,
            LinkedBlockingQueue()
        ).apply { allowCoreThreadTimeOut(true) }
        return DownloadManager(
            context,
            databaseProvider,
            downloadCache,
            resolvingFactory,
            executor
        ).apply {
            maxParallelDownloads = 2
        }
    }
}
