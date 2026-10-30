package app.stak.nativeapp.data

import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @GET("discover/tmdb")
    suspend fun discoverTmdb(
        @Query("type") type: String,
        @Query("category") category: String = "trending",
        @Query("period") period: String = "week",
        @Query("page") page: Int = 1,
        @Query("genre") genre: String? = null,
        @Query("sort") sort: String? = null,
        @Query("region") region: String = "US",
        @Query("provider") provider: String? = null
    ): DiscoverResponse

    @GET("meta/{type}/{id}")
    suspend fun titleDetails(
        @Path("type") type: String,
        @Path("id") id: String
    ): TitleDto

    @GET("metadata/search")
    suspend fun search(
        @Query("q") q: String,
        @Query("type") type: String? = null,
        @Query("page") page: Int = 1
    ): SearchResponse

    @GET("meta/title-logo")
    suspend fun titleLogo(
        @Query("imdb_id") imdbId: String,
        @Query("type") type: String
    ): LogoDto

    @GET("meta/certification")
    suspend fun certification(
        @Query("imdb_id") imdbId: String,
        @Query("type") type: String
    ): CertificationDto

    @GET("tracking/history")
    suspend fun trackingHistory(): TrackingHistoryDto

    @GET("tracking/title/{id}")
    suspend fun trackingTitle(@Path("id") id: String): TrackingTitleDto

    @GET("jellyfin")
    suspend fun jellyfinConfig(): JellyfinConfigDto

    @GET("jellyfin/home")
    suspend fun jellyfinHome(): JellyfinHomeDto

    @GET("watchlist")
    suspend fun watchlist(): WatchlistDto

    @POST("watchlist")
    suspend fun addWatchlist(@Body body: WatchlistBody): WatchlistDto

    @DELETE("watchlist/{id}")
    suspend fun removeWatchlist(@Path("id") id: String): WatchlistDto

    @GET("account")
    suspend fun account(): AccountDto
}

data class LogoDto(val logo: String? = null)
data class CertificationDto(val certification: String? = null)
data class TrackingTitleDto(
    val progress: Double? = null,
    val played: Boolean? = null,
    val videos: List<TrackingVideoDto> = emptyList()
)
data class TrackingVideoDto(
    val season: Int? = null,
    val episode: Int? = null,
    val progress: Double? = null,
    val played: Boolean? = null
)
data class JellyfinConfigDto(val connected: Boolean = false)
data class WatchlistBody(
    val imdb_id: String,
    val type: String,
    val name: String,
    val year: String? = null,
    val poster: String? = null
)
data class AccountDto(
    val name: String? = null,
    val plan: String? = null,
    val connected: Boolean = false
)
