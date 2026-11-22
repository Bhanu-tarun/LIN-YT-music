package com.lin.music.core.innertube.parser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import com.lin.music.core.model.ArtistRef
import com.lin.music.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InnerTubeParsersTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parsePlaylistResponse extracts setVideoId albumId artistId and feedbackTokens`() {
        val fixture = """
        {
          "header": {
            "musicResponsiveHeaderRenderer": {
              "title": { "runs": [{ "text": "Late Night Synth" }] },
              "subtitle": { "runs": [{ "text": "Playlist • 2026" }] },
              "thumbnail": {
                "musicThumbnailRenderer": {
                  "thumbnail": {
                    "thumbnails": [{ "url": "https://lh3.googleusercontent.com/cover=w120-h120", "width": 120, "height": 120 }]
                  }
                }
              }
            }
          },
          "contents": {
            "twoColumnBrowseResultsRenderer": {
              "secondaryContents": {
                "sectionListRenderer": {
                  "contents": [{
                    "musicPlaylistShelfRenderer": {
                      "contents": [{
                        "musicResponsiveListItemRenderer": {
                          "playlistItemData": {
                            "videoId": "vid123",
                            "playlistSetVideoId": "SET_VID_999"
                          },
                          "flexColumns": [
                            {
                              "musicResponsiveListItemFlexColumnRenderer": {
                                "text": { "runs": [{ "text": "Midnight Pulse" }] }
                              }
                            },
                            {
                              "musicResponsiveListItemFlexColumnRenderer": {
                                "text": {
                                  "runs": [
                                    {
                                      "text": "Klangwerk",
                                      "navigationEndpoint": {
                                        "browseEndpoint": {
                                          "browseId": "UCartist001",
                                          "browseEndpointContextSupportedConfigs": {
                                            "browseEndpointContextMusicConfig": { "pageType": "MUSIC_PAGE_TYPE_ARTIST" }
                                          }
                                        }
                                      }
                                    },
                                    { "text": " • " },
                                    {
                                      "text": "Neon EP",
                                      "navigationEndpoint": {
                                        "browseEndpoint": {
                                          "browseId": "MPREb_album001",
                                          "browseEndpointContextSupportedConfigs": {
                                            "browseEndpointContextMusicConfig": { "pageType": "MUSIC_PAGE_TYPE_ALBUM" }
                                          }
                                        }
                                      }
                                    }
                                  ]
                                }
                              }
                            }
                          ],
                          "fixedColumns": [
                            {
                              "musicResponsiveListItemFixedColumnRenderer": {
                                "text": { "runs": [{ "text": "4:12" }] }
                              }
                            }
                          ],
                          "menu": {
                            "menuRenderer": {
                              "items": [
                                {
                                  "toggleMenuServiceItemRenderer": {
                                    "defaultIcon": { "iconType": "LIBRARY_ADD" },
                                    "toggledIcon": { "iconType": "LIBRARY_SAVED" },
                                    "isToggled": false,
                                    "defaultServiceEndpoint": { "feedbackEndpoint": { "feedbackToken": "ADD_TOK_1" } },
                                    "toggledServiceEndpoint": { "feedbackEndpoint": { "feedbackToken": "REM_TOK_1" } }
                                  }
                                }
                              ]
                            }
                          }
                        }
                      }]
                    }
                  }]
                }
              }
            }
          }
        }
        """.trimIndent()

        val root = json.parseToJsonElement(fixture).jsonObject
        val detail = InnerTubeParsers.parsePlaylistResponse(root, "VLPL123")

        assertEquals("Late Night Synth", detail.title)
        assertFalse(detail.isEditable)
        assertFalse(detail.inLibrary)
        assertEquals(1, detail.tracks.size)
        val track = detail.tracks.first()
        assertEquals("vid123", track.videoId)
        assertEquals("SET_VID_999", track.setVideoId)
        assertEquals("Midnight Pulse", track.title)
        assertEquals("UCartist001", track.artists.first().id)
        assertEquals("Klangwerk", track.artists.first().name)
        assertEquals("MPREb_album001", track.album?.id)
        assertEquals("Neon EP", track.album?.title)
        assertEquals(252_000L, track.durationMs)
        assertFalse(track.inLibrary)
        assertEquals("ADD_TOK_1", track.feedbackTokenAdd)
        assertEquals("REM_TOK_1", track.feedbackTokenRemove)
        assertTrue(detail.thumbnailUrl!!.contains("w1080-h1080"))
    }

    @Test
    fun `parseNextResponse extracts mix chips, lyrics browseId, and queue`() {
        val fixture = """
        {
          "contents": {
            "singleColumnMusicWatchNextResultsRenderer": {
              "tabbedRenderer": {
                "watchNextTabbedResultsRenderer": {
                  "tabs": [
                    {
                      "tabRenderer": {
                        "title": "Up next",
                        "content": {
                          "musicQueueRenderer": {
                            "subHeaderChipCloud": {
                              "chipCloudRenderer": {
                                "chips": [
                                  {
                                    "chipCloudChipRenderer": {
                                      "text": { "runs": [{ "text": "Familiar" }] },
                                      "isSelected": false,
                                      "navigationEndpoint": {
                                        "queueUpdateCommand": {
                                          "fetchContentsCommand": {
                                            "watchEndpoint": { "playlistId": "RDAMVMabc", "params": "wAEB8gECGAE%3D" }
                                          }
                                        }
                                      }
                                    }
                                  }
                                ]
                              }
                            },
                            "content": {
                              "playlistPanelRenderer": {
                                "playlistId": "RDAMVMabc",
                                "contents": [
                                  {
                                    "playlistPanelVideoRenderer": {
                                      "videoId": "abc",
                                      "selected": true,
                                      "title": { "runs": [{ "text": "Solaris" }] },
                                      "lengthText": { "runs": [{ "text": "3:30" }] },
                                      "longBylineText": {
                                        "runs": [
                                          {
                                            "text": "Vesper",
                                            "navigationEndpoint": { "browseEndpoint": { "browseId": "UCvesper" } }
                                          }
                                        ]
                                      }
                                    }
                                  }
                                ]
                              }
                            }
                          }
                        }
                      }
                    },
                    {
                      "tabRenderer": {
                        "title": "Lyrics",
                        "endpoint": { "browseEndpoint": { "browseId": "MPLYt_123" } }
                      }
                    }
                  ]
                }
              }
            }
          }
        }
        """.trimIndent()

        val page = InnerTubeParsers.parseNextResponse(json.parseToJsonElement(fixture).jsonObject)
        assertEquals("RDAMVMabc", page.playlistId)
        assertEquals("MPLYt_123", page.lyricsBrowseId)
        assertEquals(1, page.mixChips.size)
        assertEquals("Familiar", page.mixChips.first().title)
        assertNotNull(page.currentTrackMetadata)
        assertEquals("Solaris", page.currentTrackMetadata?.title)
        assertEquals(210_000L, page.currentTrackMetadata?.durationMs)
    }

    @Test
    fun `parseAlbumResponse identifies EP and attaches album metadata to tracks`() {
        val fixture = """
        {
          "header": {
            "musicResponsiveHeaderRenderer": {
              "title": { "runs": [{ "text": "Daybreak EP" }] },
              "subtitle": { "runs": [{ "text": "EP" }, { "text": " • " }, { "text": "2024" }] },
              "straplineTextOne": {
                "runs": [
                  {
                    "text": "Solstice",
                    "navigationEndpoint": { "browseEndpoint": { "browseId": "UCsolstice" } }
                  }
                ]
              }
            }
          },
          "contents": {
            "twoColumnBrowseResultsRenderer": {
              "secondaryContents": {
                "sectionListRenderer": {
                  "contents": [{
                    "musicShelfRenderer": {
                      "contents": [{
                        "musicResponsiveListItemRenderer": {
                          "playlistItemData": { "videoId": "epTrack1" },
                          "flexColumns": [
                            { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Dawn" }] } } }
                          ]
                        }
                      }]
                    }
                  }]
                }
              }
            }
          }
        }
        """.trimIndent()

        val album = InnerTubeParsers.parseAlbumResponse(json.parseToJsonElement(fixture).jsonObject, "MPREb_ep1")
        assertTrue(album.isEpOrSingle)
        assertEquals("2024", album.year)
        assertEquals(1, album.tracks.size)
        assertEquals("MPREb_ep1", album.tracks.first().album?.id)
        assertEquals("Solstice", album.tracks.first().artistDisplay)
    }

    @Test
    fun `parseHomeResponse parses gridRenderer speed dial and parseLibraryPlaylists handles list items`() {
        val homeFixture = """
        {
          "contents": {
            "singleColumnBrowseResultsRenderer": {
              "tabs": [{
                "tabRenderer": {
                  "content": {
                    "sectionListRenderer": {
                      "contents": [{
                        "gridRenderer": {
                          "header": { "gridHeaderRenderer": { "title": { "runs": [{ "text": "Speed dial" }] } } },
                          "items": [
                            {
                              "musicTwoRowItemRenderer": {
                                "title": { "runs": [{ "text": "Nightcall" }] },
                                "navigationEndpoint": { "watchEndpoint": { "videoId": "nc123" } }
                              }
                            }
                          ]
                        }
                      }]
                    }
                  }
                }
              }]
            }
          }
        }
        """.trimIndent()
        val home = InnerTubeParsers.parseHomeResponse(json.parseToJsonElement(homeFixture).jsonObject)
        assertEquals(1, home.sections.size)
        assertEquals("Speed dial", home.sections.first().title)

        val plFixture = """
        {
          "contents": {
            "singleColumnBrowseResultsRenderer": {
              "tabs": [{
                "tabRenderer": {
                  "content": {
                    "sectionListRenderer": {
                      "contents": [{
                        "musicShelfRenderer": {
                          "contents": [{
                            "musicResponsiveListItemRenderer": {
                              "navigationEndpoint": { "browseEndpoint": { "browseId": "VLPL_MY_LIST" } },
                              "flexColumns": [
                                { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Focus Flow" }] } } },
                                { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Playlist • 18 songs" }] } } }
                              ]
                            }
                          }]
                        }
                      }]
                    }
                  }
                }
              }]
            }
          }
        }
        """.trimIndent()
        val playlists = InnerTubeParsers.parseLibraryPlaylists(json.parseToJsonElement(plFixture).jsonObject)
        assertEquals(1, playlists.size)
        assertEquals("PL_MY_LIST", playlists.first().id)
        assertEquals("Focus Flow", playlists.first().title)
        assertFalse(playlists.first().isEditable)
    }

    @Test
    fun `parsePlaylistResponse marks editable playlists when musicEditablePlaylistDetailHeaderRenderer is present`() {
        val editableFixture = """
        {
          "header": {
            "musicEditablePlaylistDetailHeaderRenderer": {
              "header": {
                "musicResponsiveHeaderRenderer": {
                  "title": { "runs": [{ "text": "My Owned Mix" }] },
                  "subtitle": { "runs": [{ "text": "Playlist • You" }] }
                }
              }
            }
          },
          "contents": {
            "twoColumnBrowseResultsRenderer": {
              "secondaryContents": {
                "sectionListRenderer": { "contents": [] }
              }
            }
          }
        }
        """.trimIndent()
        val detail = InnerTubeParsers.parsePlaylistResponse(json.parseToJsonElement(editableFixture).jsonObject, "VLPL_OWNED")
        assertEquals("My Owned Mix", detail.title)
        assertTrue(detail.isEditable)
        assertTrue(detail.inLibrary)
    }

    @Test
    fun `extractLibraryTokens handles commandExecutorCommand, LIBRARY_REMOVE, and menuServiceItemRenderer`() {
        val wrappedAndRemoveFixture = """
        {
          "contents": {
            "singleColumnMusicWatchNextResultsRenderer": {
              "tabbedRenderer": {
                "watchNextTabbedResultsRenderer": {
                  "tabs": [{
                    "tabRenderer": {
                      "title": "Up next",
                      "content": {
                        "musicQueueRenderer": {
                          "content": {
                            "playlistPanelRenderer": {
                              "playlistId": "RDAMVMx",
                              "contents": [
                                {
                                  "playlistPanelVideoRenderer": {
                                    "videoId": "vidCommandExec",
                                    "selected": true,
                                    "title": { "runs": [{ "text": "Wrapped Track" }] },
                                    "menu": {
                                      "menuRenderer": {
                                        "items": [
                                          {
                                            "toggleMenuServiceItemRenderer": {
                                              "defaultIcon": { "iconType": "LIBRARY_ADD" },
                                              "toggledIcon": { "iconType": "LIBRARY_REMOVE" },
                                              "isToggled": false,
                                              "defaultServiceEndpoint": {
                                                "commandExecutorCommand": {
                                                  "commands": [
                                                    { "feedbackEndpoint": { "feedbackToken": "EXEC_ADD_TOK" } }
                                                  ]
                                                }
                                              },
                                              "toggledServiceEndpoint": {
                                                "commandExecutorCommand": {
                                                  "commands": [
                                                    { "feedbackEndpoint": { "feedbackToken": "EXEC_REM_TOK" } }
                                                  ]
                                                }
                                              }
                                            }
                                          }
                                        ]
                                      }
                                    }
                                  }
                                },
                                {
                                  "playlistPanelVideoRenderer": {
                                    "videoId": "vidLibRemove",
                                    "title": { "runs": [{ "text": "Saved Track" }] },
                                    "menu": {
                                      "menuRenderer": {
                                        "items": [
                                          {
                                            "toggleMenuServiceItemRenderer": {
                                              "defaultIcon": { "iconType": "LIBRARY_REMOVE" },
                                              "toggledIcon": { "iconType": "LIBRARY_ADD" },
                                              "isToggled": false,
                                              "defaultServiceEndpoint": { "feedbackEndpoint": { "feedbackToken": "REM_FIRST_TOK" } },
                                              "toggledServiceEndpoint": { "feedbackEndpoint": { "feedbackToken": "ADD_SECOND_TOK" } }
                                            }
                                          }
                                        ]
                                      }
                                    }
                                  }
                                }
                              ]
                            }
                          }
                        }
                      }
                    }
                  }]
                }
              }
            }
          }
        }
        """.trimIndent()

        val nextPage = InnerTubeParsers.parseNextResponse(json.parseToJsonElement(wrappedAndRemoveFixture).jsonObject)
        assertEquals(2, nextPage.tracks.size)
        val wrapped = nextPage.tracks[0]
        assertFalse(wrapped.inLibrary)
        assertEquals("EXEC_ADD_TOK", wrapped.feedbackTokenAdd)
        assertEquals("EXEC_REM_TOK", wrapped.feedbackTokenRemove)

        val saved = nextPage.tracks[1]
        assertTrue(saved.inLibrary)
        assertEquals("REM_FIRST_TOK", saved.feedbackTokenRemove)
        assertEquals("ADD_SECOND_TOK", saved.feedbackTokenAdd)

        val serviceItemFixture = """
        {
          "contents": {
            "singleColumnBrowseResultsRenderer": {
              "tabs": [{
                "tabRenderer": {
                  "content": {
                    "sectionListRenderer": {
                      "contents": [{
                        "musicShelfRenderer": {
                          "contents": [{
                            "musicResponsiveListItemRenderer": {
                              "playlistItemData": { "videoId": "vidSvc" },
                              "flexColumns": [
                                { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Direct Add" }] } } }
                              ],
                              "menu": {
                                "menuRenderer": {
                                  "items": [
                                    {
                                      "menuServiceItemRenderer": {
                                        "icon": { "iconType": "LIBRARY_ADD" },
                                        "text": { "runs": [{ "text": "Save to library" }] },
                                        "serviceEndpoint": { "feedbackEndpoint": { "feedbackToken": "SVC_ADD_TOK" } }
                                      }
                                    }
                                  ]
                                }
                              }
                            }
                          }]
                        }
                      }]
                    }
                  }
                }
              }]
            }
          }
        }
        """.trimIndent()
        val (shelfTracks, _) = InnerTubeParsers.parseTracksFromBrowseShelf(json.parseToJsonElement(serviceItemFixture).jsonObject)
        assertEquals(1, shelfTracks.size)
        assertEquals("SVC_ADD_TOK", shelfTracks.first().feedbackTokenAdd)
    }

    @Test
    fun `parseNextResponse unwraps playlistPanelVideoWrapperRenderer picking ATV over OMV and deduplicating mix videos`() {
        val wrapperFixture = """
        {
          "contents": {
            "singleColumnMusicWatchNextResultsRenderer": {
              "tabbedRenderer": {
                "watchNextTabbedResultsRenderer": {
                  "tabs": [{
                    "tabRenderer": {
                      "title": "Up next",
                      "content": {
                        "musicQueueRenderer": {
                          "content": {
                            "playlistPanelRenderer": {
                              "playlistId": "RDAMVM1",
                              "contents": [
                                {
                                  "playlistPanelVideoWrapperRenderer": {
                                    "primaryRenderer": {
                                      "playlistPanelVideoRenderer": {
                                        "videoId": "atv_primary",
                                        "selected": true,
                                        "title": { "runs": [{ "text": "Starlight" }] },
                                        "longBylineText": { "runs": [{ "text": "Muse" }] },
                                        "navigationEndpoint": {
                                          "watchEndpoint": {
                                            "videoId": "atv_primary",
                                            "watchEndpointMusicSupportedConfigs": {
                                              "watchEndpointMusicConfig": { "musicVideoType": "MUSIC_VIDEO_TYPE_ATV" }
                                            }
                                          }
                                        }
                                      }
                                    },
                                    "counterpart": [{
                                      "counterpartRenderer": {
                                        "playlistPanelVideoRenderer": {
                                          "videoId": "omv_counterpart",
                                          "title": { "runs": [{ "text": "Starlight" }] },
                                          "longBylineText": { "runs": [{ "text": "Muse" }] },
                                          "navigationEndpoint": {
                                            "watchEndpoint": {
                                              "videoId": "omv_counterpart",
                                              "watchEndpointMusicSupportedConfigs": {
                                                "watchEndpointMusicConfig": { "musicVideoType": "MUSIC_VIDEO_TYPE_OMV" }
                                              }
                                            }
                                          }
                                        }
                                      }
                                    }]
                                  }
                                },
                                {
                                  "playlistPanelVideoWrapperRenderer": {
                                    "primaryRenderer": {
                                      "playlistPanelVideoRenderer": {
                                        "videoId": "omv_primary",
                                        "title": { "runs": [{ "text": "Hysteria (Official Music Video)" }] },
                                        "longBylineText": { "runs": [{ "text": "Muse" }] },
                                        "navigationEndpoint": {
                                          "watchEndpoint": {
                                            "videoId": "omv_primary",
                                            "watchEndpointMusicSupportedConfigs": {
                                              "watchEndpointMusicConfig": { "musicVideoType": "MUSIC_VIDEO_TYPE_OMV" }
                                            }
                                          }
                                        }
                                      }
                                    },
                                    "counterpart": [{
                                      "counterpartRenderer": {
                                        "playlistPanelVideoRenderer": {
                                          "videoId": "atv_counterpart",
                                          "title": { "runs": [{ "text": "Hysteria" }] },
                                          "longBylineText": { "runs": [{ "text": "Muse" }] },
                                          "navigationEndpoint": {
                                            "watchEndpoint": {
                                              "videoId": "atv_counterpart",
                                              "watchEndpointMusicSupportedConfigs": {
                                                "watchEndpointMusicConfig": { "musicVideoType": "MUSIC_VIDEO_TYPE_ATV" }
                                              }
                                            }
                                          }
                                        }
                                      }
                                    }]
                                  }
                                }
                              ]
                            }
                          }
                        }
                      }
                    }
                  }]
                }
              }
            }
          }
        }
        """.trimIndent()

        val page = InnerTubeParsers.parseNextResponse(json.parseToJsonElement(wrapperFixture).jsonObject)
        assertEquals(listOf("atv_primary", "atv_counterpart"), page.tracks.map { it.videoId })

        val sample = listOf(
            Track(videoId = "v1", title = "Blinding Lights", artists = listOf(ArtistRef(name = "The Weeknd"))),
            Track(videoId = "v2", title = "Blinding Lights (Official Music Video)", artists = listOf(ArtistRef(name = "The Weeknd"))),
            Track(videoId = "v3", title = "Save Your Tears (Audio)", artists = listOf(ArtistRef(name = "The Weeknd"))),
            Track(videoId = "v4", title = "Save Your Tears", artists = listOf(ArtistRef(name = "The Weeknd")))
        )
        val deduped = InnerTubeParsers.deduplicateMixTracks(sample)
        assertEquals(2, deduped.size)
        assertEquals("v1", deduped[0].videoId)
        assertTrue(deduped.none { it.videoId == "v2" })
    }

    @Test
    fun `parsePlaylistResponse extracts shelf continuationCommand over sectionList nextContinuationData and ignores suggested shelf tracks`() {
        val fixture = """
        {
          "header": {
            "musicResponsiveHeaderRenderer": {
              "title": { "runs": [{ "text": "Giant Playlist" }] },
              "subtitle": { "runs": [{ "text": "Playlist" }] }
            }
          },
          "contents": {
            "twoColumnBrowseResultsRenderer": {
              "secondaryContents": {
                "sectionListRenderer": {
                  "continuations": [
                    { "nextContinuationData": { "continuation": "RELATED_SHELF_TOKEN" } }
                  ],
                  "contents": [
                    {
                      "musicPlaylistShelfRenderer": {
                        "contents": [
                          {
                            "musicResponsiveListItemRenderer": {
                              "playlistItemData": { "videoId": "plTrack1" },
                              "flexColumns": [
                                { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Track 1" }] } } }
                              ]
                            }
                          },
                          {
                            "continuationItemRenderer": {
                              "continuationEndpoint": {
                                "continuationCommand": { "token": "PLAYLIST_PAGE_2" }
                              }
                            }
                          }
                        ]
                      }
                    },
                    {
                      "musicShelfRenderer": {
                        "contents": [
                          {
                            "musicResponsiveListItemRenderer": {
                              "playlistItemData": { "videoId": "suggestedShouldIgnore" },
                              "flexColumns": [
                                { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Suggested Track" }] } } }
                              ]
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
              }
            }
          }
        }
        """.trimIndent()

        val detail = InnerTubeParsers.parsePlaylistResponse(json.parseToJsonElement(fixture).jsonObject, "VLPL_GIANT")
        assertEquals(listOf("plTrack1"), detail.tracks.map { it.videoId })
        assertEquals("PLAYLIST_PAGE_2", detail.continuation)
    }

    @Test
    fun `parsePlaylistResponse parses appendContinuationItemsAction pages and stops when no token`() {
        val page2Fixture = """
        {
          "onResponseReceivedActions": [
            {
              "appendContinuationItemsAction": {
                "continuationItems": [
                  {
                    "musicResponsiveListItemRenderer": {
                      "playlistItemData": { "videoId": "contTrack101" },
                      "flexColumns": [
                        { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Track 101" }] } } }
                      ]
                    }
                  },
                  {
                    "musicResponsiveListItemRenderer": {
                      "playlistItemData": { "videoId": "contTrack102" },
                      "flexColumns": [
                        { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Track 102" }] } } }
                      ]
                    }
                  },
                  {
                    "continuationItemRenderer": {
                      "continuationEndpoint": {
                        "continuationCommand": { "token": "PLAYLIST_PAGE_3" }
                      }
                    }
                  }
                ]
              }
            }
          ]
        }
        """.trimIndent()

        val page2 = InnerTubeParsers.parsePlaylistResponse(json.parseToJsonElement(page2Fixture).jsonObject, "VLPL_GIANT")
        assertEquals(listOf("contTrack101", "contTrack102"), page2.tracks.map { it.videoId })
        assertEquals("PLAYLIST_PAGE_3", page2.continuation)

        val finalPageFixture = """
        {
          "onResponseReceivedActions": [
            {
              "appendContinuationItemsAction": {
                "continuationItems": [
                  {
                    "musicResponsiveListItemRenderer": {
                      "playlistItemData": { "videoId": "contTrack201" },
                      "flexColumns": [
                        { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Track 201" }] } } }
                      ]
                    }
                  }
                ]
              }
            }
          ]
        }
        """.trimIndent()

        val finalPage = InnerTubeParsers.parsePlaylistResponse(json.parseToJsonElement(finalPageFixture).jsonObject, "VLPL_GIANT")
        assertEquals(listOf("contTrack201"), finalPage.tracks.map { it.videoId })
        assertEquals(null, finalPage.continuation)
    }

    @Test
    fun `parseAlbumResponse extracts shelf continuation and applies album fallbacks on continuation pages`() {
        val page1Fixture = """
        {
          "header": {
            "musicResponsiveHeaderRenderer": {
              "title": { "runs": [{ "text": "Anthology Box Set" }] },
              "straplineTextOne": {
                "runs": [
                  {
                    "text": "Orchestra",
                    "navigationEndpoint": { "browseEndpoint": { "browseId": "UC_ORCH" } }
                  }
                ]
              }
            }
          },
          "contents": {
            "twoColumnBrowseResultsRenderer": {
              "secondaryContents": {
                "sectionListRenderer": {
                  "continuations": [
                    { "nextContinuationData": { "continuation": "OTHER_VERSIONS_SHELF" } }
                  ],
                  "contents": [
                    {
                      "musicShelfRenderer": {
                        "contents": [
                          {
                            "musicResponsiveListItemRenderer": {
                              "playlistItemData": { "videoId": "alb1" },
                              "flexColumns": [
                                { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Movement 1" }] } } }
                              ]
                            }
                          },
                          {
                            "continuationItemRenderer": {
                              "continuationEndpoint": {
                                "continuationCommand": { "token": "ALBUM_PAGE_2" }
                              }
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
              }
            }
          }
        }
        """.trimIndent()

        val page1 = InnerTubeParsers.parseAlbumResponse(json.parseToJsonElement(page1Fixture).jsonObject, "MPREb_box1")
        assertEquals("ALBUM_PAGE_2", page1.continuation)
        assertEquals(listOf("alb1"), page1.tracks.map { it.videoId })

        val page2Fixture = """
        {
          "onResponseReceivedActions": [
            {
              "appendContinuationItemsAction": {
                "continuationItems": [
                  {
                    "musicResponsiveListItemRenderer": {
                      "playlistItemData": { "videoId": "alb101" },
                      "flexColumns": [
                        { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Movement 101" }] } } }
                      ]
                    }
                  }
                ]
              }
            }
          ]
        }
        """.trimIndent()

        val page2 = InnerTubeParsers.parseAlbumResponse(
            root = json.parseToJsonElement(page2Fixture).jsonObject,
            browseId = "MPREb_box1",
            fallbackAlbum = com.lin.music.core.model.AlbumRef("MPREb_box1", page1.title),
            fallbackArtists = page1.artists,
            fallbackThumb = "https://example.com/box.jpg"
        )
        assertEquals(null, page2.continuation)
        assertEquals(1, page2.tracks.size)
        val t = page2.tracks.first()
        assertEquals("alb101", t.videoId)
        assertEquals("MPREb_box1", t.album?.id)
        assertEquals("Anthology Box Set", t.album?.title)
        assertEquals("Orchestra", t.artistDisplay)
        assertEquals("https://example.com/box.jpg", t.thumbnailUrl)
    }
}
