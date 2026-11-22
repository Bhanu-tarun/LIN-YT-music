package com.lin.music.core.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lin.music.core.data.database.AlbumArtistDao
import com.lin.music.core.data.database.CacheDao
import com.lin.music.core.data.database.LinDatabase
import com.lin.music.core.data.database.PlaylistDao
import com.lin.music.core.data.database.TagDao
import com.lin.music.core.data.database.TrackDao
import com.lin.music.core.data.datastore.AuthStore
import com.lin.music.core.data.datastore.PreferencesStore
import com.lin.music.core.innertube.RegionProvider
import com.lin.music.core.innertube.SessionProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindModule {
    @Binds
    @Singleton
    abstract fun bindSessionProvider(authStore: AuthStore): SessionProvider

    @Binds
    @Singleton
    abstract fun bindRegionProvider(preferencesStore: PreferencesStore): RegionProvider
}

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val dispatcher = Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 16
        }
        return OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(ConnectionPool(8, 2, TimeUnit.MINUTES))
            .retryOnConnectionFailure(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE tracks ADD COLUMN playCount INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE playlists ADD COLUMN inLibrary INTEGER NOT NULL DEFAULT 1")
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE downloads ADD COLUMN sourceFlags INTEGER NOT NULL DEFAULT 1")
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_inLibrary` ON `tracks` (`inLibrary`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_isLiked` ON `tracks` (`isLiked`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_inHistory_historyTimestampMs` ON `tracks` (`inHistory`, `historyTimestampMs`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_albumId` ON `tracks` (`albumId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_playCount` ON `tracks` (`playCount`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_tracks_videoId` ON `playlist_tracks` (`videoId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_state` ON `downloads` (`state`)")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `speed_dial_pins` (`key` TEXT NOT NULL, `itemJson` TEXT NOT NULL, `pinnedAtMs` INTEGER NOT NULL, PRIMARY KEY(`key`))"
            )
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `play_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `videoId` TEXT NOT NULL, `listenedMs` INTEGER NOT NULL, `playedAtMs` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_play_sessions_playedAtMs` ON `play_sessions` (`playedAtMs`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_play_sessions_videoId_playedAtMs` ON `play_sessions` (`videoId`, `playedAtMs`)"
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LinDatabase =
        Room.databaseBuilder(context, LinDatabase::class.java, "lin_music.db")
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideTrackDao(db: LinDatabase): TrackDao = db.trackDao()

    @Provides
    fun providePlaylistDao(db: LinDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideAlbumArtistDao(db: LinDatabase): AlbumArtistDao = db.albumArtistDao()

    @Provides
    fun provideTagDao(db: LinDatabase): TagDao = db.tagDao()

    @Provides
    fun provideCacheDao(db: LinDatabase): CacheDao = db.cacheDao()
}
