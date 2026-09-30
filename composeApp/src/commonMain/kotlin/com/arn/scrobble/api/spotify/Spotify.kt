package com.arn.scrobble.api.spotify

import com.arn.scrobble.api.CustomCachePlugin
import com.arn.scrobble.api.Requesters
import com.arn.scrobble.api.Requesters.getResult
import com.arn.scrobble.api.Requesters.parseJsonBody
import com.arn.scrobble.api.cache.ExpirationPolicy
import com.arn.scrobble.api.invalidatableLazy
import com.arn.scrobble.utils.PlatformStuff
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.parameters
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class SpotifyRequester {
    private val _client = invalidatableLazy {
        Requesters.genericKtorClient.config {
            install(CustomCachePlugin) {
                policy = SpotifyCacheExpirationPolicy()
            }

            install(Auth) {
                bearer {
                    loadTokens {
                        val refreshToken = runCatching { getRefreshToken() }.getOrNull()
                            ?: return@loadTokens null
                        BearerTokens(
                            "null",
                            refreshToken
                        )
                    }

                    refreshTokens {
                        val refreshToken = runCatching { getRefreshToken() }.getOrNull()
                            ?: return@refreshTokens null
                        val tokenResponse = client.submitForm(
                            url = "https://accounts.spotify.com/api/token",
                            formParameters = parameters {
                                append("grant_type", "client_credentials")
                            }
                        ) {
                            markAsRefreshTokenRequest()

                            header(
                                HttpHeaders.Authorization,
                                "Basic $refreshToken"
                            )
                        }.parseJsonBody<SpotifyTokenResponse>()

                        BearerTokens(
                            tokenResponse.access_token,
                            refreshToken
                        )
                    }

                    sendWithoutRequest {
                        when (it.url.host) {
                            "accounts.spotify.com" -> false
                            else -> true
                        }
                    }
                }
            }
        }
    }

    private val client by _client

    fun invalidateClient() = _client.invalidate()

    suspend fun search(
        query: String,
        type: SpotifySearchType,
        market: String = "US",
        limit: Int = 10, // 10 is the max now
    ): Result<SpotifySearchResponse> {
        if (!hasCredentials()) return Result.failure(IllegalStateException("Spotify credentials not configured"))
        return client.getResult("https://api.spotify.com/v1/search") {
            parameter("q", query)
            parameter("type", type.name)
            parameter("limit", limit)
            parameter("market", market)
        }
    }

    suspend fun artist(
        artistId: String,
        market: String = "US"
    ): Result<ArtistItem> {
        if (!hasCredentials()) return Result.failure(IllegalStateException("Spotify credentials not configured"))
        return client.getResult("https://api.spotify.com/v1/artists/$artistId") {
            parameter("market", market)
        }
    }

    suspend fun album(
        albumId: String,
        market: String = "US"
    ): Result<AlbumItem> {
        if (!hasCredentials()) return Result.failure(IllegalStateException("Spotify credentials not configured"))
        return client.getResult("https://api.spotify.com/v1/albums/$albumId") {
            parameter("market", market)
        }
    }

    suspend fun track(
        trackId: String,
        market: String = "US"
    ): Result<TrackItem> {
        if (!hasCredentials()) return Result.failure(IllegalStateException("Spotify credentials not configured"))
        return client.getResult("https://api.spotify.com/v1/tracks/$trackId") {
            parameter("market", market)
        }
    }

    companion object {
        @OptIn(ExperimentalEncodingApi::class)
        suspend fun getRefreshToken(): String {
            val prefs = PlatformStuff.mainPrefs.data.first()
            val clientId = prefs.spotifyClientId.trim()
            val clientSecret = prefs.spotifyClientSecret.trim()
            if (clientId.isNotBlank()) {
                return if (clientSecret.isNotBlank()) {
                    Base64.Default.encode("$clientId:$clientSecret".encodeToByteArray())
                } else {
                    clientId
                }
            }
            throw IllegalStateException("Spotify credentials not configured")
        }

        suspend fun hasCredentials(): Boolean {
            val prefs = PlatformStuff.mainPrefs.data.first()
            return prefs.spotifyClientId.isNotBlank()
        }
    }
}

class SpotifyCacheExpirationPolicy : ExpirationPolicy {

    private val ONE_MONTH = TimeUnit.DAYS.toMillis(30)
    private val ONE_YEAR = TimeUnit.DAYS.toMillis(365)

    override fun getExpirationTime(url: Url) = when {
        url.segments.last() == "search" -> ONE_MONTH
        url.segments[url.segments.size - 2] == "audio-features" -> ONE_YEAR
        url.segments[url.segments.size - 2] == "artists" -> ONE_MONTH // artist images can change
        url.segments[url.segments.size - 2] == "albums" -> ONE_YEAR // album arts usually don't change
        else -> -1
    }

}