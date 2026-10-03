package com.arn.scrobble.api.itunes

import kotlinx.serialization.Serializable

@Serializable
data class ItunesSearchResponse(
    val resultCount: Int = 0,
    val results: List<ItunesAlbum> = emptyList()
)

@Serializable
data class ItunesArtistSearchResponse(
    val resultCount: Int = 0,
    val results: List<ItunesArtist> = emptyList()
)

@Serializable
data class ItunesAlbum(
    val collectionId: Long = 0,
    val collectionName: String = "",
    val artistName: String = "",
    val artworkUrl100: String? = null,
    val collectionType: String? = null,
    val wrapperType: String? = null,
) {
    val mediumImageUrl get() = artworkUrl100?.replace("100x100bb", "600x600bb")
    val largeImageUrl get() = artworkUrl100?.replace("100x100bb", "1200x1200bb")
}

@Serializable
data class ItunesArtist(
    val artistId: Long = 0,
    val artistName: String = "",
    val artistLinkUrl: String? = null,
    val wrapperType: String? = null,
)