package com.lin.music.core.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE inLibrary = 1 ORDER BY updatedAtMs DESC")
    fun observeLibraryTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isLiked = 1 ORDER BY updatedAtMs DESC")
    fun observeLikedTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE inHistory = 1 ORDER BY historyTimestampMs DESC LIMIT 200")
    fun observeHistoryTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE playCount > 0 ORDER BY historyTimestampMs DESC LIMIT :limit")
    fun observeRecentLocalPlays(limit: Int = 16): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE playCount > 0 ORDER BY playCount DESC, historyTimestampMs DESC LIMIT :limit")
    fun observeFrequentLocalPlays(limit: Int = 16): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE playCount > 0 ORDER BY playCount DESC, historyTimestampMs DESC LIMIT :limit")
    fun observePlayedTracksForSpeedDial(limit: Int = 60): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE albumId = :albumId ORDER BY updatedAtMs ASC")
    suspend fun getTracksByAlbumId(albumId: String): List<TrackEntity>

    @Query("SELECT tracks.* FROM tracks INNER JOIN downloads ON tracks.videoId = downloads.videoId WHERE downloads.state = 2 ORDER BY downloads.updatedAtMs DESC")
    fun observeDownloadedTracks(): Flow<List<TrackEntity>>

    @Query("SELECT tracks.* FROM tracks INNER JOIN downloads ON tracks.videoId = downloads.videoId WHERE downloads.state = 2 AND (downloads.sourceFlags & :sourceMask) != 0 ORDER BY downloads.updatedAtMs DESC")
    fun observeDownloadedTracksBySource(sourceMask: Int): Flow<List<TrackEntity>>

    @Query("SELECT tracks.* FROM tracks INNER JOIN downloads ON tracks.videoId = downloads.videoId WHERE downloads.state = 2 AND tracks.albumId IS NOT NULL AND tracks.albumId != '' ORDER BY tracks.albumTitle ASC, tracks.updatedAtMs ASC")
    fun observeDownloadedAlbumTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE inLibrary = 1 OR isLiked = 1 OR playCount > 0 ORDER BY playCount DESC, isLiked DESC, historyTimestampMs DESC LIMIT :limit")
    suspend fun getSmartDownloadCandidates(limit: Int): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE inHistory = 1 OR playCount > 0 ORDER BY historyTimestampMs DESC LIMIT :limit")
    suspend fun getRecentlyPlayedCandidates(limit: Int): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE videoId = :videoId LIMIT 1")
    suspend fun getById(videoId: String): TrackEntity?

    @Query("SELECT * FROM tracks ORDER BY historyTimestampMs DESC, updatedAtMs DESC")
    fun observeAllKnownTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks")
    suspend fun getAllKnownTracksSnapshot(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE videoId IN (:videoIds)")
    suspend fun getByIds(videoIds: List<String>): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: TrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tracks: List<TrackEntity>)

    @Query("UPDATE tracks SET isLiked = :liked, updatedAtMs = :now WHERE videoId = :videoId")
    suspend fun updateLiked(videoId: String, liked: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE tracks SET inLibrary = :inLibrary, feedbackTokenAdd = COALESCE(:addTok, feedbackTokenAdd), feedbackTokenRemove = COALESCE(:remTok, feedbackTokenRemove), updatedAtMs = :now WHERE videoId = :videoId")
    suspend fun updateLibraryState(
        videoId: String,
        inLibrary: Boolean,
        addTok: String? = null,
        remTok: String? = null,
        now: Long = System.currentTimeMillis()
    )

    @Query("UPDATE tracks SET albumId = :albumId, albumTitle = COALESCE(:albumTitle, albumTitle) WHERE videoId = :videoId")
    suspend fun updateAlbumRef(videoId: String, albumId: String, albumTitle: String?)
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists WHERE inLibrary = 1 ORDER BY updatedAtMs DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query(
        """
        SELECT DISTINCT p.* FROM playlists p
        INNER JOIN playlist_tracks pt ON p.id = pt.playlistId
        INNER JOIN downloads d ON pt.videoId = d.videoId
        WHERE d.state = 2
        ORDER BY p.updatedAtMs DESC
        """
    )
    fun observeDownloadedPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getPlaylist(playlistId: String): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylists(playlists: List<PlaylistEntity>)

    @Query("SELECT * FROM playlists")
    suspend fun getAllPlaylistsSnapshot(): List<PlaylistEntity>

    @Query("UPDATE playlists SET autoDownload = :enabled WHERE id = :playlistId")
    suspend fun setAutoDownload(playlistId: String, enabled: Boolean)

    @Query("SELECT * FROM playlists WHERE autoDownload = 1")
    suspend fun getAutoDownloadPlaylists(): List<PlaylistEntity>

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Query("UPDATE playlists SET inLibrary = 0 WHERE id NOT IN (:keepIds)")
    suspend fun prunePlaylistsNotIn(keepIds: List<String>)

    @Query("SELECT tracks.* FROM tracks INNER JOIN playlist_tracks ON tracks.videoId = playlist_tracks.videoId WHERE playlist_tracks.playlistId = :playlistId ORDER BY playlist_tracks.position ASC")
    fun observePlaylistTracks(playlistId: String): Flow<List<TrackEntity>>

    @Query("SELECT tracks.* FROM tracks INNER JOIN playlist_tracks ON tracks.videoId = playlist_tracks.videoId WHERE playlist_tracks.playlistId = :playlistId ORDER BY playlist_tracks.position ASC")
    suspend fun getPlaylistTracksSnapshot(playlistId: String): List<TrackEntity>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getPlaylistCrossRefs(playlistId: String): List<PlaylistTrackCrossRef>

    @Query("SELECT playlistId FROM playlist_tracks WHERE videoId = :videoId")
    fun observePlaylistIdsContainingTrack(videoId: String): Flow<List<String>>

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylistTracks(playlistId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTracks(refs: List<PlaylistTrackCrossRef>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun removeTrackFromPlaylistLocal(playlistId: String, videoId: String)
}

@Dao
interface AlbumArtistDao {
    @Query("SELECT * FROM albums WHERE inLibrary = 1 ORDER BY updatedAtMs DESC")
    fun observeLibraryAlbums(): Flow<List<AlbumEntity>>

    @Query(
        """
        SELECT DISTINCT a.* FROM albums a
        INNER JOIN tracks t ON t.albumId = a.browseId
        INNER JOIN downloads d ON t.videoId = d.videoId
        WHERE d.state = 2
        ORDER BY a.updatedAtMs DESC
        """
    )
    fun observeDownloadedAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE browseId = :browseId LIMIT 1")
    suspend fun getAlbum(browseId: String): AlbumEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlbums(albums: List<AlbumEntity>)

    @Query("UPDATE albums SET inLibrary = :inLibrary WHERE browseId = :browseId")
    suspend fun updateAlbumLibrary(browseId: String, inLibrary: Boolean)

    @Query("SELECT * FROM artists WHERE isSubscribed = 1 ORDER BY name ASC")
    fun observeSubscribedArtists(): Flow<List<ArtistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArtists(artists: List<ArtistEntity>)

    @Query("UPDATE artists SET isSubscribed = :subscribed WHERE browseId = :browseId")
    suspend fun updateArtistSubscribed(browseId: String, subscribed: Boolean)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM item_tags")
    fun observeAllTags(): Flow<List<ItemTagEntity>>

    @Query("SELECT DISTINCT tag FROM item_tags ORDER BY tag ASC")
    fun observeDistinctTagNames(): Flow<List<String>>

    @Query("SELECT tag FROM item_tags WHERE itemId = :itemId AND itemType = :itemType")
    fun observeTagsForItem(itemId: String, itemType: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(entity: ItemTagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTags(entities: List<ItemTagEntity>)

    @Query("DELETE FROM item_tags WHERE itemId = :itemId AND itemType = :itemType AND tag = :tag")
    suspend fun deleteTag(itemId: String, itemType: String, tag: String)

    @Query("SELECT * FROM item_tags")
    suspend fun getAllTagsSnapshot(): List<ItemTagEntity>
}

@Dao
interface CacheDao {
    @Query("SELECT * FROM stream_cache WHERE videoId = :videoId LIMIT 1")
    suspend fun getStream(videoId: String): StreamCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putStream(entity: StreamCacheEntity)

    @Query("DELETE FROM stream_cache WHERE videoId = :videoId")
    suspend fun invalidateStream(videoId: String)

    @Query("DELETE FROM stream_cache")
    suspend fun clearAllStreamCache()

    @Query("SELECT * FROM lyrics_cache WHERE videoId = :videoId LIMIT 1")
    suspend fun getLyrics(videoId: String): LyricsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putLyrics(entity: LyricsCacheEntity)

    @Query("SELECT * FROM search_history ORDER BY timestampMs DESC LIMIT 20")
    fun observeSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchQuery(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE `query` = :query")
    suspend fun deleteSearchQuery(query: String)

    @Query("SELECT * FROM downloads")
    fun observeDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE (sourceFlags & :sourceMask) != 0")
    suspend fun getDownloadsBySource(sourceMask: Int): List<DownloadEntity>

    @Query("SELECT * FROM downloads")
    suspend fun getAllDownloadsSnapshot(): List<DownloadEntity>

    @Query("DELETE FROM downloads")
    suspend fun clearAllDownloads()

    @Query("SELECT * FROM downloads WHERE videoId = :videoId LIMIT 1")
    suspend fun getDownload(videoId: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownload(entity: DownloadEntity)

    @Query("DELETE FROM downloads WHERE videoId = :videoId")
    suspend fun deleteDownload(videoId: String)

    @Query("SELECT * FROM persisted_queue ORDER BY position ASC")
    suspend fun getPersistedQueue(): List<PersistedQueueEntity>

    @Query("DELETE FROM persisted_queue")
    suspend fun clearPersistedQueue()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePersistedQueue(items: List<PersistedQueueEntity>)

    @Query("SELECT * FROM speed_dial_pins ORDER BY pinnedAtMs ASC")
    fun observeSpeedDialPins(): Flow<List<SpeedDialPinEntity>>

    @Query("SELECT * FROM speed_dial_pins ORDER BY pinnedAtMs ASC")
    suspend fun getSpeedDialPinsSnapshot(): List<SpeedDialPinEntity>

    @Query("SELECT * FROM speed_dial_pins WHERE `key` = :key LIMIT 1")
    suspend fun getSpeedDialPin(key: String): SpeedDialPinEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSpeedDialPin(pin: SpeedDialPinEntity)

    @Query("DELETE FROM speed_dial_pins WHERE `key` = :key")
    suspend fun deleteSpeedDialPin(key: String)

    @Insert
    suspend fun insertPlaySession(session: PlaySessionEntity)

    @Query(
        """
        SELECT videoId, SUM(listenedMs) AS totalListenedMs, MAX(playedAtMs) AS lastPlayedAtMs
        FROM play_sessions
        WHERE playedAtMs >= :sinceMs
        GROUP BY videoId
        ORDER BY totalListenedMs DESC, lastPlayedAtMs DESC
        LIMIT :limit
        """
    )
    suspend fun getListenStatsSince(sinceMs: Long, limit: Int): List<TrackListenStat>

    @Query("UPDATE lyrics_cache SET syncOffsetMs = :offsetMs, updatedAtMs = :updatedAtMs WHERE videoId = :videoId")
    suspend fun updateLyricsOffset(videoId: String, offsetMs: Long, updatedAtMs: Long = System.currentTimeMillis())

    @Query("SELECT * FROM play_sessions ORDER BY playedAtMs DESC")
    fun observeAllPlaySessions(): Flow<List<PlaySessionEntity>>

    @Query("SELECT * FROM play_sessions")
    suspend fun getAllPlaySessions(): List<PlaySessionEntity>

    @Query("DELETE FROM play_sessions WHERE playedAtMs < :beforeMs")
    suspend fun prunePlaySessionsBefore(beforeMs: Long)
}
