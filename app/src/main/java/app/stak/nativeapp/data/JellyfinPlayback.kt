package app.stak.nativeapp.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun jellyfinMediaItem(item: JsonObject, server: String): MediaItem {
    val id = item.text("Id") ?: throw IOException("Jellyfin item has no identifier.")
    val series = item.text("Type") in setOf("Series","Episode")
    val state = item.objectOrNull("UserData")
    val duration = ((item.number("RunTimeTicks") ?: 0.0)/10_000).toLong()
    val position = ((state?.number("PlaybackPositionTicks") ?: 0.0)/10_000).toLong().coerceAtLeast(0)
    val seriesId = item.text("SeriesId")
    val artworkId = item.text("SeriesPrimaryImageTag")?.let { seriesId } ?: id
    val provider=item.objectOrNull(if(item.text("Type")=="Episode")"SeriesProviderIds" else "ProviderIds")
    val keys=provider?.entrySet()?.mapNotNull{(key,value)->val name=key.lowercase();val text=value.takeIf{it.isJsonPrimitive}?.asString;if(name in setOf("imdb","tmdb","tvdb")&&text!=null&&text.matches(if(name=="imdb")Regex("tt\\d+") else Regex("\\d+")))"$name:$text" else null}.orEmpty()
    val source=item.objects("MediaSources").firstOrNull()
    val video=(source?.objects("MediaStreams").orEmpty()+item.objects("MediaStreams")).firstOrNull{it.text("Type")=="Video"}
    val height=video?.number("Height")?.toInt()
    val quality=height?.let{when{it>=1800->"4K";it>=1000->"1080p";it>=700->"720p";else->"${it}p"}}
    val versionLabel=listOfNotNull(item.text("EditionName"),quality,video?.text("Codec")?.uppercase(),source?.text("Container")?.uppercase() ?: item.text("Container")?.uppercase()).distinct().joinToString(" · ").takeIf{it.isNotBlank()} ?: source?.text("Name")
    return MediaItem(jellyfinUserDataKnown=state?.has("Played")==true,jellyfinFavorite=state?.get("IsFavorite")?.takeIf{it.isJsonPrimitive}?.asBoolean,jellyfinVideoHeight=height,jellyfinVideoCodec=video?.text("Codec"),jellyfinHdr=video?.text("VideoRangeType"),jellyfinUnplayedCount=state?.number("UnplayedItemCount")?.toInt(),jellyfinKnownItemCount=item.number("RecursiveItemCount")?.toInt(),jellyfinProviderKeys=keys,jellyfinPresentationKey=item.text("PresentationUniqueKey"),jellyfinVersionLabel=versionLabel,lastPlayedAt=libraryTimestamp(state?.text("LastPlayedDate")),id = "jellyfin:${seriesId ?: id}", imdbId = (if (item.text("Type") == "Episode") item.objectOrNull("SeriesProviderIds") else item.objectOrNull("ProviderIds"))?.text("Imdb")?.takeIf { it.matches(Regex("tt\\d+")) }, type = if (series) "series" else "movie",name = item.text("SeriesName") ?: item.text("Name").orEmpty(),year = item.text("ProductionYear"),runtime = duration.takeIf { it > 0 }?.let { "${it/60_000} min" },overview = item.text("Overview"),poster = "$server/Items/$artworkId/Images/Primary?maxWidth=500",background = if(item.text("Type")=="Episode") "$server/Items/$id/Images/Primary?maxWidth=780" else "$server/Items/$id/Images/Backdrop/0?maxWidth=1280",progress = if (duration > 0) (position.toDouble()/duration).toFloat().coerceIn(0f,1f) else ((state?.number("PlayedPercentage") ?: 0.0)/100).toFloat().coerceIn(0f,1f),watched = state?.get("Played")?.takeIf { it.isJsonPrimitive }?.asBoolean == true,genres = item.get("Genres")?.takeIf{it.isJsonArray}?.asJsonArray?.mapNotNull{it.takeIf{it.isJsonPrimitive}?.asString}.orEmpty(),aliases=listOfNotNull(item.text("OriginalTitle")),addedAt=libraryTimestamp(item.text("DateCreated")),jellyfinId = id,season = item.number("ParentIndexNumber")?.toInt(),episode = item.number("IndexNumber")?.toInt(),jellyfinSeriesId = seriesId,positionMs = position,durationMs = duration,releaseInfo = if (seriesId != null) "S${item.number("ParentIndexNumber")?.toInt() ?: 1} E${item.number("IndexNumber")?.toInt() ?: 1} · ${item.text("Name").orEmpty()}" else item.text("ProductionYear"))
}

