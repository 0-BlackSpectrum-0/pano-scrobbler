package com.arn.scrobble.media

import com.arn.scrobble.api.lastfm.ScrobbleData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScrobbleQueueTest {

    @Test
    fun testDesktopNeverWaitsForTemporaryApproval() {
        val appId = "com.harmonoid.harmonoid"
        val tempPackages = setOf(appId, "com.spotify.music")
        val approvedHashes = emptySet<Int>()
        val hash = 12345

        // On desktop, it must never require approval even if app is in temporary list
        val desktopNeedsApproval = needsTemporaryApproval(
            isDesktop = true,
            appId = appId,
            temporaryPackages = tempPackages,
            approvedHashes = approvedHashes,
            hash = hash
        )
        assertFalse(desktopNeedsApproval, "Desktop must never gate scrobbles on approval")

        // On non-desktop (Android), it must require approval until approved
        val androidNeedsApproval = needsTemporaryApproval(
            isDesktop = false,
            appId = appId,
            temporaryPackages = tempPackages,
            approvedHashes = approvedHashes,
            hash = hash
        )
        assertTrue(androidNeedsApproval, "Android must require approval for temp apps")

        // After approval, it must not require approval
        val androidApproved = needsTemporaryApproval(
            isDesktop = false,
            appId = appId,
            temporaryPackages = tempPackages,
            approvedHashes = setOf(hash),
            hash = hash
        )
        assertFalse(androidApproved, "Approved scrobble must not require approval again")

        // Non-temporary app never requires approval
        val otherApp = needsTemporaryApproval(
            isDesktop = false,
            appId = "com.other.app",
            temporaryPackages = tempPackages,
            approvedHashes = approvedHashes,
            hash = hash
        )
        assertFalse(otherApp, "Non-temporary app must not require approval")
    }

    @Test
    fun testFailuresAreNotSwallowedAndPersistPending() = runBlocking {
        val scrobbleData = ScrobbleData(
            track = "Track",
            artist = "Artist",
            album = "Album",
            timestamp = 1000L,
            albumArtist = null,
            duration = 180,
            appId = "com.test.player"
        )
        val trackInfo = PlayingTrackInfo("com.test.player", "key_1", null).apply {
            putOriginals(
                artist = "Artist",
                title = "Track",
                album = "Album",
                albumArtist = "",
                trackNumber = 1,
                durationMillis = 180_000L,
                normalizedUrlHost = null,
                artUrl = null
            )
        }

        var persistedError: Throwable? = null
        val expectedException = RuntimeException("Network timeout")

        val result = executeScrobbleSubmission(
            trackInfo = trackInfo,
            scrobbleSd = scrobbleData,
            submitAction = {
                throw expectedException
            },
            persistPendingAction = { error ->
                persistedError = error
            }
        )

        assertFalse(result, "Failed submission should return false")
        assertEquals(expectedException, persistedError, "Failure must be caught and persisted")
    }

    @Test
    fun testCancellationIsRethrown() = runBlocking {
        val scrobbleData = ScrobbleData(
            track = "Track",
            artist = "Artist",
            album = "Album",
            timestamp = 1000L,
            albumArtist = null,
            duration = 180,
            appId = "com.test.player"
        )
        val trackInfo = PlayingTrackInfo("com.test.player", "key_1", null).apply {
            putOriginals(
                artist = "Artist",
                title = "Track",
                album = "Album",
                albumArtist = "",
                trackNumber = 1,
                durationMillis = 180_000L,
                normalizedUrlHost = null,
                artUrl = null
            )
        }

        var persistedCalled = false
        var cancellationThrown = false

        try {
            executeScrobbleSubmission(
                trackInfo = trackInfo,
                scrobbleSd = scrobbleData,
                submitAction = {
                    throw CancellationException("Job cancelled")
                },
                persistPendingAction = {
                    persistedCalled = true
                }
            )
        } catch (e: CancellationException) {
            cancellationThrown = true
        }

        assertTrue(cancellationThrown, "CancellationException must be rethrown")
        assertFalse(persistedCalled, "Cancellation must not persist pending scrobble")
    }
}
