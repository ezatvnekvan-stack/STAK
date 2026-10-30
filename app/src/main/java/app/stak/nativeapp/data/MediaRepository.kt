package app.stak.nativeapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Native equivalent of the Capacitor direct-provider catalog path. */
class MediaRepository(private val catalog: DirectCatalog = DirectCatalog(preferCinemeta = { type -> (if (type == "series") NativeRuntime.settings.seriesMetadata else NativeRuntime.settings.movieMetadata) == "cinemeta" }), private val discovery: DirectDiscovery = DirectDiscovery(), private val trakt:TraktCatalog=TraktCatalog()) {
    suspend fun home(): HomeFeed = withContext(Dispatchers.IO) {
        coroutineScope {
            val definitions = HomeSections.read(NativeRuntime.settings.homeRowsJson)
                .filter { it.enabled && it.id !in listOf("continue", "watchlist", "coming_up") }
            val rows = definitions.map { section -> async {
                try {
                    val fromTrakt=section.category in listOf("trending","anticipated")
                    val result=if(fromTrakt)trakt.list(section.type,section.category,limit=10)else catalog.discover(section.type,section.category,1,section.year)
                    HomeRow(section.id,section.title,result.items.take(20),provider=if(fromTrakt)"Trakt"else null)
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
                } catch (error: Exception) {
                    val fromTrakt=section.category in listOf("trending","anticipated")
                    HomeRow(section.id,section.title,emptyList(),if(fromTrakt)error.message ?: "Trakt could not load. Please retry."else "Unable to load titles. Check your connection and retry.",if(fromTrakt)"Trakt"else null)
                }
            }}
            val hero = if (!NativeRuntime.settings.tmdbKey.isNullOrBlank()) {
                listOf("movie", "series").map { type -> async {
                    try { catalog.discover(type, "trending", 1).items.take(3) }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { emptyList<MediaItem>() }
                }}.map { it.await() }.let { lists -> (0..2).flatMap { i -> lists.mapNotNull { it.getOrNull(i) } } }
            } else emptyList()
            val permits=kotlinx.coroutines.sync.Semaphore(3)
            val brandedHero=hero.map{item->async{permits.acquire();try{catalog.heroMetadata(item)}catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}catch(_:Exception){item.copy(rating=null)}finally{permits.release()}}}.map{it.await()}
            HomeFeed(hero = brandedHero, rows = rows.map { it.await() }, source = "direct")
        }
    }

    suspend fun discover(type: String, category: String, query: String, page: Int, year: Int? = null, requireTmdb: Boolean = false, filters: DiscoveryFilters = DiscoveryFilters(), region: String = "US", cursor: Map<String,Int>? = null): DiscoverResult {
        if(!requireTmdb&&query.isBlank()&&category in listOf("trakt_trending","anticipated"))return trakt.list(type,if(category=="anticipated")"anticipated"else "trending",page)
        filters.validationError()?.let { throw IllegalArgumentException(it) }
        val configured = !NativeRuntime.settings.tmdbKey.isNullOrBlank()
        if (query.isNotBlank()) {
            return if (configured) discovery.search("all", query, page, filters, region, cursor)
                else discovery.filterFallback(catalog.search(query, page, "all"), filters)
        }
        if (requireTmdb || configured) {
            val selected = if (year == null) filters else filters.copy(yearFrom = year.toString(), yearTo = year.toString())
            return discovery.discover(if (type == "all") "movie" else type, if (year != null) "popular" else category, page, selected, region)
        }
        return discovery.filterFallback(catalog.discover(if (type == "all") "movie" else type, category, page, year), filters).copy(notice = "TMDb key missing. Browsing uses Cinemeta.")
    }

    suspend fun discoveryGenres(type: String) = discovery.genres(type)
    suspend fun discoveryProviders(type: String, region: String) = discovery.providers(type, region)
    suspend fun discoveryRegions() = discovery.regions()

    private val ratings = DirectRatings()
    suspend fun details(type: String, id: String): TitleDetails {
        val base = if(id.startsWith("jellyfin:")) {
            val itemId=id.substringAfter(':');require(itemId.matches(Regex("[A-Za-z0-9-]+")))
            val settings=NativeRuntime.settings
            val data=DirectJellyfin.sessionRequest("/Users/${settings.jellyfinUserId}/Items/$itemId")
            val local=jellyfinMediaItem(data,DirectJellyfin.normalizeServer(settings.jellyfinUrl.orEmpty()))
            val provider=data.objectOrNull("ProviderIds")
            val identity=provider?.text("Imdb")?.takeIf{it.matches(Regex("tt\\d+"))} ?: provider?.text("Tmdb")?.takeIf{it.matches(Regex("\\d+"))}?.let{"tmdb:$it"}
            if(identity!=null)try {catalog.details(type,identity)}catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}catch(_:Exception){TitleDetails(local,notice="Showing Jellyfin metadata. Additional provider metadata could not load.")}
            else TitleDetails(local)
        } else catalog.details(type, id)
        val imdb = base.item.imdbId
        if (!NativeRuntime.settings.ratingsEnabled || NativeRuntime.settings.omdbKey.isNullOrBlank() || imdb == null) return base
        return try {
            val scores = ratings.get(imdb)
            base.copy(imdbRating = scores.imdb, rottenTomatoes = scores.rottenTomatoes, metacritic = scores.metacritic)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { base.copy(notice = listOfNotNull(base.notice, "OMDb ratings could not load. Retry metadata.").joinToString(" ")) }
    }
    suspend fun episodeRating(imdb: String, episode: Episode): Double? = if (NativeRuntime.settings.ratingsEnabled && !NativeRuntime.settings.omdbKey.isNullOrBlank()) ratings.get(imdb, episode.season, episode.episode).imdb else null

    // Tracking and watchlist are observed from Room in StakApp, never filled with demo records.
    suspend fun history(): List<MediaItem> = emptyList()
    suspend fun jellyfinLibrary(onPage:suspend(List<MediaItem>,Int?)->Unit={_,_->}): List<MediaItem> = DirectJellyfin.library(NativeRuntime.settings,onPage=onPage)
    suspend fun watchlist(): List<MediaItem> = emptyList()
    suspend fun search(query: String, type: String = "all"): List<MediaItem> = catalog.search(query, type = type).items
}

