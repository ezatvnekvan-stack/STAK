package app.stak.nativeapp.data

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap

data class TorrentTitleQuery(val title: String, val year: String?, val type: String, val imdb: String?)
fun torrentTitleQuery(raw: String): TorrentTitleQuery? {
    val name = raw.substringAfterLast('/').substringAfterLast('\\').replace(Regex("\\.(mkv|mp4|avi|m4v|ts)$", RegexOption.IGNORE_CASE), "")
    val imdb = Regex("\\btt\\d{5,12}\\b").find(name)?.value
    val marker = Regex("(?i)(?:\\bS\\d{1,2}(?:E\\d{1,3})?\\b|\\b\\d{1,2}x\\d{2}\\b)").find(name.replace('.', ' ').replace('_', ' '))
    val clean = name.replace('.', ' ').replace('_', ' ')
    val yearMatch = Regex("\\b(19\\d{2}|20\\d{2})\\b").findAll(clean).lastOrNull { it.range.first > 0 }
    val technical = Regex("(?i)\\b(?:2160p|1080p|720p|480p|4k|web[- ]?dl|webrip|bluray|bdrip|brrip|hdtv|remux|x26[45]|h[ .]?26[45]|hevc|dvdrip|complete|season|multi)\\b").find(clean)
    val end = listOfNotNull(marker?.range?.first, yearMatch?.range?.first, technical?.range?.first).minOrNull() ?: clean.length
    val title = clean.take(end).replace(Regex("^\\[[^]]+]\\s*"), "").trim(' ', '-', '[', ']', '(', ')').replace(Regex("\\s+"), " ")
    if (title.length < 2 && imdb == null) return null
    return TorrentTitleQuery(title, yearMatch?.value, if (marker != null || clean.contains("complete", true) || clean.contains("season", true)) "series" else "movie", imdb)
}

/** Artwork is matched conservatively; unmatched files remain visible without guessed covers. */
class TorrentMetadataResolver(private val search: suspend (String, String) -> List<MediaItem> = { q, type -> DirectCatalog().search(q, type = type).items }) {
    private val cache = ConcurrentHashMap<TorrentTitleQuery, MediaItem>()
    private val permits = Semaphore(3)
    suspend fun resolveTorrent(torrent:TorBoxTorrent):MediaItem? {
        resolve(torrent.name)?.takeIf{it.poster!=null}?.let{return it}
        val files=torrent.files.filter{it.name.matches(Regex("(?i).*\\.(mkv|mp4|avi|m4v|ts)$"))&&!Regex("(?i)(?:^|[/\\\\ ._-])(?:sample|trailer|extras?)(?:[/\\\\ ._-]|$)").containsMatchIn(it.name)}
        // A multi-title bundle must not inherit the cover of an arbitrary first film.
        val queries=files.mapNotNull{file->torrentTitleQuery(file.name)?.let{it to file.name}}.distinctBy{it.first}
        if(queries.size==1)return resolve(queries.single().second)
        return null
    }
    suspend fun resolve(name: String): MediaItem? {
        val query = torrentTitleQuery(name) ?: return null
        cache[query]?.let { return it }
        return permits.withPermit {
            cache[query]?.let { return@withPermit it }
            try {
                val found = search(query.imdb ?: query.title, if(query.imdb!=null)"all" else query.type)
                val normal: (String) -> String = { java.text.Normalizer.normalize(it, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "") }
                val matching=found.filter{item->query.imdb?.let{it==item.imdbId} ?: (item.type==query.type&&listOf(item.name).plus(item.aliases).any{normal(it)==normal(query.title)})}
                val match=if(query.imdb!=null)matching.firstOrNull() else if(query.year==null)matching.distinctBy{it.mediaKey}.singleOrNull() else matching.firstOrNull{it.year?.take(4)==query.year}
                    ?: if(query.type=="series")matching.filter{(it.year?.take(4)?.toIntOrNull() ?: Int.MAX_VALUE)<=(query.year.toIntOrNull() ?: 0)}.distinctBy{it.mediaKey}.singleOrNull() else null
                match?.also{cache[query]=it}
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
        }
    }
}