object JellyfinPlayback {
    private val syncLock=Mutex()
    private val settings get() = NativeRuntime.settings
    private val user get() = settings.jellyfinUserId.orEmpty()
    // Compatibility entry points are inert: STAK observes Jellyfin and never writes playback state.
    fun queueWatched(media:MediaItem,episode:Episode?,played:Boolean,watchedAt:Long?=null) = Unit
    suspend fun resume(): List<MediaItem> {
        if (!settings.jellyfinConnected) return emptyList()
        val server = DirectJellyfin.normalizeServer(settings.jellyfinUrl.orEmpty())
        val data = DirectJellyfin.sessionRequest("/Users/$user/Items/Resume",mapOf("Limit" to "40","Fields" to "ProviderIds,UserData,RunTimeTicks,Overview","EnableUserData" to "true","MediaTypes" to "Video"))
        if (data.get("Items")?.isJsonArray != true) throw IOException("Jellyfin returned an invalid Continue Watching list.")
        return data.objects("Items").map { jellyfinMediaItem(it,server) }.filter { !it.watched && it.positionMs > 0 }
    }
    suspend fun continueFeed():ContinueFeed = supervisorScope {
        if(!settings.jellyfinConnected)return@supervisorScope ContinueFeed(emptyList())
        val scope=jellyfinScope();val userId=user
        val server=DirectJellyfin.normalizeServer(settings.jellyfinUrl.orEmpty())
        suspend fun part(block:suspend()->List<MediaItem>):Result<List<MediaItem>> = try{Result.success(block())}catch(c:CancellationException){throw c}catch(e:Exception){Result.failure(e)}
        val resumed=async{part{resume()}}
        val next=async{part{
            val data=DirectJellyfin.sessionRequest("/Shows/NextUp",mapOf("UserId" to userId,"Limit" to "40","Fields" to "ProviderIds,Overview","EnableUserData" to "true","EnableResumable" to "false"),expectedScope=scope)
            if(data.get("Items")?.isJsonArray!=true)throw IOException("Jellyfin returned an invalid Next Up list.")
            data.objects("Items").map{jellyfinMediaItem(it,server).copy(nextUp=true)}.filter{!it.watched}
        }}
        val r=resumed.await();val n=next.await()
        if(scope!=jellyfinScope())throw CancellationException("Jellyfin account changed")
        if(r.isFailure&&n.isFailure)throw (r.exceptionOrNull() ?: n.exceptionOrNull()!!)
        ContinueFeed((r.getOrDefault(emptyList())+n.getOrDefault(emptyList())).distinctBy{it.jellyfinId?:it.mediaKey},listOfNotNull(
            r.exceptionOrNull()?.let{"Resume: ${jellyfinFailureMessage(it)}"},n.exceptionOrNull()?.let{"Next Up: ${jellyfinFailureMessage(it)}"}).takeIf{it.isNotEmpty()}?.joinToString(" "))
    }
    suspend fun continueWatching():List<MediaItem> = continueFeed().items
    suspend fun resolve(media: MediaItem, episode: Episode? = null): String? {
        if (!settings.jellyfinConnected) return null
        if (media.jellyfinId != null && (!media.isSeries || media.episode != null || episode == null)) return media.jellyfinId
        val seriesId = media.jellyfinSeriesId ?: media.jellyfinId ?: media.id.takeIf { it.startsWith("jellyfin:") }?.substringAfter(':')
        val baseId = seriesId ?: run {
            val imdb = media.imdbId ?: media.mediaKey.takeIf { it.matches(Regex("tt\\d+")) } ?: return null
            DirectJellyfin.sessionRequest("/Users/$user/Items",mapOf("Recursive" to "true","IncludeItemTypes" to if(media.isSeries) "Series" else "Movie","AnyProviderIdEquals" to "imdb.$imdb","Fields" to "ProviderIds","Limit" to "10")).objects("Items").firstOrNull { it.objectOrNull("ProviderIds")?.text("Imdb") == imdb }?.text("Id")
        } ?: return null
        if (!media.isSeries || episode == null) return baseId
        return DirectJellyfin.sessionRequest("/Shows/$baseId/Episodes",mapOf("UserId" to user,"Season" to episode.season.toString(),"Fields" to "ProviderIds,UserData")).objects("Items").firstOrNull { it.number("ParentIndexNumber")?.toInt() == episode.season && it.number("IndexNumber")?.toInt() == episode.episode }?.text("Id")
    }
    suspend fun watchedStates(media: MediaItem): Map<String,Boolean> {
        val id = resolve(media) ?: return emptyMap()
        if (!media.isSeries) {
            val data = DirectJellyfin.sessionRequest("/Users/$user/Items/$id")
            return mapOf(media.mediaKey to (data.objectOrNull("UserData")?.get("Played")?.asBoolean == true))
        }
        return DirectJellyfin.sessionRequest("/Shows/$id/Episodes",mapOf("UserId" to user,"Fields" to "UserData")).objects("Items").associate { ep -> "${media.mediaKey}:${ep.number("ParentIndexNumber")?.toInt()}:${ep.number("IndexNumber")?.toInt()}" to (ep.objectOrNull("UserData")?.get("Played")?.asBoolean == true) }
    }
    suspend fun sync(record:PlaybackRecord) = Unit
    suspend fun flushPending():Int = 0
}
