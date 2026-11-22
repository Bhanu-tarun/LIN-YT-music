package com.lin.music.core.data.repository

import com.lin.music.core.data.database.AlbumArtistDao
import com.lin.music.core.data.database.ArtistEntity
import com.lin.music.core.data.database.CacheDao
import com.lin.music.core.data.database.DownloadEntity
import com.lin.music.core.data.database.ItemTagEntity
import com.lin.music.core.data.database.LyricsCacheEntity
import com.lin.music.core.data.database.PlaySessionEntity
import com.lin.music.core.data.database.PlaylistDao
import com.lin.music.core.data.database.PlaylistEntity
import com.lin.music.core.data.database.PlaylistTrackCrossRef
import com.lin.music.core.data.database.SearchHistoryEntity
import com.lin.music.core.data.database.SpeedDialPinEntity
import com.lin.music.core.data.database.TagDao
import com.lin.music.core.data.database.TrackDao
import com.lin.music.core.data.database.toEntity
import com.lin.music.core.data.datastore.AuthStore
import com.lin.music.core.data.datastore.PreferencesStore
import com.lin.music.core.innertube.InnerTubeApi
import com.lin.music.core.lyrics.LyricsRepository
import com.lin.music.core.model.AccountSession
import com.lin.music.core.model.AlbumDetail
import com.lin.music.core.model.AlbumRef
import com.lin.music.core.model.AlbumSummary
import com.lin.music.core.model.ArtistDetail
import com.lin.music.core.model.ArtistSummary
import com.lin.music.core.model.HomeItem
import com.lin.music.core.model.HomePage
import com.lin.music.core.model.ItemTagRecord
import com.lin.music.core.model.LinBackupPayload
import com.lin.music.core.model.LyricsResult
import com.lin.music.core.model.NextPage
import com.lin.music.core.model.PlaylistDetail
import com.lin.music.core.model.PlaylistSummary
import com.lin.music.core.model.SearchFilter
import com.lin.music.core.model.SearchResultPage
import com.lin.music.core.model.SpeedDialEntry
import com.lin.music.core.model.Track
import com.lin.music.core.model.speedDialKey
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val innerTubeApi: InnerTubeApi,
    private val lyricsRepository: LyricsRepository,
    private val authStore: AuthStore,
    private val preferencesStore: PreferencesStore,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val albumArtistDao: AlbumArtistDao,
    private val tagDao: TagDao,
    private val cacheDao: CacheDao
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Volatile
    var lastFullSyncAtMs: Long = 0L

    val sessionFlow: Flow<AccountSession?> = authStore.sessionFlow

    val contentRegionFlow: Flow<String> = preferencesStore.preferencesFlow
        .map { it.contentRegion }
        .distinctUntilChanged()

    suspend fun onContentRegionChanged() {
        innerTubeApi.clearVisitorDataCache()
        cacheDao.clearAllStreamCache()
    }

    fun observeLibraryTracks(): Flow<List<Track>> =
        combine(trackDao.observeLibraryTracks(), tagDao.observeAllTags()) { tracks, tags ->
            val tagMap = tags.filter { it.itemType == "track" }.groupBy({ it.itemId }, { it.tag })
            tracks.map { it.toModel(tags = tagMap[it.videoId].orEmpty()) }
        }

    fun observeLikedTracks(): Flow<List<Track>> =
        combine(trackDao.observeLikedTracks(), tagDao.observeAllTags()) { tracks, tags ->
            val tagMap = tags.filter { it.itemType == "track" }.groupBy({ it.itemId }, { it.tag })
            tracks.map { it.toModel(tags = tagMap[it.videoId].orEmpty()) }
        }

    fun observeHistoryTracks(): Flow<List<Track>> =
        combine(trackDao.observeHistoryTracks(), tagDao.observeAllTags()) { tracks, tags ->
            val tagMap = tags.filter { it.itemType == "track" }.groupBy({ it.itemId }, { it.tag })
            tracks.map { it.toModel(tags = tagMap[it.videoId].orEmpty()) }
        }

    fun observeDownloadedTracks(): Flow<List<Track>> =
        combine(trackDao.observeDownloadedTracks(), tagDao.observeAllTags()) { tracks, tags ->
            val tagMap = tags.filter { it.itemType == "track" }.groupBy({ it.itemId }, { it.tag })
            tracks.map { it.toModel(tags = tagMap[it.videoId].orEmpty()) }
        }

    fun observeDownloadedAlbumTracks(): Flow<List<Track>> =
        combine(trackDao.observeDownloadedAlbumTracks(), tagDao.observeAllTags()) { tracks, tags ->
            val tagMap = tags.filter { it.itemType == "track" }.groupBy({ it.itemId }, { it.tag })
            tracks.map { it.toModel(tags = tagMap[it.videoId].orEmpty()) }
        }

    fun observeSmartDownloadedTracks(): Flow<List<Track>> =
        combine(trackDao.observeDownloadedTracksBySource(DownloadEntity.SOURCE_SMART), tagDao.observeAllTags()) { tracks, tags ->
            val tagMap = tags.filter { it.itemType == "track" }.groupBy({ it.itemId }, { it.tag })
            tracks.map { it.toModel(tags = tagMap[it.videoId].orEmpty()) }
        }

    fun observeRecentlyPlayedDownloadedTracks(): Flow<List<Track>> =
        combine(trackDao.observeDownloadedTracksBySource(DownloadEntity.SOURCE_RECENTLY_PLAYED), tagDao.observeAllTags()) { tracks, tags ->
            val tagMap = tags.filter { it.itemType == "track" }.groupBy({ it.itemId }, { it.tag })
            tracks.map { it.toModel(tags = tagMap[it.videoId].orEmpty()) }
        }

    fun observeSpeedDialPins(): Flow<List<SpeedDialEntry>> =
        cacheDao.observeSpeedDialPins().map { pins -> pins.mapNotNull { it.toEntry() } }

    suspend fun toggleSpeedDialPin(item: HomeItem): Boolean {
        val key = item.speedDialKey()
        val existing = cacheDao.getSpeedDialPin(key)
        return if (existing != null) {
            cacheDao.deleteSpeedDialPin(key)
            false
        } else {
            cacheDao.upsertSpeedDialPin(
                SpeedDialPinEntity(
                    key = key,
                    itemJson = json.encodeToString(item)
                )
            )
            true
        }
    }

    fun observeSpeedDialLocalTracks(): Flow<List<Track>> =
        combine(
            trackDao.observePlayedTracksForSpeedDial(60),
            tagDao.observeAllTags()
        ) { played, tags ->
            val nowMs = System.currentTimeMillis()
            val tagMap = tags.filter { it.itemType == "track" }.groupBy({ it.itemId }, { it.tag })
            val qualified = played.filter { entity ->
                entity.playCount >= 2 || (nowMs - entity.historyTimestampMs) <= 48L * 3_600_000L
            }.ifEmpty { played }
            qualified
                .sortedByDescending { entity ->
                    val daysSince = ((nowMs - entity.historyTimestampMs).coerceAtLeast(0L)) / 86_400_000.0
                    val freqDecay = entity.playCount * 10.0 / (1.0 + daysSince / 14.0)
                    val likeBoost = if (entity.isLiked) 6.0 else 0.0
                    val recentBoost = if (daysSince < 2.0) 8.0 / (1.0 + daysSince) else 0.0
                    freqDecay + likeBoost + recentBoost
                }
                .take(27)
                .map { it.toModel(tags = tagMap[it.videoId].orEmpty()) }
        }

    fun observePlaylists(): Flow<List<PlaylistSummary>> =
        combine(playlistDao.observePlaylists(), tagDao.observeAllTags()) { playlists, tags ->
            val tagMap = tags.filter { it.itemType == "playlist" }.groupBy({ it.itemId }, { it.tag })
            playlists.map { it.toSummary(tags = tagMap[it.id].orEmpty()) }
        }

    fun observeDownloadedPlaylists(): Flow<List<PlaylistSummary>> =
        combine(playlistDao.observeDownloadedPlaylists(), tagDao.observeAllTags()) { playlists, tags ->
            val tagMap = tags.filter { it.itemType == "playlist" }.groupBy({ it.itemId }, { it.tag })
            playlists.map { it.toSummary(tags = tagMap[it.id].orEmpty()) }
        }

    fun observeLibraryAlbums(): Flow<List<AlbumSummary>> =
        combine(albumArtistDao.observeLibraryAlbums(), tagDao.observeAllTags()) { albums, tags ->
            val tagMap = tags.filter { it.itemType == "album" }.groupBy({ it.itemId }, { it.tag })
            albums.map { it.toSummary(tags = tagMap[it.browseId].orEmpty()) }
        }

    fun observeDownloadedAlbums(): Flow<List<AlbumSummary>> =
        combine(
            albumArtistDao.observeDownloadedAlbums(),
            trackDao.observeDownloadedTracks(),
            tagDao.observeAllTags()
        ) { explicitAlbums, dlTracks, tags ->
            val tagMap = tags.filter { it.itemType == "album" }.groupBy({ it.itemId }, { it.tag })
            val result = explicitAlbums.map { it.toSummary(tags = tagMap[it.browseId].orEmpty()) }.toMutableList()
            val seenIds = result.map { it.browseId }.toMutableSet()
            dlTracks.groupBy { it.albumId }.forEach { (albumId, entities) ->
                if (!albumId.isNullOrBlank() && seenIds.add(albumId)) {
                    val sample = entities.first().toModel()
                    result += AlbumSummary(
                        browseId = albumId,
                        playlistId = null,
                        title = sample.album?.title?.takeIf { it.isNotBlank() } ?: "Downloaded Album",
                        artists = sample.artists,
                        isEpOrSingle = entities.size <= 3,
                        thumbnailUrl = entities.firstNotNullOfOrNull { it.thumbnailUrl },
                        tags = tagMap[albumId].orEmpty()
                    )
                }
            }
            result
        }

    fun observeSubscribedArtists(): Flow<List<ArtistSummary>> =
        albumArtistDao.observeSubscribedArtists().map { list -> list.map { it.toSummary() } }

    fun observeDistinctTags(): Flow<List<String>> = tagDao.observeDistinctTagNames()

    fun observeTagsForItem(itemId: String, itemType: String): Flow<List<String>> =
        tagDao.observeTagsForItem(itemId, itemType)

    fun observePlaylistIdsContaining(videoId: String): Flow<Set<String>> =
        playlistDao.observePlaylistIdsContainingTrack(videoId).map { it.toSet() }

    fun observeDownloadMap(): Flow<Map<String, Int>> =
        cacheDao.observeDownloads().map { list -> list.associate { it.videoId to it.state } }

    fun observeSearchHistory(): Flow<List<String>> =
        cacheDao.observeSearchHistory().map { list -> list.map { it.query } }

    private suspend fun mergeUpsertTracks(
        tracks: List<Track>,
        markInLibrary: Boolean? = null,
        markLiked: Boolean? = null,
        markInHistory: Boolean = false,
        historyBaseTimeMs: Long = System.currentTimeMillis()
    ) {
        if (tracks.isEmpty()) return
        val existingById = tracks
            .map { it.videoId }
            .distinct()
            .chunked(500)
            .flatMap { trackDao.getByIds(it) }
            .associateBy { it.videoId }
        val merged = tracks.mapIndexed { idx, t ->
            val existing = existingById[t.videoId]
            val entity = t.toEntity(
                inHistory = markInHistory || (existing?.inHistory == true),
                historyTimestampMs = if (markInHistory) (historyBaseTimeMs - idx * 1000L) else (existing?.historyTimestampMs ?: 0L),
                playCount = existing?.playCount ?: 0
            )
            entity.copy(
                inLibrary = markInLibrary ?: (t.inLibrary || (existing?.inLibrary == true)),
                isLiked = markLiked ?: (t.isLiked || (existing?.isLiked == true)),
                albumId = entity.albumId ?: existing?.albumId,
                albumTitle = entity.albumTitle ?: existing?.albumTitle,
                feedbackTokenAdd = entity.feedbackTokenAdd ?: existing?.feedbackTokenAdd,
                feedbackTokenRemove = entity.feedbackTokenRemove ?: existing?.feedbackTokenRemove
            )
        }
        trackDao.upsertAll(merged)
    }

    suspend fun refreshLibraryPlaylists(): Result<List<PlaylistSummary>> = runCatching {
        val pls = innerTubeApi.getLibraryPlaylists()
        playlistDao.upsertPlaylists(pls.map {
            PlaylistEntity(
                id = it.id,
                browseId = it.browseId,
                title = it.title,
                subtitle = it.subtitle,
                description = null,
                thumbnailUrl = it.thumbnailUrl,
                trackCount = it.trackCount,
                isEditable = it.isEditable,
                inLibrary = true
            )
        })
        if (pls.isNotEmpty()) {
            playlistDao.prunePlaylistsNotIn((pls.map { it.id } + "LM").distinct())
        }
        pls
    }

    suspend fun refreshLibrarySongs(): Result<List<Track>> = runCatching {
        val (firstPage, firstCont) = innerTubeApi.getLibrarySongs()
        val accumulated = firstPage.toMutableList()
        val seenTokens = mutableSetOf<String>()
        var nextToken = firstCont
        var pages = 1
        while (!nextToken.isNullOrBlank() &&
            accumulated.size < MAX_COLLECTION_TRACKS &&
            pages < MAX_CONTINUATION_PAGES &&
            seenTokens.add(nextToken)
        ) {
            val (nextTracks, followingToken) = runCatching { innerTubeApi.getLibrarySongs(nextToken) }
                .getOrNull()
                ?: break
            if (nextTracks.isEmpty()) break
            val remaining = MAX_COLLECTION_TRACKS - accumulated.size
            accumulated += nextTracks.take(remaining)
            pages++
            nextToken = followingToken
        }
        val all = accumulated.distinctBy { it.videoId }.take(MAX_COLLECTION_TRACKS)
        mergeUpsertTracks(all, markInLibrary = true)
        all
    }

    suspend fun refreshLikedSongs(): Result<List<Track>> = runCatching {
        val firstLiked = innerTubeApi.getLikedSongs()
        val accumulated = firstLiked.tracks.take(MAX_COLLECTION_TRACKS).toMutableList()
        val seenTokens = mutableSetOf<String>()
        var nextToken = firstLiked.continuation
        var pages = 1
        while (!nextToken.isNullOrBlank() &&
            accumulated.size < MAX_COLLECTION_TRACKS &&
            pages < MAX_CONTINUATION_PAGES &&
            seenTokens.add(nextToken)
        ) {
            val nextPage = runCatching { innerTubeApi.getLikedSongs(nextToken) }.getOrNull() ?: break
            if (nextPage.tracks.isEmpty()) break
            val remaining = MAX_COLLECTION_TRACKS - accumulated.size
            accumulated += nextPage.tracks.take(remaining)
            pages++
            nextToken = nextPage.continuation
        }
        val allLiked = accumulated.distinctBy { it.videoId }.take(MAX_COLLECTION_TRACKS)
        mergeUpsertTracks(allLiked, markLiked = true)
        playlistDao.upsertPlaylists(
            listOf(
                PlaylistEntity(
                    id = "LM",
                    browseId = "VLLM",
                    title = firstLiked.title,
                    subtitle = "${allLiked.size} tracks",
                    description = firstLiked.description,
                    thumbnailUrl = firstLiked.thumbnailUrl,
                    trackCount = allLiked.size,
                    isEditable = false
                )
            )
        )
        playlistDao.clearPlaylistTracks("LM")
        playlistDao.insertPlaylistTracks(allLiked.mapIndexed { idx, t ->
            PlaylistTrackCrossRef("LM", t.videoId, t.setVideoId, idx)
        })
        allLiked
    }

    suspend fun refreshHistory(): Result<List<Track>> = runCatching {
        val (hist, _) = innerTubeApi.getHistory()
        mergeUpsertTracks(hist, markInHistory = true)
        hist
    }

    suspend fun refreshLibraryAlbums(): Result<List<AlbumSummary>> = runCatching {
        val albums = innerTubeApi.getLibraryAlbums()
        albumArtistDao.upsertAlbums(albums.map { it.toEntity(inLibrary = true) })
        albums
    }

    suspend fun refreshLibraryArtists(): Result<List<ArtistSummary>> = runCatching {
        val artists = innerTubeApi.getLibraryArtists()
        albumArtistDao.upsertArtists(artists.map {
            ArtistEntity(
                browseId = it.browseId,
                name = it.name,
                subscriberCount = it.subscriberCount,
                thumbnailUrl = it.thumbnailUrl,
                isSubscribed = true
            )
        })
        artists
    }

    suspend fun syncAccountAndLibrary(): Result<Unit> = runCatching {
        val session = authStore.currentSession() ?: error("Not signed in")
        coroutineScope {
            val accountJob = async {
                runCatching {
                    val info = innerTubeApi.getAccountInfo()
                    authStore.saveSession(
                        session.copy(
                            accountName = info.name ?: session.accountName,
                            accountHandle = info.handle ?: session.accountHandle,
                            avatarUrl = info.avatarUrl ?: session.avatarUrl
                        )
                    )
                }
            }
            val playlistsJob = async { refreshLibraryPlaylists() }
            val albumsJob = async { refreshLibraryAlbums() }
            val artistsJob = async { refreshLibraryArtists() }
            val songsJob = async { refreshLibrarySongs() }
            val likedJob = async { refreshLikedSongs() }
            val historyJob = async { refreshHistory() }

            val results = listOf(
                accountJob.await(),
                playlistsJob.await(),
                albumsJob.await(),
                artistsJob.await(),
                songsJob.await(),
                likedJob.await(),
                historyJob.await()
            )
            val allFailed = results.drop(1).all { it.isFailure }
            if (allFailed) {
                val firstErr = results.drop(1).firstNotNullOfOrNull { it.exceptionOrNull() }
                if (firstErr != null) throw firstErr
            }
            lastFullSyncAtMs = System.currentTimeMillis()
        }
    }

    suspend fun recordTrackPlayed(track: Track) {
        val existing = trackDao.getById(track.videoId)
        val base = track.toEntity(
            inHistory = true,
            historyTimestampMs = System.currentTimeMillis(),
            playCount = (existing?.playCount ?: 0) + 1
        )
        trackDao.upsert(
            base.copy(
                inLibrary = track.inLibrary || (existing?.inLibrary == true),
                isLiked = track.isLiked || (existing?.isLiked == true),
                albumId = base.albumId ?: existing?.albumId,
                albumTitle = base.albumTitle ?: existing?.albumTitle,
                feedbackTokenAdd = base.feedbackTokenAdd ?: existing?.feedbackTokenAdd,
                feedbackTokenRemove = base.feedbackTokenRemove ?: existing?.feedbackTokenRemove
            )
        )
    }

    suspend fun recordListenSession(track: Track, listenedMs: Long) {
        if (listenedMs <= 0L) return
        val now = System.currentTimeMillis()
        cacheDao.insertPlaySession(
            PlaySessionEntity(
                videoId = track.videoId,
                listenedMs = listenedMs,
                playedAtMs = now
            )
        )
        val existing = trackDao.getById(track.videoId)
        if (existing == null) {
            trackDao.upsert(
                track.toEntity(
                    inHistory = true,
                    historyTimestampMs = now,
                    playCount = 0
                )
            )
        } else {
            trackDao.upsert(
                existing.copy(
                    inHistory = true,
                    historyTimestampMs = maxOf(existing.historyTimestampMs, now)
                )
            )
        }
    }

    suspend fun getTrackCachedState(videoId: String): Track? = trackDao.getById(videoId)?.toModel()

    suspend fun cacheAlbumForOffline(album: AlbumDetail) {
        val existing = albumArtistDao.getAlbum(album.browseId)
        albumArtistDao.upsertAlbums(
            listOf(
                AlbumSummary(
                    browseId = album.browseId,
                    playlistId = album.playlistId,
                    title = album.title,
                    artists = album.artists,
                    year = album.year,
                    isEpOrSingle = album.isEpOrSingle,
                    thumbnailUrl = album.thumbnailUrl,
                    tags = album.tags
                ).toEntity(inLibrary = (existing?.inLibrary == true) || album.inLibrary)
            )
        )
        val normalizedTracks = album.tracks.map { t ->
            if (t.album?.id.isNullOrBlank()) t.copy(album = AlbumRef(id = album.browseId, title = album.title)) else t
        }
        mergeUpsertTracks(normalizedTracks)
    }

    suspend fun cachePlaylistForOffline(playlist: PlaylistDetail) {
        val existing = playlistDao.getPlaylist(playlist.id)
        val isLib = (existing?.inLibrary == true) || playlist.inLibrary || playlist.isEditable || playlist.id == "LM"
        playlistDao.upsertPlaylists(
            listOf(
                PlaylistEntity(
                    id = playlist.id,
                    browseId = playlist.browseId,
                    title = playlist.title,
                    subtitle = playlist.author,
                    description = playlist.description,
                    thumbnailUrl = playlist.thumbnailUrl,
                    trackCount = playlist.tracks.size,
                    isEditable = (existing?.isEditable == true) || playlist.isEditable,
                    inLibrary = isLib
                )
            )
        )
        mergeUpsertTracks(playlist.tracks)
        playlistDao.clearPlaylistTracks(playlist.id)
        playlistDao.insertPlaylistTracks(playlist.tracks.mapIndexed { idx, track ->
            PlaylistTrackCrossRef(playlist.id, track.videoId, track.setVideoId, idx)
        })
    }

    suspend fun getHome(continuation: String? = null): HomePage = innerTubeApi.getHome(continuation)

    suspend fun loadAlbumProgressive(
        browseId: String,
        onUpdate: (AlbumDetail, Boolean) -> Unit = { _, _ -> }
    ): AlbumDetail {
        val cachedAlbum = albumArtistDao.getAlbum(browseId)
        val firstRes = runCatching { innerTubeApi.getAlbum(browseId) }
        val page1 = firstRes.getOrElse { err ->
            val cachedTracks = trackDao.getTracksByAlbumId(browseId).map { it.toModel() }
            val fallback = if (cachedAlbum != null && cachedTracks.isNotEmpty()) {
                val summary = cachedAlbum.toSummary()
                AlbumDetail(
                    browseId = summary.browseId,
                    playlistId = summary.playlistId,
                    title = summary.title,
                    artists = summary.artists,
                    year = summary.year,
                    isEpOrSingle = summary.isEpOrSingle,
                    thumbnailUrl = summary.thumbnailUrl,
                    tracks = cachedTracks,
                    inLibrary = cachedAlbum.inLibrary
                )
            } else if (cachedTracks.isNotEmpty()) {
                val sample = cachedTracks.first()
                AlbumDetail(
                    browseId = browseId,
                    title = sample.album?.title?.takeIf { it.isNotBlank() } ?: "Album",
                    artists = sample.artists,
                    thumbnailUrl = sample.thumbnailUrl,
                    tracks = cachedTracks,
                    inLibrary = false
                )
            } else {
                throw err
            }
            onUpdate(fallback, false)
            return fallback
        }

        val resolvedBase = page1.copy(inLibrary = page1.inLibrary || (cachedAlbum?.inLibrary == true))
        val albumRef = AlbumRef(id = resolvedBase.browseId, title = resolvedBase.title)
        val accumulated = page1.tracks.take(MAX_COLLECTION_TRACKS).toMutableList()
        var nextToken = page1.continuation
        val seenTokens = mutableSetOf<String>()
        var pages = 1
        val mayFallbackToPlaylist = accumulated.size >= 100 &&
            nextToken.isNullOrBlank() &&
            !resolvedBase.playlistId.isNullOrBlank()

        onUpdate(
            resolvedBase.copy(tracks = accumulated.toList(), continuation = nextToken),
            (!nextToken.isNullOrBlank() || mayFallbackToPlaylist) && accumulated.size < MAX_COLLECTION_TRACKS
        )

        while (!nextToken.isNullOrBlank() &&
            accumulated.size < MAX_COLLECTION_TRACKS &&
            pages < MAX_CONTINUATION_PAGES &&
            seenTokens.add(nextToken)
        ) {
            val nextPage = runCatching {
                innerTubeApi.getAlbum(
                    browseId = browseId,
                    continuation = nextToken,
                    fallbackAlbum = albumRef,
                    fallbackArtists = resolvedBase.artists,
                    fallbackThumb = resolvedBase.thumbnailUrl
                )
            }.getOrNull() ?: break
            if (nextPage.tracks.isEmpty()) break
            val remaining = MAX_COLLECTION_TRACKS - accumulated.size
            accumulated += nextPage.tracks.take(remaining)
            pages++
            nextToken = nextPage.continuation?.takeIf { it.isNotBlank() }
            onUpdate(
                resolvedBase.copy(tracks = accumulated.toList(), continuation = nextToken),
                !nextToken.isNullOrBlank() && accumulated.size < MAX_COLLECTION_TRACKS && pages < MAX_CONTINUATION_PAGES
            )
        }

        // If an unusually large album returned 100 tracks on browse without a shelf continuation,
        // try paginating its underlying OLAK playlistId cleanly up to MAX_COLLECTION_TRACKS.
        if (accumulated.size == 100 && nextToken.isNullOrBlank() && !resolvedBase.playlistId.isNullOrBlank()) {
            val olakId = resolvedBase.playlistId!!
            val olakBrowse = if (olakId.startsWith("VL")) olakId else "VL$olakId"
            val olakPage1 = runCatching { innerTubeApi.getPlaylist(olakBrowse, null) }.getOrNull()
            if (olakPage1 != null) {
                val olakTracks = olakPage1.tracks.take(MAX_COLLECTION_TRACKS).toMutableList()
                var olakToken = olakPage1.continuation
                val olakSeen = mutableSetOf<String>()
                var olakPages = 1
                while (!olakToken.isNullOrBlank() &&
                    olakTracks.size < MAX_COLLECTION_TRACKS &&
                    olakPages < MAX_CONTINUATION_PAGES &&
                    olakSeen.add(olakToken)
                ) {
                    val nextOlak = runCatching { innerTubeApi.getPlaylist(olakBrowse, olakToken) }.getOrNull() ?: break
                    if (nextOlak.tracks.isEmpty()) break
                    val remaining = MAX_COLLECTION_TRACKS - olakTracks.size
                    olakTracks += nextOlak.tracks.take(remaining)
                    olakPages++
                    olakToken = nextOlak.continuation?.takeIf { it.isNotBlank() }
                }
                if (olakTracks.size > accumulated.size) {
                    accumulated.clear()
                    accumulated += olakTracks.map { t ->
                        t.copy(
                            album = t.album ?: albumRef,
                            artists = t.artists.ifEmpty { resolvedBase.artists },
                            thumbnailUrl = t.thumbnailUrl ?: resolvedBase.thumbnailUrl
                        )
                    }
                }
            }
        }

        val finalDetail = resolvedBase.copy(
            tracks = accumulated.toList(),
            continuation = null
        )
        onUpdate(finalDetail, false)
        cacheAlbumForOffline(finalDetail)
        return finalDetail
    }

    suspend fun getAlbum(browseId: String): AlbumDetail = loadAlbumProgressive(browseId)

    suspend fun getArtist(browseId: String): ArtistDetail = innerTubeApi.getArtist(browseId)

    suspend fun loadPlaylistProgressive(
        browseId: String,
        onUpdate: (PlaylistDetail, Boolean) -> Unit = { _, _ -> }
    ): PlaylistDetail {
        val normalizedId = browseId.removePrefix("VL")
        val existing = playlistDao.getPlaylist(normalizedId)
        val firstRes = runCatching { innerTubeApi.getPlaylist(browseId, null) }
        val page1 = firstRes.getOrElse { err ->
            val cachedTracks = playlistDao.getPlaylistTracksSnapshot(normalizedId)
            if (existing != null && cachedTracks.isNotEmpty()) {
                val crossRefs = playlistDao.getPlaylistCrossRefs(normalizedId)
                val setIdByVideo = crossRefs.associate { it.videoId to it.setVideoId }
                val cachedDetail = PlaylistDetail(
                    id = existing.id,
                    browseId = existing.browseId,
                    title = existing.title,
                    description = existing.description,
                    author = existing.subtitle,
                    thumbnailUrl = existing.thumbnailUrl,
                    tracks = cachedTracks.mapIndexed { idx, entity ->
                        val refSetId = crossRefs.getOrNull(idx)
                            ?.takeIf { it.videoId == entity.videoId }
                            ?.setVideoId
                            ?: setIdByVideo[entity.videoId]
                        entity.toModel(setVideoId = refSetId)
                    },
                    isEditable = existing.isEditable,
                    inLibrary = existing.inLibrary
                )
                onUpdate(cachedDetail, false)
                return cachedDetail
            }
            throw err
        }

        val resolvedInLibrary = page1.inLibrary || (existing?.inLibrary == true) || page1.isEditable || page1.id == "LM"
        val resolvedEditable = (page1.isEditable || (existing?.isEditable == true)) && page1.id != "LM"
        val baseResolved = page1.copy(
            isEditable = resolvedEditable,
            inLibrary = resolvedInLibrary
        )

        val accumulated = page1.tracks.take(MAX_COLLECTION_TRACKS).toMutableList()
        var nextToken = page1.continuation
        val seenTokens = mutableSetOf<String>()
        var pages = 1

        onUpdate(
            baseResolved.copy(tracks = accumulated.toList(), continuation = nextToken),
            !nextToken.isNullOrBlank() && accumulated.size < MAX_COLLECTION_TRACKS
        )

        while (!nextToken.isNullOrBlank() &&
            accumulated.size < MAX_COLLECTION_TRACKS &&
            pages < MAX_CONTINUATION_PAGES &&
            seenTokens.add(nextToken)
        ) {
            val nextPage = runCatching { innerTubeApi.getPlaylist(browseId, nextToken) }.getOrNull() ?: break
            if (nextPage.tracks.isEmpty()) break
            val remaining = MAX_COLLECTION_TRACKS - accumulated.size
            accumulated += nextPage.tracks.take(remaining)
            pages++
            nextToken = nextPage.continuation?.takeIf { it.isNotBlank() }
            onUpdate(
                baseResolved.copy(tracks = accumulated.toList(), continuation = nextToken),
                !nextToken.isNullOrBlank() && accumulated.size < MAX_COLLECTION_TRACKS && pages < MAX_CONTINUATION_PAGES
            )
        }

        val finalDetail = baseResolved.copy(
            tracks = accumulated.toList(),
            continuation = null
        )
        onUpdate(finalDetail, false)

        if (resolvedInLibrary) {
            playlistDao.upsertPlaylists(
                listOf(
                    PlaylistEntity(
                        id = finalDetail.id,
                        browseId = finalDetail.browseId,
                        title = finalDetail.title,
                        subtitle = finalDetail.author,
                        description = finalDetail.description,
                        thumbnailUrl = finalDetail.thumbnailUrl,
                        trackCount = finalDetail.tracks.size,
                        isEditable = finalDetail.isEditable,
                        inLibrary = true
                    )
                )
            )
            mergeUpsertTracks(finalDetail.tracks)
            playlistDao.clearPlaylistTracks(finalDetail.id)
            playlistDao.insertPlaylistTracks(finalDetail.tracks.mapIndexed { idx, track ->
                PlaylistTrackCrossRef(finalDetail.id, track.videoId, track.setVideoId, idx)
            })
        }
        return finalDetail
    }

    suspend fun getPlaylist(browseId: String, continuation: String? = null): PlaylistDetail {
        if (continuation != null) {
            return innerTubeApi.getPlaylist(browseId, continuation)
        }
        return loadPlaylistProgressive(browseId)
    }

    suspend fun togglePlaylistLibrary(playlist: PlaylistDetail): Result<Boolean> = runCatching {
        val target = !playlist.inLibrary
        innerTubeApi.saveAlbumToLibrary(playlist.id, target)
        if (target) {
            playlistDao.upsertPlaylists(
                listOf(
                    PlaylistEntity(
                        id = playlist.id,
                        browseId = playlist.browseId,
                        title = playlist.title,
                        subtitle = playlist.author,
                        description = playlist.description,
                        thumbnailUrl = playlist.thumbnailUrl,
                        trackCount = playlist.tracks.size,
                        isEditable = playlist.isEditable
                    )
                )
            )
        } else if (!playlist.isEditable && playlist.id != "LM") {
            playlistDao.deletePlaylist(playlist.id)
        }
        target
    }

    suspend fun clonePlaylistToOwned(playlist: PlaylistDetail): Result<String> = runCatching {
        val newId = createPlaylist(
            title = playlist.title,
            description = playlist.description.orEmpty(),
            privacy = "PRIVATE"
        ).getOrThrow()
        playlist.tracks.forEach { track ->
            addTrackToPlaylist(newId, track)
        }
        newId
    }

    suspend fun search(query: String, filter: SearchFilter, continuation: String? = null): SearchResultPage {
        if (continuation == null && query.isNotBlank()) {
            cacheDao.insertSearchQuery(SearchHistoryEntity(query = query.trim()))
        }
        return innerTubeApi.search(query, filter, continuation)
    }

    suspend fun getSearchSuggestions(query: String): List<String> = innerTubeApi.getSearchSuggestions(query)

    suspend fun getNext(
        videoId: String? = null,
        playlistId: String? = null,
        params: String? = null,
        continuation: String? = null
    ): NextPage = innerTubeApi.getNext(videoId, playlistId, params, continuation)

    suspend fun getRelated(browseId: String): HomePage = innerTubeApi.getRelated(browseId)

    /**
     * Resolves an album browseId even when a track came from a stripped endpoint lacking albumId.
     */
    suspend fun resolveAlbumId(track: Track): String? {
        if (!track.album?.id.isNullOrBlank()) return track.album?.id
        val cached = trackDao.getById(track.videoId)
        if (!cached?.albumId.isNullOrBlank()) return cached?.albumId

        val next = runCatching { innerTubeApi.getNext(videoId = track.videoId) }.getOrNull()
        val resolved = next?.currentTrackMetadata?.album
        if (!resolved?.id.isNullOrBlank()) {
            trackDao.updateAlbumRef(track.videoId, resolved!!.id!!, resolved.title)
            return resolved.id
        }
        return null
    }

    suspend fun toggleLike(track: Track): Result<Boolean> = runCatching {
        val targetLike = !track.isLiked
        trackDao.upsert(track.copy(isLiked = targetLike).toEntity())
        runCatching {
            innerTubeApi.likeSong(track.videoId, targetLike)
        }.onFailure {
            trackDao.updateLiked(track.videoId, track.isLiked)
        }.getOrThrow()
        targetLike
    }

    /**
     * Adds or removes a track from the user's YouTube Music Library (distinct from Liked Songs & Playlists).
     */
    suspend fun toggleLibrary(track: Track): Result<Boolean> = runCatching {
        val existingDb = trackDao.getById(track.videoId)
        val targetInLib = !track.inLibrary
        var resolvedTrack = track.copy(
            album = track.album ?: existingDb?.let {
                if (!it.albumId.isNullOrBlank() || !it.albumTitle.isNullOrBlank()) AlbumRef(it.albumId, it.albumTitle.orEmpty()) else null
            },
            feedbackTokenAdd = track.feedbackTokenAdd ?: existingDb?.feedbackTokenAdd,
            feedbackTokenRemove = track.feedbackTokenRemove ?: existingDb?.feedbackTokenRemove
        )

        fun neededToken(t: Track): String? = if (targetInLib) t.feedbackTokenAdd else t.feedbackTokenRemove
        var remoteConfirmedInLib: Boolean? = null

        fun mergeMeta(meta: Track?) {
            if (meta == null) return
            if (meta.feedbackTokenAdd != null || meta.feedbackTokenRemove != null) {
                remoteConfirmedInLib = meta.inLibrary
            }
            resolvedTrack = resolvedTrack.copy(
                album = resolvedTrack.album ?: meta.album,
                feedbackTokenAdd = meta.feedbackTokenAdd ?: resolvedTrack.feedbackTokenAdd,
                feedbackTokenRemove = meta.feedbackTokenRemove ?: resolvedTrack.feedbackTokenRemove
            )
        }

        if (neededToken(resolvedTrack) == null) {
            val next = runCatching {
                innerTubeApi.getNext(videoId = track.videoId, playlistId = "RDAMVM${track.videoId}")
            }.getOrNull()
            val meta = next?.currentTrackMetadata?.takeIf { it.videoId == track.videoId }
                ?: next?.tracks?.firstOrNull { it.videoId == track.videoId }
            mergeMeta(meta)
        }

        if (neededToken(resolvedTrack) == null && remoteConfirmedInLib != targetInLib && !resolvedTrack.album?.id.isNullOrBlank()) {
            val albumMeta = runCatching { innerTubeApi.getAlbum(resolvedTrack.album!!.id!!) }
                .getOrNull()?.tracks?.firstOrNull { it.videoId == track.videoId }
            mergeMeta(albumMeta)
        }

        if (neededToken(resolvedTrack) == null && remoteConfirmedInLib != targetInLib) {
            val query = "${resolvedTrack.title} ${resolvedTrack.artistDisplay}".trim()
            if (query.isNotBlank()) {
                val searchMeta = runCatching { innerTubeApi.search(query, SearchFilter.SONGS) }
                    .getOrNull()?.tracks?.firstOrNull { it.videoId == track.videoId }
                mergeMeta(searchMeta)
            }
        }

        val entityBase = resolvedTrack.toEntity(
            inHistory = existingDb?.inHistory == true,
            historyTimestampMs = existingDb?.historyTimestampMs ?: 0L,
            playCount = existingDb?.playCount ?: 0
        ).copy(
            inLibrary = targetInLib,
            isLiked = resolvedTrack.isLiked || (existingDb?.isLiked == true)
        )

        if (remoteConfirmedInLib == targetInLib) {
            trackDao.upsert(entityBase)
            return@runCatching targetInLib
        }

        val token = neededToken(resolvedTrack)
        if (token != null) {
            trackDao.upsert(entityBase)
            runCatching {
                innerTubeApi.sendFeedback(listOf(token))
            }.onFailure {
                trackDao.updateLibraryState(resolvedTrack.videoId, !targetInLib)
            }.getOrThrow()
            return@runCatching targetInLib
        }

        // Local-first library save fallback when YouTube omits library tokens (e.g., non-ATV tracks)
        trackDao.upsert(entityBase)
        targetInLib
    }

    suspend fun createPlaylist(title: String, description: String = "", privacy: String = "PRIVATE"): Result<String> =
        runCatching {
            val createdId = innerTubeApi.createPlaylist(title, description, privacy)
                ?: throw IllegalStateException("Playlist creation failed")
            playlistDao.upsertPlaylists(
                listOf(
                    PlaylistEntity(
                        id = createdId.removePrefix("VL"),
                        browseId = if (createdId.startsWith("VL")) createdId else "VL$createdId",
                        title = title,
                        subtitle = "You",
                        description = description,
                        thumbnailUrl = null,
                        trackCount = 0,
                        isEditable = true
                    )
                )
            )
            createdId.removePrefix("VL")
        }

    suspend fun updatePlaylist(playlistId: String, title: String, description: String): Result<Unit> = runCatching {
        innerTubeApi.updatePlaylistMetadata(playlistId, title, description)
        playlistDao.getPlaylist(playlistId.removePrefix("VL"))?.let { existing ->
            playlistDao.upsertPlaylists(listOf(existing.copy(title = title, description = description)))
        }
    }

    suspend fun deletePlaylist(playlistId: String): Result<Unit> = runCatching {
        innerTubeApi.deletePlaylist(playlistId)
        playlistDao.deletePlaylist(playlistId.removePrefix("VL"))
    }

    suspend fun addTrackToPlaylist(playlistId: String, track: Track): Result<Unit> = runCatching {
        val normalizedId = playlistId.removePrefix("VL")
        innerTubeApi.addTrackToPlaylist(normalizedId, track.videoId)
        trackDao.upsert(track.toEntity())
        val currentRefs = playlistDao.getPlaylistCrossRefs(normalizedId)
        playlistDao.insertPlaylistTracks(
            listOf(
                PlaylistTrackCrossRef(
                    playlistId = normalizedId,
                    videoId = track.videoId,
                    setVideoId = null,
                    position = currentRefs.size
                )
            )
        )
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, track: Track): Result<Unit> = runCatching {
        val normalizedId = playlistId.removePrefix("VL")
        var setVideoId = track.setVideoId
        if (setVideoId.isNullOrBlank()) {
            val refs = playlistDao.getPlaylistCrossRefs(normalizedId)
            setVideoId = refs.firstOrNull { it.videoId == track.videoId }?.setVideoId
        }
        if (setVideoId.isNullOrBlank()) {
            val fresh = getPlaylist("VL$normalizedId")
            setVideoId = fresh.tracks.firstOrNull { it.videoId == track.videoId }?.setVideoId
        }
        val finalSetId = setVideoId
            ?: throw IllegalStateException("Missing setVideoId to remove track from playlist")
        innerTubeApi.removeTrackFromPlaylist(normalizedId, track.videoId, finalSetId)
        playlistDao.removeTrackFromPlaylistLocal(normalizedId, track.videoId)
    }

    suspend fun moveTrackInPlaylist(
        playlistId: String,
        setVideoId: String,
        successorSetVideoId: String?
    ): Result<Unit> = runCatching {
        innerTubeApi.moveTrackInPlaylist(playlistId.removePrefix("VL"), setVideoId, successorSetVideoId)
    }

    suspend fun toggleArtistSubscription(artist: ArtistDetail): Result<Boolean> = runCatching {
        val target = !artist.isSubscribed
        innerTubeApi.subscribeArtist(artist.browseId, target)
        albumArtistDao.upsertArtists(
            listOf(
                ArtistEntity(
                    browseId = artist.browseId,
                    name = artist.name,
                    subscriberCount = artist.subscriberCount,
                    thumbnailUrl = artist.thumbnailUrl,
                    isSubscribed = target
                )
            )
        )
        target
    }

    suspend fun toggleAlbumLibrary(album: AlbumDetail): Result<Boolean> = runCatching {
        val target = !album.inLibrary
        val plId = album.playlistId ?: throw IllegalStateException("Album missing playlistId for library save")
        innerTubeApi.saveAlbumToLibrary(plId, target)
        albumArtistDao.upsertAlbums(
            listOf(
                AlbumSummary(
                    browseId = album.browseId,
                    playlistId = album.playlistId,
                    title = album.title,
                    artists = album.artists,
                    year = album.year,
                    isEpOrSingle = album.isEpOrSingle,
                    thumbnailUrl = album.thumbnailUrl
                ).toEntity(inLibrary = target)
            )
        )
        target
    }

    suspend fun getLyrics(track: Track): LyricsResult {
        cacheDao.getLyrics(track.videoId)?.toModel()?.let { cached ->
            if (cached.syncedLines.isNotEmpty() || !cached.plainText.isNullOrBlank()) {
                return cached
            }
        }
        val fetched = lyricsRepository.fetchLyrics(
            videoId = track.videoId,
            title = track.title,
            artist = track.artistDisplay,
            album = track.album?.title,
            durationMs = track.durationMs
        )
        if (fetched.syncedLines.isNotEmpty() || !fetched.plainText.isNullOrBlank()) {
            cacheDao.putLyrics(LyricsCacheEntity(track.videoId, json.encodeToString(fetched)))
        }
        return fetched
    }

    suspend fun addTag(itemId: String, itemType: String, rawTag: String) {
        val normalized = rawTag.trim().lowercase().removePrefix("#")
        if (normalized.isNotBlank()) {
            tagDao.insertTag(ItemTagEntity(itemId = itemId, itemType = itemType, tag = normalized))
        }
    }

    suspend fun removeTag(itemId: String, itemType: String, tag: String) {
        tagDao.deleteTag(itemId = itemId, itemType = itemType, tag = tag)
    }

    suspend fun deleteSearchQuery(query: String) {
        cacheDao.deleteSearchQuery(query)
    }

    suspend fun exportBackupJson(): String {
        val prefs = preferencesStore.current()
        val tags = tagDao.getAllTagsSnapshot().map { ItemTagRecord(it.itemId, it.itemType, it.tag) }
        return json.encodeToString(LinBackupPayload(preferences = prefs, itemTags = tags))
    }

    suspend fun importBackupJson(rawJson: String): Result<Unit> = runCatching {
        val payload = json.decodeFromString<LinBackupPayload>(rawJson)
        preferencesStore.update { payload.preferences }
        tagDao.insertTags(payload.itemTags.map { ItemTagEntity(it.itemId, it.itemType, it.tag) })
    }

    companion object {
        const val MAX_COLLECTION_TRACKS = 5_000
        const val MAX_CONTINUATION_PAGES = 60
    }
}
