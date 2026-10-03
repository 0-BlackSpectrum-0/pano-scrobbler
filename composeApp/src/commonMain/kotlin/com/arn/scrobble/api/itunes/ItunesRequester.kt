package com.arn.scrobble.api.itunes

import com.arn.scrobble.api.Requesters
import com.arn.scrobble.api.Requesters.getResult
import io.ktor.client.request.parameter

class ItunesRequester {
    private val client get() = Requesters.genericKtorClient

    suspend fun searchAlbum(
        artist: String,
        album: String,
        limit: Int = 3
    ) =
        client.getResult<ItunesSearchResponse>("https://itunes.apple.com/search") {
            parameter("term", "$artist $album")
            parameter("entity", "album")
            parameter("limit", limit)
        }

    suspend fun searchArtist(
        artist: String,
        limit: Int = 3
    ) =
        client.getResult<ItunesArtistSearchResponse>("https://itunes.apple.com/search") {
            parameter("term", artist)
            parameter("entity", "musicArtist")
            parameter("limit", limit)
        }
}