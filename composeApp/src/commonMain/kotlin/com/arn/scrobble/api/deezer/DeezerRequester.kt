package com.arn.scrobble.api.deezer

import com.arn.scrobble.api.Requesters
import com.arn.scrobble.api.Requesters.getResult
import io.ktor.client.request.parameter

class DeezerRequester {
    private val client get() = Requesters.genericKtorClient

    suspend fun searchTrack(
        artist: String,
        track: String,
        limit: Int
    ) =
        client.getResult<DeezerSearchResponse>("https://api.deezer.com/search") {
            parameter("q", "artist:\"$artist\" track:\"$track\"")
            parameter("order", "RANKING")
            parameter("limit", limit)
        }

    suspend fun lookupTrack(
        trackId: Long,
    ) =
        client.getResult<DeezerTrack>("https://api.deezer.com/track/$trackId")

    suspend fun searchArtist(
        artist: String,
        limit: Int = 3
    ) =
        client.getResult<DeezerArtistSearchResponse>("https://api.deezer.com/search/artist") {
            parameter("q", artist)
            parameter("limit", limit)
        }

    suspend fun searchAlbum(
        artist: String,
        album: String,
        limit: Int = 3
    ) =
        client.getResult<DeezerAlbumSearchResponse>("https://api.deezer.com/search/album") {
            parameter("q", "artist:\"$artist\" album:\"$album\"")
            parameter("limit", limit)
        }

    suspend fun lookupAlbum(
        albumId: Long
    ) =
        client.getResult<DeezerAlbum>("https://api.deezer.com/album/$albumId")

    suspend fun lookupArtist(
        artistId: Long
    ) =
        client.getResult<DeezerArtist>("https://api.deezer.com/artist/$artistId")
}