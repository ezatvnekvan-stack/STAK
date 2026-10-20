package app.stak.nativeapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

class StakViewModel(application: Application) : AndroidViewModel(application) {
    val repository = Repository(application)
    val saved = repository.dao.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val watchedEpisodes = repository.dao.episodes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val preferences = application.getSharedPreferences("stak_appearance", 0)
    val reduced = MutableStateFlow(preferences.getBoolean("reduced", false))
    val haptics = MutableStateFlow(preferences.getBoolean("haptics", true))
    val dark = MutableStateFlow(preferences.getBoolean("dark", true))
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val mutationLock = Mutex()
    val catalogs = java.util.concurrent.ConcurrentHashMap<String, List<Media>>()
    suspend fun catalog(type: String, query: String = "", refresh: Boolean = false): List<Media> {
        val key = "$type:$query"
        if (!refresh) catalogs[key]?.let { return it }
        return repository.catalog(type, query).also { catalogs[key] = it }
    }
    fun appearance(name: String, enabled: Boolean) {
        preferences.edit().putBoolean(name, enabled).apply()
        when (name) { "reduced" -> reduced.value = enabled; "haptics" -> haptics.value = enabled; "dark" -> dark.value = enabled }
    }
    fun media(key: String) = repository.cached(key) ?: saved.value.firstOrNull { it.mediaKey == key }?.media()
    fun operation(block: suspend () -> Unit): Job = viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { messages.emit(e.message ?: "Could not complete this action.") }
    }
    fun toggle(media: Media, collection: String) = operation {
        mutationLock.withLock {
            val previous = repository.dao.all().firstOrNull { it.mediaKey == media.key && it.collection == collection }
            if (previous == null) repository.dao.put(SavedMedia(media.key, collection, media.json().toString()))
            else repository.dao.remove(media.key, collection)
            WatchlistWidget.refresh(getApplication())
        }
    }
    suspend fun changeWatched(media: Media, watched: Boolean): SavedMedia? = mutationLock.withLock {
        val previous = repository.dao.all().firstOrNull { it.mediaKey == media.key && it.collection == "tracking" }
        // Confirm server mutation first. A failed sync never appears as a successful local change.
        repository.setJellyfinWatched(media, watched)
        if (watched) repository.dao.put(SavedMedia(media.key, "tracking", media.json().put("played", true).put("progress", 100).toString()))
        else repository.dao.remove(media.key, "tracking")
        previous
    }
    suspend fun restoreWatched(media: Media, previous: SavedMedia?) = mutationLock.withLock {
        repository.setJellyfinWatched(media, previous != null)
        if (previous != null) repository.dao.put(previous) else repository.dao.remove(media.key, "tracking")
    }
    suspend fun importBackup(text: String) {
        val entries = BackupCodec.decode(text)
        val root = JSONObject(text)
        val episodeRows = if (root.optString("format") == "stak-native-backup") root.optJSONArray("episodes").objects().mapNotNull {
            val key = it.optString("mediaKey"); val season = it.optInt("season"); val episode = it.optInt("episode")
            if (key.length <= 160 && season > 0 && episode > 0) WatchedEpisode(key, season, episode, it.optLong("watchedAt", System.currentTimeMillis())) else null
        } else root.optJSONArray("tracking").objects().flatMap { title ->
            val media = Media.fromJson(title)
            if (entries.none { it.mediaKey == media.key }) emptyList() else title.optJSONArray("episodes").objects().filter { it.optBoolean("played") && it.optInt("season") > 0 && it.optInt("episode") > 0 }
                .map { WatchedEpisode(media.key, it.optInt("season"), it.optInt("episode")) }
        }
        require(episodeRows.size <= 20000) { "Too many episode entries." }
        repository.db.withTransaction {
            repository.dao.merge(entries)
            val existing = repository.dao.allEpisodes().map { Triple(it.mediaKey, it.season, it.episode) }.toSet()
            episodeRows.filter { Triple(it.mediaKey, it.season, it.episode) !in existing }.forEach { repository.dao.putEpisode(it) }
        }
        WatchlistWidget.refresh(getApplication())
        messages.emit("Imported ${entries.size} titles; existing entries preserved.")
    }
}
