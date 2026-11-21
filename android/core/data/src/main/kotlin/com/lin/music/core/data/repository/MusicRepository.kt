package com.lin.music.core.data.repository

import com.lin.music.core.data.database.AlbumArtistDao
import com.lin.music.core.data.database.ArtistEntity
import com.lin.music.core.data.database.CacheDao
import com.lin.music.core.data.database.DownloadEntity
import com.lin.music.core.data.database.ItemTagEntity
import com.lin.music.core.data.database.LyricsCacheEntity
import com.lin.music.core.data.database.PlaylistDao
import com.lin.music.core.data.database.PlaylistEntity
import com.lin.music.core.data.database.PlaylistTrackCrossRef
import com.lin.music.core.data.database.SearchHistoryEntity
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
import com.lin.music.core.model.HomePage
import com.lin.music.core.model.ItemTagRecord
import com.lin.music.core.model.LinBackupPayload
import com.lin.music.core.model.LyricsResult
import com.lin.music.core.model.NextPage
import com.lin.music.core.model.PlaylistDetail
import com.lin.music.core.model.PlaylistSummary
import com.lin.music.core.model.SearchFilter
import com.lin.music.core.model.SearchResultPage
import com.lin.music.core.model.Track
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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

    fun observeSpeedDialLocalTracks(): Flow<List<Track>> =
        combine(
            trackDao.observeRecentLocalPlays(16),
            trackDao.observeFrequentLocalPlays(16),
            tagDao.observeAllTags()
        ) { recent, frequent, tags ->
            val tagMap = tags.filter { it.itemType == "track" }.groupBy({ it.itemId }, { it.tag })
            val merged = (recent.take(4) + frequent + recent).distinctBy { it.videoId }.take(16)
            merged.map { it.toModel(tags = tagMap[it.videoId].orEmpty()) }
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
        val (firstPage, cont) = innerTubeApi.getLibrarySongs()
        val secondPage = if (!cont.isNullOrBlank()) {
            runCatching { innerTubeApi.getLibrarySongs(cont).first }.getOrDefault(emptyList())
        } else emptyList()
        val all = (firstPage + secondPage).distinctBy { it.videoId }
        mergeUpsertTracks(all, markInLibrary = true)
        all
    }

    suspend fun refreshLikedSongs(): Result<List<Track>> = runCatching {
        val liked = innerTubeApi.getLikedSongs()
        mergeUpsertTracks(liked.tracks, markLiked = true)
        playlistDao.upsertPlaylists(
            listOf(
                PlaylistEntity(
                    id = "LM",
                    browseId = "VLLM",
                    title = liked.title,
                    subtitle = "${liked.tracks.size} tracks",
                    description = liked.description,
                    thumbnailUrl = liked.thumbnailUrl,
                    trackCount = liked.tracks.size,
                    isEditable = false
                )
            )
        )
        playlistDao.clearPlaylistTracks("LM")
        playlistDao.insertPlaylistTracks(liked.tracks.mapIndexed { idx, t ->
            PlaylistTrackCrossRef("LM", t.videoId, t.setVideoId, idx)
        })
        liked.tracks
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

    suspend fun getAlbum(browseId: String): AlbumDetail {
        val cachedAlbum = albumArtistDao.getAlbum(browseId)
        return runCatching {
            val detail = innerTubeApi.getAlbum(browseId)
            cacheAlbumForOffline(detail)
            detail.copy(inLibrary = detail.inLibrary || (cachedAlbum?.inLibrary == true))
        }.getOrElse { err ->
            val cachedTracks = trackDao.getTracksByAlbumId(browseId).map { it.toModel() }
            if (cachedAlbum != null && cachedTracks.isNotEmpty()) {
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
        }
    }

    suspend fun getArtist(browseId: String): ArtistDetail = innerTubeApi.getArtist(browseId)

    suspend fun getPlaylist(browseId: String, continuation: String? = null): PlaylistDetail {
        if (continuation != null) {
            return innerTubeApi.getPlaylist(browseId, continuation)
        }
        val normalizedId = browseId.removePrefix("VL")
        val existing = playlistDao.getPlaylist(normalizedId)
        return runCatching {
            val detail = innerTubeApi.getPlaylist(browseId, null)
            val resolvedInLibrary = detail.inLibrary || (existing?.inLibrary == true) || detail.isEditable || detail.id == "LM"
            val resolvedEditable = (detail.isEditable || (existing?.isEditable == true)) && detail.id != "LM"
            val resolved = detail.copy(
                isEditable = resolvedEditable,
                inLibrary = resolvedInLibrary
            )
            if (resolvedInLibrary) {
                playlistDao.upsertPlaylists(
                    listOf(
                        PlaylistEntity(
                            id = resolved.id,
                            browseId = resolved.browseId,
                            title = resolved.title,
                            subtitle = resolved.author,
                            description = resolved.description,
                            thumbnailUrl = resolved.thumbnailUrl,
                            trackCount = resolved.tracks.size,
                            isEditable = resolved.isEditable,
                            inLibrary = true
                        )
                    )
                )
                mergeUpsertTracks(resolved.tracks)
                playlistDao.clearPlaylistTracks(resolved.id)
                playlistDao.insertPlaylistTracks(resolved.tracks.mapIndexed { idx, track ->
                    PlaylistTrackCrossRef(resolved.id, track.videoId, track.setVideoId, idx)
                })
            }
            resolved
        }.getOrElse { err ->
            val cachedTracks = playlistDao.getPlaylistTracksSnapshot(normalizedId)
            if (existing != null && cachedTracks.isNotEmpty()) {
                val crossRefs = playlistDao.getPlaylistCrossRefs(normalizedId).associate { it.videoId to it.setVideoId }
                PlaylistDetail(
                    id = existing.id,
                    browseId = existing.browseId,
                    title = existing.title,
                    description = existing.description,
                    author = existing.subtitle,
                    thumbnailUrl = existing.thumbnailUrl,
                    tracks = cachedTracks.map { it.toModel(setVideoId = crossRefs[it.videoId]) },
                    isEditable = existing.isEditable,
                    inLibrary = existing.inLibrary
                )
            } else {
                throw err
            }
        }
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
            val fresh = innerTubeApi.getPlaylist("VL$normalizedId")
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
}