// ---------- DTO mapping ----------

private fun Any?.genreNames(): List<String> = when (this) {
    null -> emptyList()
    is String -> split(',').map { it.trim() }.filter { it.isNotEmpty() }
    is List<*> -> mapNotNull { entry ->
        when (entry) {
            is String -> entry
            is Map<*, *> -> (entry["name"] as? String) ?: (entry["Name"] as? String)
            else -> null
        }
    }
    else -> emptyList()
}

fun MediaDto.toMedia(fallbackType: String = "movie"): MediaItem {
    val safeType = type ?: fallbackType
    return MediaItem(
        id = id ?: imdbId ?: name.orEmpty(),
        imdbId = imdbId,
        type = safeType,
        name = name ?: "Unknown",
        year = year ?: releaseInfo?.take(4),
        runtime = runtime,
        overview = overview ?: description,
        poster = poster,
        background = background ?: poster,
        rating = imdbRating,
        tmdbRating = tmdbRating,
        genres = genres.genreNames(),
        releaseInfo = releaseInfo ?: year
    )
}

fun TitleDto.toMedia(fallbackType: String = "movie"): MediaItem {
    val safeType = type ?: fallbackType
    return MediaItem(
        id = id ?: imdbId ?: name.orEmpty(),
        imdbId = imdbId,
        type = safeType,
        name = name ?: "Unknown",
        year = year ?: releaseInfo?.take(4),
        runtime = runtime,
        overview = overview ?: description,
        poster = poster,
        background = background ?: poster,
        rating = imdbRating,
        tmdbRating = tmdbRating,
        genres = genres.genreNames(),
        releaseInfo = releaseInfo ?: year
    )
}

fun TrackingDto.toMedia(): MediaItem = MediaItem(
    id = imdbId ?: name.orEmpty(),
    imdbId = imdbId,
    type = if (type == "movie") "movie" else "series",
    name = seriesName ?: name ?: "Unknown",
    poster = poster,
    background = poster,
    progress = ((progress ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f),
    watched = played == true,
    releaseInfo = if (type == "episode") "S${season ?: 1}E${episode ?: 1}" else null
)

fun JellyfinItemDto.toMedia(): MediaItem = MediaItem(
    id = id ?: name.orEmpty(),
    type = if (type == "Series" || type == "Episode") "series" else "movie",
    name = seriesName ?: name ?: "Unknown",
    poster = null,
    background = null,
    progress = (userData?.playedPercentage?.div(100.0) ?: 0.0).toFloat().coerceIn(0f, 1f),
    releaseInfo = if (type == "Episode") "S${season ?: 1}E${episode ?: 1}" else type,
    jellyfinId = id
)
