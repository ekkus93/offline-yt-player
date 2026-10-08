package com.ekkus.offlineytplayer.ui

import androidx.media3.common.MimeTypes
import com.ekkus.offlineytplayer.playback.LocalSubtitleTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LibraryPlaybackRouteSubtitleTest {
    @Test
    fun persistedSubtitleTracksReachLocalPlaybackAsset() {
        val row = LibraryRowModel(
            id = "item-1",
            title = "Fixture",
            detail = "720p",
            completed = true,
            videoPath = "/library/items/item-1/video.mp4",
            subtitleTracks = listOf(
                LocalSubtitleTrack(
                    path = "/library/items/item-1/subtitles/en.vtt",
                    language = "en",
                    label = "en",
                    mimeType = MimeTypes.TEXT_VTT,
                ),
            ),
        )

        val asset = requireNotNull(LibraryPlaybackRoute.assetFor(row))

        assertEquals(1, asset.subtitleTracks.size)
        assertEquals("en", asset.subtitleTracks.single().language)
        assertEquals("/library/items/item-1/subtitles/en.vtt", asset.subtitleTracks.single().path)
        assertEquals(MimeTypes.TEXT_VTT, asset.subtitleTracks.single().mimeType)
    }
}
