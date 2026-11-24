package com.lin.music.core.innertube.parser

import com.lin.music.core.model.AlbumDetail
import com.lin.music.core.model.AlbumRef
import com.lin.music.core.model.AlbumSummary
import com.lin.music.core.model.ArtistDetail
import com.lin.music.core.model.ArtistRef
import com.lin.music.core.model.ArtistSummary
import com.lin.music.core.model.HomeItem
import com.lin.music.core.model.HomePage
import com.lin.music.core.model.HomeSection
import com.lin.music.core.model.MixChip
import com.lin.music.core.model.NextPage
import com.lin.music.core.model.PlaylistDetail
import com.lin.music.core.model.PlaylistSummary
import com.lin.music.core.model.SearchResultPage
import com.lin.music.core.model.Track
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object InnerTubeParsers {

    data class ParsedAccountInfo(
        val name: String?,
        val handle: String?,
        val avatarUrl: String?
    )

    fun parseAccountInfo(root: JsonObject): ParsedAccountInfo {
        val renderer = root.findFirstObject("activeAccountHeaderRenderer") ?: return ParsedAccountInfo(null, null, null)
        val name = renderer.obj("accountName")?.extractText()
        val handle = renderer.obj("channelHandle")?.extractText()
        val avatar = renderer.obj("accountPhoto")?.extractThumbnailUrl()
        return ParsedAccountInfo(name, handle, avatar)
    }

    fun parseHomeResponse(root: JsonObject): HomePage {
        val sectionContents = root.findFirstArray("sectionListRenderer", "contents")
            ?: root.findFirstArray("continuationContents", "sectionListContinuation", "contents")
            ?: root.findFirstArray("appendContinuationItemsAction", "continuationItems")
            ?: JsonArray(emptyList())

        val unwrapped = sectionContents.flatMap { elem ->
            elem.asObj()?.obj("itemSectionRenderer")?.arr("contents")?.toList() ?: listOf(elem)
        }

        val sections = unwrapped.mapNotNull { elem ->
            val obj = elem.asObj() ?: return@mapNotNull null
            obj.obj("musicCarouselShelfRenderer")?.let { parseCarouselSection(it) }
                ?: obj.obj("musicImmersiveCarouselShelfRenderer")?.let { parseCarouselSection(it) }
                ?: obj.obj("gridRenderer")?.let { parseGridSection(it) }
                ?: obj.obj("musicShelfRenderer")?.let { parseMusicShelfAsHomeSection(it) }
        }

        val continuation = root.extractContinuationToken()
        return HomePage(sections = sections, continuation = continuation)
    }

    private fun parseCarouselSection(carousel: JsonObject): HomeSection? {
        val header = carousel.obj("header")
            ?.obj("musicCarouselShelfBasicHeaderRenderer")
        val title = header?.obj("title")?.extractText()
            ?: header?.obj("strapline")?.extractText()
            ?: return null
        val contents = carousel.arr("contents") ?: return null
        val items = contents.mapNotNull { itemElem ->
            val obj = itemElem.asObj() ?: return@mapNotNull null
            obj.obj("musicResponsiveListItemRenderer")?.let { r ->
                parseTrackFromListItem(r)?.let { HomeItem.SongItem(it) }
            } ?: obj.obj("musicTwoRowItemRenderer")?.let { r ->
                parseTwoRowHomeItem(r)
            }
        }
        if (items.isEmpty()) return null
        return HomeSection(
            title = title,
            items = items,
            isQuickPicks = title.contains("quick picks", ignoreCase = true) ||
                items.all { it is HomeItem.SongItem }
        )
    }

    private fun parseMusicShelfAsHomeSection(shelf: JsonObject): HomeSection? {
        val title = shelf.obj("title")?.extractText() ?: "Library"
        val contents = shelf.arr("contents") ?: return null
        val items = contents.mapNotNull { elem ->
            elem.asObj()?.obj("musicResponsiveListItemRenderer")?.let { r ->
                parseTrackFromListItem(r)?.let { HomeItem.SongItem(it) }
            }
        }
        if (items.isEmpty()) return null
        return HomeSection(title = title, items = items, isQuickPicks = true)
    }

    private fun parseGridSection(grid: JsonObject): HomeSection? {
        val title = grid.obj("header")?.obj("gridHeaderRenderer")?.obj("title")?.extractText() ?: "Speed dial"
        val items = grid.arr("items").orEmpty().mapNotNull { elem ->
            val obj = elem.asObj() ?: return@mapNotNull null
            obj.obj("musicTwoRowItemRenderer")?.let { parseTwoRowHomeItem(it) }
                ?: obj.obj("musicResponsiveListItemRenderer")?.let { r ->
                    parseTrackFromListItem(r)?.let { HomeItem.SongItem(it) }
                }
        }
        if (items.isEmpty()) return null
        return HomeSection(title = title, items = items, isQuickPicks = false)
    }

    private fun parseTwoRowHomeItem(renderer: JsonObject): HomeItem? {
        val navEndpoint = renderer.obj("navigationEndpoint")
        val browseEndpoint = navEndpoint?.obj("browseEndpoint")
        val watchEndpoint = navEndpoint?.obj("watchEndpoint")
        val watchPlaylistEndpoint = navEndpoint?.obj("watchPlaylistEndpoint")
        val title = renderer.obj("title")?.extractText() ?: return null
        val subtitleRuns = renderer.obj("subtitle")?.arr("runs")
        val subtitle = renderer.obj("subtitle")?.extractText()
        val thumb = renderer.obj("thumbnailRenderer")
            ?.obj("musicThumbnailRenderer")
            ?.obj("thumbnail")
            ?.extractThumbnailUrl()

        val watchVideoId = watchEndpoint?.str("videoId")?.takeIf { it.isNotBlank() }
        if (watchVideoId != null) {
            val artists = subtitleRuns.extractArtistsFromRuns()
            val (addToken, removeToken, inLib) = extractLibraryTokens(renderer)
            val isLiked = extractLikeState(renderer)
            return HomeItem.SongItem(
                Track(
                    videoId = watchVideoId,
                    title = title,
                    artists = artists,
                    thumbnailUrl = thumb,
                    inLibrary = inLib,
                    isLiked = isLiked,
                    feedbackTokenAdd = addToken,
                    feedbackTokenRemove = removeToken
                )
            )
        }

        val overlayPlayNav = renderer.obj("thumbnailOverlay")
            ?.obj("musicItemThumbnailOverlayRenderer")
            ?.obj("content")
            ?.obj("musicPlayButtonRenderer")
            ?.obj("playNavigationEndpoint")
        val watchPlaylistId = watchPlaylistEndpoint?.str("playlistId")?.takeIf { it.isNotBlank() }
            ?: watchEndpoint?.str("playlistId")?.takeIf { it.isNotBlank() }
            ?: overlayPlayNav?.obj("watchPlaylistEndpoint")?.str("playlistId")?.takeIf { it.isNotBlank() }
            ?: overlayPlayNav?.obj("watchEndpoint")?.str("playlistId")?.takeIf { it.isNotBlank() }
        if (browseEndpoint == null && watchPlaylistId != null) {
            val cleanId = watchPlaylistId.removePrefix("VL")
            return HomeItem.PlaylistItem(
                PlaylistSummary(
                    id = cleanId,
                    browseId = if (watchPlaylistId.startsWith("VL")) watchPlaylistId else "VL$cleanId",
                    title = title,
                    subtitle = subtitle,
                    thumbnailUrl = thumb
                )
            )
        }

        val browseId = browseEndpoint?.str("browseId") ?: return null
        val pageType = browseEndpoint.obj("browseEndpointContextSupportedConfigs")
            ?.obj("browseEndpointContextMusicConfig")
            ?.str("pageType")

        return when {
            pageType == "MUSIC_PAGE_TYPE_ARTIST" || browseId.startsWith("UC") -> {
                HomeItem.ArtistItem(
                    ArtistSummary(
                        browseId = browseId,
                        name = title,
                        subscriberCount = subtitle,
                        thumbnailUrl = thumb
                    )
                )
            }
            pageType == "MUSIC_PAGE_TYPE_ALBUM" || browseId.startsWith("MPREb_") -> {
                val artists = subtitleRuns.extractArtistsFromRuns()
                val year = subtitleRuns?.lastOrNull()?.asObj()?.str("text")
                    ?.takeIf { it.length == 4 && it.all(Char::isDigit) }
                val isEp = subtitle?.contains("EP", ignoreCase = true) == true ||
                    subtitle?.contains("Single", ignoreCase = true) == true
                HomeItem.AlbumItem(
                    AlbumSummary(
                        browseId = browseId,
                        title = title,
                        artists = artists,
                        year = year,
                        isEpOrSingle = isEp,
                        thumbnailUrl = thumb
                    )
                )
            }
            else -> {
                HomeItem.PlaylistItem(
                    PlaylistSummary(
                        id = browseId.removePrefix("VL"),
                        browseId = browseId,
                        title = title,
                        subtitle = subtitle,
                        thumbnailUrl = thumb
                    )
                )
            }
        }
    }

    fun parseTracksFromBrowseShelf(root: JsonObject): Pair<List<Track>, String?> {
        val container = root.extractTrackShelfContainer() ?: root
        val tracks = mutableListOf<Track>()
        val items = container.findAllObjects("musicResponsiveListItemRenderer")
        for (item in items) {
            parseTrackFromListItem(item)?.let { tracks += it }
        }
        return tracks to container.extractShelfContinuationToken()
    }

    fun parseLibraryPlaylists(root: JsonObject): List<PlaylistSummary> {
        val results = mutableListOf<PlaylistSummary>()
        val seenIds = mutableSetOf<String>()

        val gridItems = root.findAllObjects("musicTwoRowItemRenderer")
        for (item in gridItems) {
            val browseId = item.obj("navigationEndpoint")?.obj("browseEndpoint")?.str("browseId")
                ?: continue
            if (browseId == "LM" || browseId == "VLLM" || browseId.startsWith("VL") || browseId.startsWith("PL") || browseId.startsWith("RD")) {
                val title = item.obj("title")?.extractText() ?: continue
                val id = browseId.removePrefix("VL")
                if (!seenIds.add(id)) continue
                val subtitle = item.obj("subtitle")?.extractText()
                val thumb = item.obj("thumbnailRenderer")
                    ?.obj("musicThumbnailRenderer")
                    ?.obj("thumbnail")
                    ?.extractThumbnailUrl()
                val menuTexts = item.findAllObjects("menuServiceItemRenderer")
                    .plus(item.findAllObjects("menuNavigationItemRenderer"))
                    .mapNotNull { it.obj("text")?.extractText() }
                val isEditable = browseId != "VLLM" && browseId != "LM" && menuTexts.any {
                    it.contains("Edit playlist", ignoreCase = true) ||
                        it.contains("Delete playlist", ignoreCase = true)
                }
                results += PlaylistSummary(
                    id = id,
                    browseId = if (browseId.startsWith("VL") || browseId == "LM") browseId else "VL$browseId",
                    title = title,
                    subtitle = subtitle,
                    thumbnailUrl = thumb,
                    isEditable = isEditable
                )
            }
        }

        // Fallback if YTM renders playlists as list rows
        val listItems = root.findAllObjects("musicResponsiveListItemRenderer")
        for (item in listItems) {
            val browseId = item.obj("navigationEndpoint")?.obj("browseEndpoint")?.str("browseId")
                ?: continue
            if (browseId == "LM" || browseId == "VLLM" || browseId.startsWith("VL") || browseId.startsWith("PL")) {
                val id = browseId.removePrefix("VL")
                if (!seenIds.add(id)) continue
                val cols = item.arr("flexColumns") ?: continue
                val title = cols.getOrNull(0)?.asObj()
                    ?.obj("musicResponsiveListItemFlexColumnRenderer")
                    ?.obj("text")?.extractText() ?: continue
                val subtitle = cols.getOrNull(1)?.asObj()
                    ?.obj("musicResponsiveListItemFlexColumnRenderer")
                    ?.obj("text")?.extractText()
                val thumb = item.obj("thumbnail")?.obj("musicThumbnailRenderer")
                    ?.obj("thumbnail")?.extractThumbnailUrl()
                val menuTexts = item.findAllObjects("menuServiceItemRenderer")
                    .plus(item.findAllObjects("menuNavigationItemRenderer"))
                    .mapNotNull { it.obj("text")?.extractText() }
                val isEditable = browseId != "VLLM" && browseId != "LM" && menuTexts.any {
                    it.contains("Edit playlist", ignoreCase = true) ||
                        it.contains("Delete playlist", ignoreCase = true)
                }
                results += PlaylistSummary(
                    id = id,
                    browseId = if (browseId.startsWith("VL") || browseId == "LM") browseId else "VL$browseId",
                    title = title,
                    subtitle = subtitle,
                    thumbnailUrl = thumb,
                    isEditable = isEditable
                )
            }
        }

        return results
    }

    fun parseLibraryAlbums(root: JsonObject): List<AlbumSummary> {
        return root.findAllObjects("musicTwoRowItemRenderer").mapNotNull { item ->
            val browseEndpoint = item.obj("navigationEndpoint")?.obj("browseEndpoint") ?: return@mapNotNull null
            val browseId = browseEndpoint.str("browseId") ?: return@mapNotNull null
            val title = item.obj("title")?.extractText() ?: return@mapNotNull null
            val subtitleRuns = item.obj("subtitle")?.arr("runs")
            val subtitle = item.obj("subtitle")?.extractText()
            val thumb = item.obj("thumbnailRenderer")
                ?.obj("musicThumbnailRenderer")
                ?.obj("thumbnail")
                ?.extractThumbnailUrl()
            AlbumSummary(
                browseId = browseId,
                title = title,
                artists = subtitleRuns.extractArtistsFromRuns(),
                isEpOrSingle = subtitle?.contains("EP", ignoreCase = true) == true ||
                    subtitle?.contains("Single", ignoreCase = true) == true,
                thumbnailUrl = thumb
            )
        }
    }

    fun parseLibraryArtists(root: JsonObject): List<ArtistSummary> {
        val fromList = root.findAllObjects("musicResponsiveListItemRenderer").mapNotNull { item ->
            val browseId = item.obj("navigationEndpoint")?.obj("browseEndpoint")?.str("browseId")
                ?: return@mapNotNull null
            val cols = item.arr("flexColumns") ?: return@mapNotNull null
            val name = cols.getOrNull(0)?.asObj()
                ?.obj("musicResponsiveListItemFlexColumnRenderer")
                ?.obj("text")
                ?.extractText() ?: return@mapNotNull null
            val subs = cols.getOrNull(1)?.asObj()
                ?.obj("musicResponsiveListItemFlexColumnRenderer")
                ?.obj("text")
                ?.extractText()
            val thumb = item.obj("thumbnail")?.obj("musicThumbnailRenderer")
                ?.obj("thumbnail")?.extractThumbnailUrl()
            ArtistSummary(browseId = browseId, name = name, subscriberCount = subs, thumbnailUrl = thumb)
        }
        if (fromList.isNotEmpty()) return fromList
        return root.findAllObjects("musicTwoRowItemRenderer").mapNotNull { item ->
            val browseId = item.obj("navigationEndpoint")?.obj("browseEndpoint")?.str("browseId")
                ?: return@mapNotNull null
            val name = item.obj("title")?.extractText() ?: return@mapNotNull null
            val subs = item.obj("subtitle")?.extractText()
            val thumb = item.obj("thumbnailRenderer")?.obj("musicThumbnailRenderer")
                ?.obj("thumbnail")?.extractThumbnailUrl()
            ArtistSummary(browseId = browseId, name = name, subscriberCount = subs, thumbnailUrl = thumb)
        }
    }

    fun parseAlbumResponse(
        root: JsonObject,
        browseId: String,
        fallbackAlbum: AlbumRef? = null,
        fallbackArtists: List<ArtistRef> = emptyList(),
        fallbackThumb: String? = null
    ): AlbumDetail {
        val header = root.findFirstObject("musicResponsiveHeaderRenderer")
            ?: root.findFirstObject("musicDetailHeaderRenderer")

        val title = header?.obj("title")?.extractText() ?: fallbackAlbum?.title ?: "Album"
        val description = header?.obj("description")
            ?.obj("musicDescriptionShelfRenderer")
            ?.obj("description")
            ?.extractText()
            ?: header?.obj("description")?.extractText()

        val subtitleRuns = header?.obj("subtitle")?.arr("runs")
        val straplineRuns = header?.obj("straplineTextOne")?.arr("runs")
        val artists = (straplineRuns ?: subtitleRuns).extractArtistsFromRuns().ifEmpty { fallbackArtists }
        val year = subtitleRuns?.firstOrNull { elem ->
            val t = elem.asObj()?.str("text")?.trim().orEmpty()
            t.length == 4 && t.all(Char::isDigit)
        }?.asObj()?.str("text")

        val subtitleText = header?.obj("subtitle")?.extractText().orEmpty()
        val isEp = subtitleText.contains("EP", ignoreCase = true) ||
            subtitleText.contains("Single", ignoreCase = true)

        val thumbnailUrl = header?.obj("thumbnail")
            ?.obj("musicThumbnailRenderer")
            ?.obj("thumbnail")
            ?.extractThumbnailUrl()
            ?: header?.obj("thumbnail")
                ?.obj("croppedSquareThumbnailRenderer")
                ?.obj("thumbnail")
                ?.extractThumbnailUrl()
            ?: fallbackThumb

        val playlistId = header?.findAllObjects("watchEndpoint")?.firstNotNullOfOrNull { it.str("playlistId") }
            ?: root.findAllObjects("watchPlaylistEndpoint").firstNotNullOfOrNull { it.str("playlistId") }

        val inLibrary = header?.findAllObjects("toggleButtonRenderer")
            ?.firstOrNull { btn ->
                val icon = btn.obj("defaultIcon")?.str("iconType").orEmpty()
                icon.contains("LIBRARY") || icon.contains("BOOKMARK")
            }?.bool("isToggled") ?: false

        val albumRef = fallbackAlbum ?: AlbumRef(id = browseId, title = title)
        val container = root.extractTrackShelfContainer() ?: root
        val rawTracks = container.findAllObjects("musicResponsiveListItemRenderer")
            .mapNotNull { parseTrackFromListItem(it, fallbackAlbum = albumRef, fallbackArtists = artists, fallbackThumb = thumbnailUrl) }

        return AlbumDetail(
            browseId = browseId,
            playlistId = playlistId,
            title = title,
            artists = artists,
            year = year,
            description = description,
            isEpOrSingle = isEp,
            thumbnailUrl = thumbnailUrl,
            tracks = rawTracks,
            continuation = container.extractShelfContinuationToken(),
            inLibrary = inLibrary
        )
    }

    fun parsePlaylistResponse(root: JsonObject, browseId: String): PlaylistDetail {
        val header = root.findFirstObject("musicResponsiveHeaderRenderer")
            ?: root.findFirstObject("musicDetailHeaderRenderer")
            ?: root.findFirstObject("musicEditablePlaylistDetailHeaderRenderer")
                ?.obj("header")
                ?.let { it.obj("musicResponsiveHeaderRenderer") ?: it.obj("musicDetailHeaderRenderer") }

        val editableHeader = root.findFirstObject("musicEditablePlaylistDetailHeaderRenderer")
        val title = header?.obj("title")?.extractText() ?: "Playlist"
        val description = header?.obj("description")
            ?.obj("musicDescriptionShelfRenderer")
            ?.obj("description")
            ?.extractText()
            ?: header?.obj("description")?.extractText()
        val author = header?.obj("straplineTextOne")?.extractText()
            ?: header?.obj("subtitle")?.extractText()
        val thumbnailUrl = header?.obj("thumbnail")
            ?.obj("musicThumbnailRenderer")
            ?.obj("thumbnail")
            ?.extractThumbnailUrl()
            ?: header?.obj("thumbnail")
                ?.obj("croppedSquareThumbnailRenderer")
                ?.obj("thumbnail")
                ?.extractThumbnailUrl()

        val container = root.extractTrackShelfContainer() ?: root
        val tracks = container.findAllObjects("musicResponsiveListItemRenderer")
            .mapNotNull { parseTrackFromListItem(it) }

        val isLikedSongs = browseId == "LM" || browseId == "VLLM"
        val headerMenuTexts = (editableHeader ?: header)
            ?.let {
                it.findAllObjects("menuServiceItemRenderer") +
                    it.findAllObjects("menuNavigationItemRenderer")
            }.orEmpty()
            .mapNotNull { it.obj("text")?.extractText() }
        val hasHeaderEditAction = headerMenuTexts.any {
            it.contains("Edit playlist", ignoreCase = true) ||
                it.contains("Delete playlist", ignoreCase = true)
        }
        val hasRemoveAction = root.findAllObjects("playlistEditEndpoint").any { ep ->
            ep.arr("actions").orEmpty().any { it.asObj()?.str("action") == "ACTION_REMOVE_VIDEO" }
        }
        val isEditable = !isLikedSongs && (editableHeader != null || hasHeaderEditAction || hasRemoveAction)
        val inLibrary = isEditable || isLikedSongs || header?.findAllObjects("toggleButtonRenderer")
            ?.firstOrNull { btn ->
                val icon = btn.obj("defaultIcon")?.str("iconType").orEmpty()
                icon.contains("LIBRARY") || icon.contains("BOOKMARK")
            }?.bool("isToggled") == true

        val radioId = header?.findAllObjects("menuNavigationItemRenderer")
            ?.firstOrNull { it.obj("icon")?.str("iconType") == "MIX" }
            ?.obj("navigationEndpoint")?.obj("watchPlaylistEndpoint")?.str("playlistId")

        val shuffleId = header?.findAllObjects("watchPlaylistEndpoint")
            ?.firstOrNull()?.str("playlistId")

        return PlaylistDetail(
            id = browseId.removePrefix("VL"),
            browseId = browseId,
            title = title,
            description = description,
            author = author,
            thumbnailUrl = thumbnailUrl,
            tracks = tracks,
            continuation = container.extractShelfContinuationToken(),
            isEditable = isEditable,
            inLibrary = inLibrary,
            radioEndpointPlaylistId = radioId,
            shuffleEndpointPlaylistId = shuffleId
        )
    }

    fun parseArtistResponse(root: JsonObject, browseId: String): ArtistDetail {
        val header = root.findFirstObject("musicImmersiveHeaderRenderer")
            ?: root.findFirstObject("musicVisualHeaderRenderer")
        val name = header?.obj("title")?.extractText() ?: "Artist"
        val description = header?.obj("description")?.extractText()
        val subButton = header?.obj("subscriptionButton")?.obj("subscribeButtonRenderer")
        val isSubscribed = subButton?.bool("subscribed") ?: false
        val subsCount = subButton?.obj("subscriberCountText")?.extractText()
            ?: subButton?.obj("longSubscriberCountText")?.extractText()
        val thumb = header?.obj("thumbnail")?.obj("musicThumbnailRenderer")
            ?.obj("thumbnail")?.extractThumbnailUrl()

        val shuffleId = header?.obj("playButton")?.obj("buttonRenderer")
            ?.obj("navigationEndpoint")?.obj("watchEndpoint")?.str("playlistId")
        val radioId = header?.obj("startRadioButton")?.obj("buttonRenderer")
            ?.obj("navigationEndpoint")?.obj("watchEndpoint")?.str("playlistId")

        val topSongsShelf = root.findAllObjects("musicShelfRenderer").firstOrNull()
        val topSongs = topSongsShelf?.arr("contents")?.mapNotNull { elem ->
            elem.asObj()?.obj("musicResponsiveListItemRenderer")?.let { parseTrackFromListItem(it) }
        } ?: emptyList()
        val topSongsBrowseId = topSongsShelf?.obj("bottomEndpoint")?.obj("browseEndpoint")?.str("browseId")

        val albums = mutableListOf<AlbumSummary>()
        val singlesAndEps = mutableListOf<AlbumSummary>()
        val relatedArtists = mutableListOf<ArtistSummary>()

        root.findAllObjects("musicCarouselShelfRenderer").forEach { shelf ->
            val shelfTitle = shelf.obj("header")
                ?.obj("musicCarouselShelfBasicHeaderRenderer")
                ?.obj("title")?.extractText().orEmpty().lowercase()
            val contents = shelf.arr("contents") ?: JsonArray(emptyList())
            contents.forEach { elem ->
                val twoRow = elem.asObj()?.obj("musicTwoRowItemRenderer") ?: return@forEach
                when (val parsed = parseTwoRowHomeItem(twoRow)) {
                    is HomeItem.AlbumItem -> {
                        if (shelfTitle.contains("single") || shelfTitle.contains("ep")) {
                            singlesAndEps += parsed.album.copy(isEpOrSingle = true)
                        } else {
                            albums += parsed.album
                        }
                    }
                    is HomeItem.ArtistItem -> relatedArtists += parsed.artist
                    else -> Unit
                }
            }
        }

        return ArtistDetail(
            browseId = browseId,
            name = name,
            description = description,
            subscriberCount = subsCount,
            thumbnailUrl = thumb,
            isSubscribed = isSubscribed,
            shufflePlaylistId = shuffleId,
            radioPlaylistId = radioId,
            topSongsBrowseId = topSongsBrowseId,
            topSongs = topSongs,
            albums = albums,
            singlesAndEps = singlesAndEps,
            relatedArtists = relatedArtists
        )
    }

    fun parseSearchResponse(root: JsonObject): SearchResultPage {
        val tracks = mutableListOf<Track>()
        val albums = mutableListOf<AlbumSummary>()
        val artists = mutableListOf<ArtistSummary>()
        val playlists = mutableListOf<PlaylistSummary>()

        val items = root.findAllObjects("musicResponsiveListItemRenderer")
        for (item in items) {
            val browseEndpoint = item.obj("navigationEndpoint")?.obj("browseEndpoint")
            val browseId = browseEndpoint?.str("browseId")
            val pageType = browseEndpoint?.obj("browseEndpointContextSupportedConfigs")
                ?.obj("browseEndpointContextMusicConfig")
                ?.str("pageType")

            if (browseId != null) {
                val cols = item.arr("flexColumns") ?: continue
                val title = cols.getOrNull(0)?.asObj()
                    ?.obj("musicResponsiveListItemFlexColumnRenderer")
                    ?.obj("text")?.extractText() ?: continue
                val subtitleRuns = cols.getOrNull(1)?.asObj()
                    ?.obj("musicResponsiveListItemFlexColumnRenderer")
                    ?.obj("text")?.arr("runs")
                val subtitle = cols.getOrNull(1)?.asObj()
                    ?.obj("musicResponsiveListItemFlexColumnRenderer")
                    ?.obj("text")?.extractText()
                val thumb = item.obj("thumbnail")?.obj("musicThumbnailRenderer")
                    ?.obj("thumbnail")?.extractThumbnailUrl()

                when {
                    pageType == "MUSIC_PAGE_TYPE_ARTIST" || browseId.startsWith("UC") -> {
                        artists += ArtistSummary(browseId = browseId, name = title, subscriberCount = subtitle, thumbnailUrl = thumb)
                    }
                    pageType == "MUSIC_PAGE_TYPE_ALBUM" || browseId.startsWith("MPREb_") -> {
                        albums += AlbumSummary(
                            browseId = browseId,
                            title = title,
                            artists = subtitleRuns.extractArtistsFromRuns(),
                            isEpOrSingle = subtitle?.contains("EP", true) == true || subtitle?.contains("Single", true) == true,
                            thumbnailUrl = thumb
                        )
                    }
                    else -> {
                        playlists += PlaylistSummary(
                            id = browseId.removePrefix("VL"),
                            browseId = browseId,
                            title = title,
                            subtitle = subtitle,
                            thumbnailUrl = thumb
                        )
                    }
                }
            } else {
                parseTrackFromListItem(item)?.let { tracks += it }
            }
        }

        return SearchResultPage(
            tracks = tracks,
            albums = albums,
            artists = artists,
            playlists = playlists,
            continuation = root.extractContinuationToken()
        )
    }

    fun parseSearchSuggestions(root: JsonObject): List<String> {
        return root.findAllObjects("searchSuggestionRenderer")
            .mapNotNull { it.obj("suggestion")?.extractText() }
            .distinct()
    }

    fun parseNextResponse(root: JsonObject): NextPage {
        val queueTracks = mutableListOf<Track>()
        var currentTrackMetadata: Track? = null
        val isVideoTypeMap = mutableMapOf<String, Boolean>()

        val panelContents = root.findAllObjects("playlistPanelRenderer")
            .flatMap { it.arr("contents").orEmpty() }
            .ifEmpty {
                root.findAllObjects("playlistPanelContinuation")
                    .flatMap { it.arr("contents").orEmpty() }
            }

        if (panelContents.isNotEmpty()) {
            for (elem in panelContents) {
                val obj = elem.asObj() ?: continue
                val wrapper = obj.obj("playlistPanelVideoWrapperRenderer")
                if (wrapper != null) {
                    val primary = wrapper.obj("primaryRenderer")?.obj("playlistPanelVideoRenderer")
                    val counterparts = wrapper.arr("counterpart").orEmpty().mapNotNull {
                        it.asObj()?.obj("counterpartRenderer")?.obj("playlistPanelVideoRenderer")
                    }
                    val candidates = listOfNotNull(primary) + counterparts
                    val chosen = chooseAudioPanelCandidate(candidates) ?: continue
                    val parsed = parseTrackFromPanelVideo(chosen) ?: continue
                    val chosenType = chosen.panelMusicVideoType()
                    if (chosenType == "MUSIC_VIDEO_TYPE_OMV" || chosenType == "MUSIC_VIDEO_TYPE_UGC") {
                        isVideoTypeMap[parsed.videoId] = true
                    }
                    queueTracks += parsed
                    if (candidates.any { it.bool("selected") == true }) {
                        currentTrackMetadata = parsed
                    }
                } else {
                    val renderer = obj.obj("playlistPanelVideoRenderer") ?: continue
                    val parsed = parseTrackFromPanelVideo(renderer) ?: continue
                    val mType = renderer.panelMusicVideoType()
                    if (mType == "MUSIC_VIDEO_TYPE_OMV" || mType == "MUSIC_VIDEO_TYPE_UGC") {
                        isVideoTypeMap[parsed.videoId] = true
                    }
                    queueTracks += parsed
                    if (renderer.bool("selected") == true) {
                        currentTrackMetadata = parsed
                    }
                }
            }
        } else {
            val panelVideos = root.findAllObjects("playlistPanelVideoRenderer")
            for (renderer in panelVideos) {
                val parsed = parseTrackFromPanelVideo(renderer) ?: continue
                val mType = renderer.panelMusicVideoType()
                if (mType == "MUSIC_VIDEO_TYPE_OMV" || mType == "MUSIC_VIDEO_TYPE_UGC") {
                    isVideoTypeMap[parsed.videoId] = true
                }
                queueTracks += parsed
                if (renderer.bool("selected") == true) {
                    currentTrackMetadata = parsed
                }
            }
        }

        val seedId = currentTrackMetadata?.videoId
        val dedupedQueue = deduplicateMixTracks(
            tracks = queueTracks,
            allowSeedVideoId = seedId,
            explicitVideoIds = isVideoTypeMap.filterValues { it }.keys
        )

        val tabs = root.findAllObjects("tabRenderer")
        var lyricsBrowseId: String? = null
        var relatedBrowseId: String? = null
        for (tab in tabs) {
            val tabTitle = tab.str("title").orEmpty().lowercase()
            val endpointId = tab.obj("endpoint")?.obj("browseEndpoint")?.str("browseId")
            if (endpointId != null) {
                when {
                    tabTitle.contains("lyric") -> lyricsBrowseId = endpointId
                    tabTitle.contains("related") -> relatedBrowseId = endpointId
                }
            }
        }

        val mixChips = root.findAllObjects("chipCloudChipRenderer").mapNotNull { chip ->
            val title = chip.obj("text")?.extractText() ?: return@mapNotNull null
            val queueEp = chip.obj("navigationEndpoint")?.obj("queueUpdateCommand")
                ?.obj("fetchContentsCommand")?.obj("watchEndpoint")
                ?: chip.obj("navigationEndpoint")?.obj("watchEndpoint")
                ?: return@mapNotNull null
            val playlistId = queueEp.str("playlistId") ?: return@mapNotNull null
            MixChip(
                title = title,
                playlistId = playlistId,
                params = queueEp.str("params"),
                isSelected = chip.bool("isSelected") == true
            )
        }

        val panel = root.findFirstObject("playlistPanelRenderer")
        val continuation = panel
            ?.arr("continuations")
            ?.firstOrNull()?.asObj()
            ?.obj("nextRadioContinuationData")?.str("continuation")
            ?: root.extractContinuationToken()

        val playlistId = panel?.str("playlistId")
        val playlistTitle = panel?.str("title")?.takeIf { it.isNotBlank() }
            ?: panel?.obj("title")?.extractText()?.takeIf { it.isNotBlank() }

        return NextPage(
            tracks = dedupedQueue,
            currentTrackMetadata = currentTrackMetadata ?: dedupedQueue.firstOrNull(),
            playlistId = playlistId,
            continuation = continuation,
            lyricsBrowseId = lyricsBrowseId,
            relatedBrowseId = relatedBrowseId,
            mixChips = mixChips,
            playlistTitle = playlistTitle
        )
    }

    private fun JsonObject.panelMusicVideoType(): String? =
        obj("navigationEndpoint")
            ?.obj("watchEndpoint")
            ?.obj("watchEndpointMusicSupportedConfigs")
            ?.obj("watchEndpointMusicConfig")
            ?.str("musicVideoType")

    private fun chooseAudioPanelCandidate(candidates: List<JsonObject>): JsonObject? {
        if (candidates.isEmpty()) return null
        return candidates.firstOrNull { it.panelMusicVideoType() == "MUSIC_VIDEO_TYPE_ATV" }
            ?: candidates.firstOrNull {
                val t = it.panelMusicVideoType()
                t != "MUSIC_VIDEO_TYPE_OMV" && t != "MUSIC_VIDEO_TYPE_UGC"
            }
            ?: candidates.firstOrNull()
    }

    fun deduplicateMixTracks(
        tracks: List<Track>,
        existing: List<Track> = emptyList(),
        allowSeedVideoId: String? = null,
        explicitVideoIds: Set<String> = emptySet()
    ): List<Track> {
        val seenIds = existing.mapTo(mutableSetOf()) { it.videoId }
        val seenKeys = existing.mapTo(mutableSetOf()) { mixTrackDedupKey(it) }
        val nonVideoFirst = tracks.sortedBy {
            if (it.videoId in explicitVideoIds || isExplicitMusicVideoTitle(it.title)) 1 else 0
        }
        val keptIds = linkedSetOf<String>()
        for (track in nonVideoFirst) {
            if (track.videoId in seenIds) continue
            val isExplicitMv = track.videoId != allowSeedVideoId &&
                (track.videoId in explicitVideoIds || isExplicitMusicVideoTitle(track.title))
            if (isExplicitMv) continue
            val key = mixTrackDedupKey(track)
            if (!seenKeys.add(key)) continue
            seenIds.add(track.videoId)
            keptIds.add(track.videoId)
        }
        return tracks.filter { it.videoId in keptIds }.distinctBy { it.videoId }
    }

    private fun mixTrackDedupKey(track: Track): String {
        val cleanTitle = cleanMixTrackTitle(track.title)
        val mainArtist = track.artists.firstOrNull()?.name?.trim()?.lowercase().orEmpty()
        return "$cleanTitle|$mainArtist"
    }

    private fun cleanMixTrackTitle(title: String): String {
        return title
            .replace(Regex("\\s*[(\\[]\\s*(?:official\\s+)?(?:music\\s+)?(?:lyric\\s+)?(?:video|audio|visualizer|hd|4k)[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*-\\s*(?:official\\s+)?(?:music\\s+)?(?:lyric\\s+)?(?:video|audio)\\s*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()
    }

    private fun isExplicitMusicVideoTitle(title: String): Boolean {
        val lower = title.lowercase()
        return lower.contains("official music video") ||
            lower.contains("official video") ||
            Regex("\\((?:official\\s+)?music\\s+video\\)|\\[(?:official\\s+)?music\\s+video\\]").containsMatchIn(lower)
    }

    fun parseBrowseLyrics(root: JsonObject): String? {
        return root.findFirstObject("musicDescriptionShelfRenderer")
            ?.obj("description")
            ?.extractText()
    }

    fun parseTrackFromListItem(
        item: JsonObject,
        fallbackAlbum: AlbumRef? = null,
        fallbackArtists: List<ArtistRef> = emptyList(),
        fallbackThumb: String? = null
    ): Track? {
        val cols = item.arr("flexColumns") ?: return null
        val titleRunObj = cols.getOrNull(0)?.asObj()
            ?.obj("musicResponsiveListItemFlexColumnRenderer")
            ?.obj("text")
        val title = titleRunObj?.extractText() ?: return null

        val videoId = item.obj("playlistItemData")?.str("videoId")
            ?: titleRunObj.arr("runs")?.firstOrNull()?.asObj()
                ?.obj("navigationEndpoint")?.obj("watchEndpoint")?.str("videoId")
            ?: item.obj("overlay")?.obj("musicItemThumbnailOverlayRenderer")
                ?.obj("content")?.obj("musicPlayButtonRenderer")
                ?.obj("playNavigationEndpoint")?.obj("watchEndpoint")?.str("videoId")
            ?: return null

        val setVideoId = item.obj("playlistItemData")?.str("playlistSetVideoId")
            ?: extractSetVideoIdFromMenu(item)

        val secondaryRuns = cols.getOrNull(1)?.asObj()
            ?.obj("musicResponsiveListItemFlexColumnRenderer")
            ?.obj("text")?.arr("runs")

        val thirdRuns = cols.getOrNull(2)?.asObj()
            ?.obj("musicResponsiveListItemFlexColumnRenderer")
            ?.obj("text")?.arr("runs")

        val fixedColText = item.arr("fixedColumns")?.firstOrNull()?.asObj()
            ?.obj("musicResponsiveListItemFixedColumnRenderer")
            ?.obj("text")?.extractText()

        val artists = (secondaryRuns.extractArtistsFromRuns() + thirdRuns.extractArtistsFromRuns())
            .distinctBy { it.name + (it.id ?: "") }
            .ifEmpty { fallbackArtists }

        var album = extractAlbumFromRuns(secondaryRuns)
            ?: extractAlbumFromRuns(thirdRuns)
            ?: extractAlbumFromMenu(item)
            ?: fallbackAlbum

        if (album?.id == null) {
            val menuAlbum = extractAlbumFromMenu(item)
            if (menuAlbum != null) album = menuAlbum
        }

        val durationStr = fixedColText
            ?: secondaryRuns.extractDurationString()
            ?: thirdRuns.extractDurationString()

        val thumb = item.obj("thumbnail")?.obj("musicThumbnailRenderer")
            ?.obj("thumbnail")?.extractThumbnailUrl()
            ?: fallbackThumb

        val (addToken, removeToken, inLib) = extractLibraryTokens(item)
        val isLiked = extractLikeState(item)
        val isExplicit = item.findAllObjects("musicInlineBadgeRenderer")
            .any { it.obj("icon")?.str("iconType") == "MUSIC_EXPLICIT_BADGE" }

        return Track(
            videoId = videoId,
            title = title,
            artists = artists,
            album = album,
            durationMs = parseDurationMs(durationStr),
            thumbnailUrl = thumb,
            inLibrary = inLib,
            isLiked = isLiked,
            feedbackTokenAdd = addToken,
            feedbackTokenRemove = removeToken,
            setVideoId = setVideoId,
            isExplicit = isExplicit
        )
    }

    private fun parseTrackFromPanelVideo(renderer: JsonObject): Track? {
        val videoId = renderer.str("videoId") ?: return null
        val title = renderer.obj("title")?.extractText() ?: return null
        val bylineRuns = renderer.obj("longBylineText")?.arr("runs")
            ?: renderer.obj("shortBylineText")?.arr("runs")

        val artists = bylineRuns.extractArtistsFromRuns()
        val album = extractAlbumFromRuns(bylineRuns) ?: extractAlbumFromMenu(renderer)
        val durationStr = renderer.obj("lengthText")?.extractText()
        val thumb = renderer.obj("thumbnail")?.extractThumbnailUrl()
        val (addToken, removeToken, inLib) = extractLibraryTokens(renderer)
        val isLiked = extractLikeState(renderer)

        return Track(
            videoId = videoId,
            title = title,
            artists = artists,
            album = album,
            durationMs = parseDurationMs(durationStr),
            thumbnailUrl = thumb,
            inLibrary = inLib,
            isLiked = isLiked,
            feedbackTokenAdd = addToken,
            feedbackTokenRemove = removeToken
        )
    }

    private fun extractSetVideoIdFromMenu(item: JsonObject): String? {
        val endpoints = item.findAllObjects("playlistEditEndpoint")
        for (ep in endpoints) {
            val actions = ep.arr("actions") ?: continue
            for (action in actions) {
                val sv = action.asObj()?.str("setVideoId")
                if (!sv.isNullOrBlank()) return sv
            }
        }
        return null
    }

    private fun extractAlbumFromRuns(runs: JsonArray?): AlbumRef? {
        if (runs == null) return null
        for (run in runs) {
            val obj = run.asObj() ?: continue
            val browseEp = obj.obj("navigationEndpoint")?.obj("browseEndpoint") ?: continue
            val browseId = browseEp.str("browseId") ?: continue
            val pageType = browseEp.obj("browseEndpointContextSupportedConfigs")
                ?.obj("browseEndpointContextMusicConfig")
                ?.str("pageType")
            if (pageType == "MUSIC_PAGE_TYPE_ALBUM" || browseId.startsWith("MPREb_")) {
                val title = obj.str("text") ?: continue
                return AlbumRef(id = browseId, title = title)
            }
        }
        return null
    }

    private fun extractAlbumFromMenu(obj: JsonObject): AlbumRef? {
        val navItems = obj.findAllObjects("menuNavigationItemRenderer")
        for (nav in navItems) {
            val icon = nav.obj("icon")?.str("iconType").orEmpty()
            val text = nav.obj("text")?.extractText().orEmpty()
            if (icon == "ALBUM" || text.contains("Go to album", ignoreCase = true)) {
                val browseId = nav.obj("navigationEndpoint")?.obj("browseEndpoint")?.str("browseId")
                if (!browseId.isNullOrBlank()) {
                    return AlbumRef(id = browseId, title = "")
                }
            }
        }
        return null
    }

    private data class LibraryTokens(
        val addToken: String?,
        val removeToken: String?,
        val inLibrary: Boolean
    )

    private fun extractFeedbackToken(endpoint: JsonObject?): String? {
        if (endpoint == null) return null
        val direct = endpoint.obj("feedbackEndpoint")?.let { ep ->
            ep.str("feedbackToken")?.takeIf { it.isNotBlank() }
                ?: ep.arr("feedbackTokens")?.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull }?.takeIf { it.isNotBlank() }
        }
        if (direct != null) return direct

        val nested = endpoint.findFirstObject("feedbackEndpoint")?.let { ep ->
            ep.str("feedbackToken")?.takeIf { it.isNotBlank() }
                ?: ep.arr("feedbackTokens")?.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull }?.takeIf { it.isNotBlank() }
        }
        return nested
    }

    private fun extractLibraryTokens(obj: JsonObject): LibraryTokens {
        var addToken: String? = null
        var removeToken: String? = null
        var inLibrary = false

        val toggles = obj.findAllObjects("toggleMenuServiceItemRenderer")
        for (toggle in toggles) {
            val defIcon = toggle.obj("defaultIcon")?.str("iconType").orEmpty()
            val togIcon = toggle.obj("toggledIcon")?.str("iconType").orEmpty()
            val defText = toggle.obj("defaultText")?.extractText().orEmpty()
            val togText = toggle.obj("toggledText")?.extractText().orEmpty()
            if (defIcon.contains("PLAYLIST") || togIcon.contains("PLAYLIST") ||
                defText.contains("playlist", ignoreCase = true) || togText.contains("playlist", ignoreCase = true)
            ) {
                continue
            }
            val isLibItem = defIcon.contains("LIBRARY") || togIcon.contains("LIBRARY") ||
                defIcon.contains("BOOKMARK") || togIcon.contains("BOOKMARK") ||
                defIcon.contains("CHECK") || togIcon.contains("CHECK") ||
                defText.contains("library", ignoreCase = true) || togText.contains("library", ignoreCase = true)
            if (!isLibItem) continue

            val defToken = extractFeedbackToken(toggle.obj("defaultServiceEndpoint"))
            val togToken = extractFeedbackToken(toggle.obj("toggledServiceEndpoint"))
            val isToggled = toggle.bool("isToggled") == true

            val isDefaultSaved = defIcon == "LIBRARY_SAVED" ||
                defIcon == "LIBRARY_REMOVE" ||
                defIcon == "REMOVE_FROM_LIBRARY" ||
                defIcon == "BOOKMARK" ||
                defIcon == "CHECK" ||
                defIcon == "CHECK_CIRCLE" ||
                defIcon == "CHECK_CIRCLE_THICK" ||
                defIcon.contains("SAVED") ||
                defIcon.contains("REMOVE") ||
                defText.contains("Remove", true)

            if (isDefaultSaved) {
                inLibrary = !isToggled
                if (defToken != null) removeToken = defToken
                if (togToken != null) addToken = togToken
            } else {
                inLibrary = isToggled
                if (defToken != null) addToken = defToken
                if (togToken != null) removeToken = togToken
            }
        }

        if (addToken == null || removeToken == null) {
            val serviceItems = obj.findAllObjects("menuServiceItemRenderer")
            for (item in serviceItems) {
                val icon = item.obj("icon")?.str("iconType").orEmpty()
                val text = item.obj("text")?.extractText().orEmpty()
                if (icon.contains("PLAYLIST") || text.contains("playlist", ignoreCase = true)) continue
                val isLibItem = icon.contains("LIBRARY") || icon.contains("BOOKMARK") || icon.contains("CHECK") ||
                    text.contains("library", ignoreCase = true)
                if (!isLibItem) continue

                val tok = extractFeedbackToken(item.obj("serviceEndpoint"))
                val isRemoveAction = icon == "LIBRARY_REMOVE" ||
                    icon == "REMOVE_FROM_LIBRARY" ||
                    icon == "LIBRARY_SAVED" ||
                    icon.contains("REMOVE") ||
                    icon.contains("SAVED") ||
                    text.contains("Remove", ignoreCase = true)
                if (isRemoveAction) {
                    inLibrary = true
                    if (tok != null && removeToken == null) removeToken = tok
                } else {
                    if (tok != null && addToken == null) addToken = tok
                }
            }
        }
        return LibraryTokens(addToken, removeToken, inLibrary)
    }

    private fun extractLikeState(obj: JsonObject): Boolean {
        val likeRenderer = obj.findFirstObject("likeButtonRenderer")
        val status = likeRenderer?.str("likeStatus")
        return status == "LIKE"
    }

    private fun JsonArray?.extractArtistsFromRuns(): List<ArtistRef> {
        if (this == null) return emptyList()
        val artists = mutableListOf<ArtistRef>()
        for (elem in this) {
            val obj = elem.asObj() ?: continue
            val text = obj.str("text")?.trim() ?: continue
            if (text.isBlank() || text == "•" || text == "," || text == "&" || text == "feat." || text == "ft.") continue
            val browseEp = obj.obj("navigationEndpoint")?.obj("browseEndpoint")
            val browseId = browseEp?.str("browseId")
            val pageType = browseEp?.obj("browseEndpointContextSupportedConfigs")
                ?.obj("browseEndpointContextMusicConfig")
                ?.str("pageType")
            if (pageType == "MUSIC_PAGE_TYPE_ARTIST" || (browseId != null && browseId.startsWith("UC"))) {
                artists += ArtistRef(id = browseId, name = text)
            }
        }
        if (artists.isNotEmpty()) return artists

        // Fallback for unlinked artist text runs before " • " or duration
        val rawNames = mutableListOf<String>()
        for (elem in this) {
            val text = elem.asObj()?.str("text")?.trim() ?: continue
            if (text == "•") break
            if (text.equals("Song", true) || text.equals("Video", true) || text.equals("Album", true) ||
                text.equals("EP", true) || text.equals("Single", true) || text.equals("Playlist", true)
            ) {
                continue
            }
            if (text == "," || text == "&") continue
            if (parseDurationMs(text) > 0L) continue
            if (text.length == 4 && text.all(Char::isDigit)) continue
            if (text.contains("plays", true) || text.contains("views", true)) continue
            rawNames += text
        }
        return rawNames.take(3).map { ArtistRef(id = null, name = it) }
    }

    private fun JsonArray?.extractDurationString(): String? {
        if (this == null) return null
        for (elem in this.reversed()) {
            val t = elem.asObj()?.str("text")?.trim() ?: continue
            if (parseDurationMs(t) > 0L) return t
        }
        return null
    }

    fun parseDurationMs(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val trimmed = raw.trim()
        if (!trimmed.contains(':')) return 0L
        val parts = trimmed.split(':').mapNotNull { it.toLongOrNull() }
        if (parts.isEmpty() || parts.size > 3) return 0L
        return when (parts.size) {
            2 -> (parts[0] * 60L + parts[1]) * 1000L
            3 -> (parts[0] * 3600L + parts[1] * 60L + parts[2]) * 1000L
            else -> 0L
        }
    }

    private fun JsonObject.extractThumbnailUrl(): String? {
        val thumbs = arr("thumbnails") ?: return null
        val url = thumbs.lastOrNull()?.asObj()?.str("url") ?: return null
        return upgradeThumbnailResolution(url)
    }

    fun upgradeThumbnailResolution(url: String): String {
        val normalized = if (url.startsWith("//")) "https:$url" else url
        return when {
            normalized.contains("googleusercontent.com") || normalized.contains("ggpht.com") -> {
                val wHPattern = Regex("=w\\d+-h\\d+[^\"\\s]*")
                val sPattern = Regex("=s\\d+[^\"\\s]*")
                when {
                    wHPattern.containsMatchIn(normalized) ->
                        normalized.replace(wHPattern, "=w1080-h1080-l90-rj")
                    sPattern.containsMatchIn(normalized) ->
                        normalized.replace(sPattern, "=w1080-h1080-l90-rj")
                    else -> normalized
                }
            }
            normalized.contains("i.ytimg.com") || normalized.contains("img.youtube.com") -> {
                normalized
                    .replace("/default.jpg", "/sddefault.jpg")
                    .replace("/mqdefault.jpg", "/sddefault.jpg")
                    .replace("/hqdefault.jpg", "/sddefault.jpg")
            }
            else -> normalized
        }
    }

    private fun JsonObject.extractTrackShelfContainer(): JsonObject? {
        return findFirstObject("appendContinuationItemsAction")
            ?: findFirstObject("musicPlaylistShelfContinuation")
            ?: findFirstObject("musicShelfContinuation")
            ?: findFirstObject("musicPlaylistShelfRenderer")
            ?: findFirstObject("musicShelfRenderer")
    }

    private fun JsonObject.extractShelfContinuationToken(): String? {
        return findFirstObject("continuationCommand")?.str("token")
            ?: findFirstObject("nextContinuationData")?.str("continuation")
    }

    private fun JsonObject.extractContinuationToken(): String? {
        return findFirstObject("nextContinuationData")?.str("continuation")
            ?: findFirstObject("continuationCommand")?.str("token")
    }

    private fun JsonObject.extractText(): String? {
        str("simpleText")?.let { return it }
        val runs = arr("runs") ?: return null
        return runs.joinToString("") { it.asObj()?.str("text").orEmpty() }.ifBlank { null }
    }

    // --- Lightweight recursive JSON helpers ---
    private fun JsonElement.asObj(): JsonObject? = this as? JsonObject
    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.findFirstArray(vararg path: String): JsonArray? {
        if (path.size == 1) {
            return findFirstObject(path[0])?.let { it as? JsonArray } ?: arr(path[0])
        }
        val parent = findFirstObject(path[0]) ?: return null
        var cur: JsonElement = parent
        for (i in 1 until path.size - 1) {
            cur = (cur as? JsonObject)?.get(path[i]) ?: return null
        }
        return (cur as? JsonObject)?.get(path.last()) as? JsonArray
    }

    private fun JsonElement.findFirstObject(targetKey: String): JsonObject? {
        when (this) {
            is JsonObject -> {
                (this[targetKey] as? JsonObject)?.let { return it }
                for (value in this.values) {
                    value.findFirstObject(targetKey)?.let { return it }
                }
            }
            is JsonArray -> {
                for (elem in this) {
                    elem.findFirstObject(targetKey)?.let { return it }
                }
            }
            else -> Unit
        }
        return null
    }

    private fun JsonElement.findAllObjects(targetKey: String, out: MutableList<JsonObject> = mutableListOf()): List<JsonObject> {
        when (this) {
            is JsonObject -> {
                (this[targetKey] as? JsonObject)?.let { out += it }
                for (value in this.values) {
                    value.findAllObjects(targetKey, out)
                }
            }
            is JsonArray -> {
                for (elem in this) {
                    elem.findAllObjects(targetKey, out)
                }
            }
            else -> Unit
        }
        return out
    }
}
