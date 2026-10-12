package com.arn.scrobble.automation

import com.arn.scrobble.BuildKonfig
import com.arn.scrobble.api.lastfm.ScrobbleData
import com.arn.scrobble.media.PlayingTrackInfo
import com.arn.scrobble.media.PlayingTrackNotifyEvent
import com.arn.scrobble.media.parseNowPlayingResult
import com.arn.scrobble.utils.Stuff
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AutomationTest {

    @Test
    fun testAutomationPrefixMatchesAppId() {
        assertEquals("${BuildKonfig.APP_ID}.automation", Automation.PREFIX)
    }

    @Test
    fun testParseNowPlayingResult() {
        // null input
        assertNull(parseNowPlayingResult(null))

        // empty string
        assertNull(parseNowPlayingResult(""))

        // plain text responses like "Failed" or "Ok"
        assertNull(parseNowPlayingResult("Failed"))
        assertNull(parseNowPlayingResult("Ok"))

        // malformed JSON
        assertNull(parseNowPlayingResult("{not valid json}"))
        assertNull(parseNowPlayingResult("{"))

        // valid JSON round-trip
        val scrobbleData = ScrobbleData(
            track = "Track",
            artist = "Artist",
            album = "Album",
            timestamp = 123456789L,
            albumArtist = null,
            duration = 180,
            appId = "com.example.music"
        )
        val trackPlaying = PlayingTrackNotifyEvent.TrackPlaying(
            notiKey = "key_1",
            scrobbleData = scrobbleData,
            msid = "msid_1",
            origScrobbleData = scrobbleData,
            hash = 12345,
            nowPlaying = true,
            userLoved = false,
            artUrlState = PlayingTrackInfo.ArtUrlState(null),
            timelineStartTime = 1000L,
            preprocessed = true
        )

        val json = Stuff.myJson.encodeToString(trackPlaying)
        val parsed = parseNowPlayingResult(json)
        assertNotNull(parsed)
        assertEquals(trackPlaying.notiKey, parsed.notiKey)
        assertEquals(trackPlaying.scrobbleData.track, parsed.scrobbleData.track)
        assertEquals(trackPlaying.scrobbleData.artist, parsed.scrobbleData.artist)
        assertEquals(trackPlaying.hash, parsed.hash)
        assertEquals(trackPlaying.nowPlaying, parsed.nowPlaying)
    }
}
