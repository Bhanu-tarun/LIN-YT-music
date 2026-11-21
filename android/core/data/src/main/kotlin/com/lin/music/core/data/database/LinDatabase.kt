package com.lin.music.core.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        AlbumEntity::class,
        ArtistEntity::class,
        ItemTagEntity::class,
        StreamCacheEntity::class,
        DownloadEntity::class,
        SearchHistoryEntity::class,
        PersistedQueueEntity::class,
        LyricsCacheEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class LinDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun albumArtistDao(): AlbumArtistDao
    abstract fun tagDao(): TagDao
    abstract fun cacheDao(): CacheDao
}
