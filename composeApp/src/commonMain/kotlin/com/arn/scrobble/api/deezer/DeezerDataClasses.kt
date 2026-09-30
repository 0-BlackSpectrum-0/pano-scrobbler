package com.arn.scrobble.api.deezer

import kotlinx.serialization.Serializable

@Serializable
data class DeezerSearchResponse(
    val data: List<DeezerTrack>,
    val total: Int = 0
)

@Serializable
data class DeezerArtistSearchResponse(
    val data: List<DeezerArtist>,
    val total: Int = 0
)

@Serializable
data class DeezerAlbumSearchResponse(
    val data: List<DeezerAlbum>,
    val total: Int = 0
)

@Serializable
data class DeezerTrack(
    val id: Long,
    val title: String,
    val duration: Int = 0,
    val artist: DeezerArtist,
    val album: DeezerAlbum,
    val contributors: List<DeezerArtist>? = null,
    val type: String = "track"
)

@Serializable
data class DeezerArtist(
    val id: Long,
    val name: String,
    val link: String? = null,
    val picture_small: String? = null,
    val picture_medium: String? = null,
    val picture_big: String? = null,
    val picture_xl: String? = null,
    val type: String? = null
) {
    val mediumImageUrl get() = picture_medium ?: picture_big
    val largeImageUrl get() = picture_xl ?: picture_big ?: picture_medium
}

@Serializable
data class DeezerAlbum(
    val id: Long,
    val title: String,
    val link: String? = null,
    val cover_small: String? = null,
    val cover_medium: String? = null,
    val cover_big: String? = null,
    val cover_xl: String? = null,
    val artist: DeezerArtist? = null,
    val type: String? = null
) {
    val mediumImageUrl get() = cover_medium ?: cover_big
    val largeImageUrl get() = cover_xl ?: cover_big ?: cover_medium
}