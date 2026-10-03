package com.arn.scrobble.api.lastfm

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class ScrobbleData(
    val artist: String,
    val track: String,
    val album: String?,
    val timestamp: Long,
    val trackNumber: Int? = null,
    val albumArtist: String?,
    val duration: Long?,
    val appId: String?,
) {
    @androidx.room3.Ignore
    @kotlinx.serialization.Transient
    var artUrl: String? = null

    constructor(
        artist: String,
        track: String,
        album: String?,
        timestamp: Long,
        trackNumber: Int? = null,
        albumArtist: String?,
        duration: Long?,
        appId: String?,
        artUrl: String? = null,
    ) : this(
        artist = artist,
        track = track,
        album = album,
        timestamp = timestamp,
        trackNumber = trackNumber,
        albumArtist = albumArtist,
        duration = duration,
        appId = appId,
    ) {
        this.artUrl = artUrl
    }

    fun safeDuration() = duration?.takeIf { it in (30_000..3600_000) }

    fun toTrack() = Track(
        name = track,
        artist = Artist(artist),
        date = timestamp,
        album = album?.ifEmpty { null }
            ?.let { Album(album, Artist(albumArtist.orEmpty().ifEmpty { artist })) },
        duration = duration,
        artUrl = artUrl,
    )

    fun trimmed() = copy(
        artist = artist.trim(),
        track = track.trim(),
        album = album?.trim()?.ifEmpty { null },
        albumArtist = albumArtist?.trim()?.ifEmpty { null },
    )
}