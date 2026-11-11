package app.stak.nativeapp.data

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName

/**
 * One media shape shared by Home, Discover, Watchlist, Library, Tracking and Title.
 * Keeping a single model avoids the previous per-screen DTO drift.
 */
@Immutable
data class MediaItem(
    val id: String,
    val imdbId: String? = null,
    val type: String = "movie", // "movie" | "series"
    val name: String,
    val year: String? = null,
    val runtime: String? = null,
    val overview: String? = null,
    val poster: String? = null,
    val background: String? = null,
    val rating: Double? = null,
    val tmdbRating: Double? = null,
    val genres: List<String> = emptyList(),
    val releaseInfo: String? = null,
    val progress: Float = 0f,
    val watched: Boolean = false,
    val badge: String? = null,
    val section: String? = null,
    val jellyfinId: String? = null,
    val aliases: List<String> = emptyList(),
    val season: Int? = null,
    val episode: Int? = null,
    val jellyfinSeriesId: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val nextUp: Boolean = false,
    val addedAt: Long = 0,
    val jellyfinProviderKeys:List<String>? = emptyList(),
    val jellyfinPresentationKey:String?=null,
    val jellyfinVersionIds:List<String>? = emptyList(),
    val jellyfinVersionLabel:String?=null,
    val lastPlayedAt:Long=0,
    val logo:String?=null,
    val certification:String?=null,
    val jellyfinUserDataKnown:Boolean?=null,
    val jellyfinFavorite:Boolean?=null,
    val jellyfinVideoHeight:Int?=null,
    val jellyfinVideoCodec:String?=null,
    val jellyfinHdr:String?=null,
    val jellyfinUnplayedCount:Int?=null,
    val jellyfinKnownItemCount:Int?=null,
    val cinematicArtwork:String?=null
) {
    val mediaKey: String get() = imdbId ?: id
    val isSeries: Boolean get() = type.equals("series", ignoreCase = true)
}

@Immutable
data class Episode(
    val season: Int,
    val episode: Int,
    val name: String,
    val overview: String? = null,
    val image: String? = null,
    val runtime: String? = null,
    val progress: Float = 0f,
    val watched: Boolean = false,
    val released: String? = null,
    val imdbRating: Double? = null
)

fun MediaItem.sameIdentity(other: MediaItem): Boolean = type == other.type && (mediaKey == other.mediaKey || id == other.id)

@Immutable
data class SourceRelease(
    val name: String,
    val resolution: String = "1080p",
    val size: String? = null,
    val seeders: Int = 0,
    val cached: Boolean = false,
    val hdr: Boolean = false,
    val atmos: Boolean = false,
    val remux: Boolean = false,
    val webdl: Boolean = false,
    val indexer: String? = null,
    val infoHash: String? = null,
    val streamUrl: String? = null,
    val fileIndex: Int? = null,
    val sizeBytes: Long? = null,
    val dolbyVision: Boolean = false,
    val languages: List<String> = emptyList(),
    val torrentSizeBytes:Long?=null,
    val releaseText:String?=null,
    val torznabIndexerId:String?=null,
    val torrentFileUrl:String?=null,
    val magnetUrl:String?=null,
    val sourceToken:String?=null,
    val addonName:String?=null
)

@Immutable
data class TitleDetails(
    val item: MediaItem,
    val genres: List<String> = emptyList(),
    val certification: String? = null,
    val imdbRating: Double? = null,
    val tmdbRating: Double? = null,
    val episodes: List<Episode> = emptyList(),
    val sources: List<SourceRelease> = emptyList(),
    val seasons: List<Int> = emptyList(),
    val notice: String? = null,
    val trailerUrl: String? = null,
    val gallery: List<String> = emptyList(),
    val cast: List<TitlePerson> = emptyList(),
    val crew: List<TitlePerson> = emptyList(),
    val collection: List<MediaItem> = emptyList(),
    val related: List<MediaItem> = emptyList(),
    val logo: String? = null,
    val status: String? = null,
    val rottenTomatoes: String? = null,
    val metacritic: String? = null
)

@Immutable data class TitlePerson(val id: String, val name: String, val role: String, val photo: String? = null)

@Immutable
data class HomeFeed(
    val hero: List<MediaItem> = emptyList(),
    val continueWatching: List<MediaItem> = emptyList(),
    val rows: List<HomeRow> = emptyList(),
    val source: String = "local"
)

@Immutable
data class HomeRow(
    val id: String,
    val title: String,
    val items: List<MediaItem>,
    val error: String? = null,
    val provider:String? = null
)

@Immutable
data class DiscoverResult(
    val items: List<MediaItem> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 1,
    val source: String = "local",
    val notice: String? = null,
    val ratingErrors: Int = 0,
    val verifiedImdb: Boolean = false,
    val nextTypePages: Map<String,Int>? = null,
    val partialErrors: List<String> = emptyList()
)

// ---------- API DTOs ----------

data class DiscoverResponse(
    @SerializedName("items") val items: List<MediaDto> = emptyList(),
    @SerializedName("page") val page: Int = 1,
    @SerializedName("total_pages") val totalPages: Int = 1
)

data class SearchResponse(
    @SerializedName("items") val items: List<MediaDto> = emptyList(),
    @SerializedName("next_cursor") val nextCursor: Any? = null
)

data class MediaDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("imdb_id") val imdbId: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("year") val year: String? = null,
    @SerializedName("releaseInfo") val releaseInfo: String? = null,
    @SerializedName("runtime") val runtime: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("overview") val overview: String? = null,
    @SerializedName("poster") val poster: String? = null,
    @SerializedName("background") val background: String? = null,
    @SerializedName("tmdbRating") val tmdbRating: Double? = null,
    @SerializedName("imdbRating") val imdbRating: Double? = null,
    @SerializedName("genres") val genres: Any? = null
)

data class TitleDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("imdb_id") val imdbId: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("year") val year: String? = null,
    @SerializedName("releaseInfo") val releaseInfo: String? = null,
    @SerializedName("runtime") val runtime: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("overview") val overview: String? = null,
    @SerializedName("poster") val poster: String? = null,
    @SerializedName("background") val background: String? = null,
    @SerializedName("imdbRating") val imdbRating: Double? = null,
    @SerializedName("tmdbRating") val tmdbRating: Double? = null,
    @SerializedName("genres") val genres: Any? = null,
    @SerializedName("certification") val certification: String? = null,
    @SerializedName("videos") val videos: List<VideoDto> = emptyList()
)

data class VideoDto(
    @SerializedName("season") val season: Int? = null,
    @SerializedName("episode") val episode: Int? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("overview") val overview: String? = null,
    @SerializedName("thumbnail") val thumbnail: String? = null,
    @SerializedName("runtime") val runtime: String? = null
)

data class TrackingHistoryDto(
    @SerializedName("continue") val continueItems: List<TrackingDto> = emptyList(),
    @SerializedName("activity") val activity: List<TrackingDto> = emptyList()
)

data class TrackingDto(
    @SerializedName("imdb_id") val imdbId: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("series_name") val seriesName: String? = null,
    @SerializedName("poster") val poster: String? = null,
    @SerializedName("season") val season: Int? = null,
    @SerializedName("episode") val episode: Int? = null,
    @SerializedName("progress") val progress: Double? = null,
    @SerializedName("played") val played: Boolean? = null,
    @SerializedName("last_played") val lastPlayed: String? = null
)

data class JellyfinHomeDto(
    @SerializedName("continue_watching") val continueWatching: List<JellyfinItemDto> = emptyList(),
    @SerializedName("next_up") val nextUp: List<JellyfinItemDto> = emptyList(),
    @SerializedName("libraries") val libraries: List<JellyfinItemDto> = emptyList()
)

data class JellyfinItemDto(
    @SerializedName("Id") val id: String? = null,
    @SerializedName("Name") val name: String? = null,
    @SerializedName("SeriesName") val seriesName: String? = null,
    @SerializedName("Type") val type: String? = null,
    @SerializedName("SeriesId") val seriesId: String? = null,
    @SerializedName("ParentIndexNumber") val season: Int? = null,
    @SerializedName("IndexNumber") val episode: Int? = null,
    @SerializedName("RunTimeTicks") val runtimeTicks: Long? = null,
    @SerializedName("UserData") val userData: JellyfinUserDataDto? = null,
    @SerializedName("ImageTags") val imageTags: Map<String, String>? = null,
    @SerializedName("PrimaryImageAspectRatio") val aspectRatio: Double? = null
)

data class JellyfinUserDataDto(
    @SerializedName("PlaybackPositionTicks") val positionTicks: Long? = null,
    @SerializedName("PlayedPercentage") val playedPercentage: Double? = null
)

data class WatchlistDto(
    @SerializedName("items") val items: List<MediaDto> = emptyList()
)

