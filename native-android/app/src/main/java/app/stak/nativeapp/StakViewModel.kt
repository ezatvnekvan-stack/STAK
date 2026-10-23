@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package app.stak.nativeapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

class StakViewModel
@JvmOverloads
constructor(application: Application, val repository: Repository = Repository(application)) :
    AndroidViewModel(application) {
    val accountScope =
        repository.credentials
            .scopes()
            .stateIn(viewModelScope, SharingStarted.Eagerly, repository.accountScope)
    val saved =
        accountScope
            .flatMapLatest { scope ->
                repository.daoFor(scope).observe().onStart { emit(emptyList()) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val watchedEpisodes =
        accountScope
            .flatMapLatest { scope ->
                repository.daoFor(scope).episodes().onStart { emit(emptyList()) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val preferences = application.getSharedPreferences("stak_appearance", 0)
    val reduced = MutableStateFlow(preferences.getBoolean("reduced", false))
    val haptics = MutableStateFlow(preferences.getBoolean("haptics", true))
    val dark = MutableStateFlow(preferences.getBoolean("dark", true))
    val options =
        MutableStateFlow<Map<String, String>>(
            preferences.all.filterValues { it is String }.mapValues { it.value as String }
        )

    fun option(name: String, value: String) {
        preferences.edit().putString(name, value).apply()
        options.value = options.value + (name to value)
        if (name == "theme" && value != "system") appearance("dark", value == "dark")
    }

    val torboxAccount = TorBoxAccountStore(repository)
    val indexers = IndexerStore(repository)

    val artworkTone = MutableStateFlow(GRAPHITE_TONE)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val mutationLock = Mutex()
    val catalogs = java.util.concurrent.ConcurrentHashMap<String, List<Media>>()
    private val catalogReads = ReadLocks()

    init {
        viewModelScope.launch {
            accountScope.collectLatest {
                repository.forgetPrivateMetadata()
                indexers.reload()
                artworkTone.value = GRAPHITE_TONE
                WatchlistWidget.refresh(getApplication())
                // Verification outlives a screen recomposition/account-navigation reset.
                torboxAccount.refresh()
            }
        }
    }

    suspend fun catalog(type: String, query: String = "", refresh: Boolean = false): List<Media> {
        val key = "$type:$query"
        if (!refresh)
            catalogs[key]?.let {
                return it
            }
        return catalogReads.forKey(key).withLock {
            if (!refresh) catalogs[key]?.let { return@withLock it }
            repository.catalog(type, query, refresh = refresh).also { catalogs[key] = it }
        }
    }

    fun appearance(name: String, enabled: Boolean) {
        preferences.edit().putBoolean(name, enabled).apply()
        when (name) {
            "reduced" -> reduced.value = enabled
            "haptics" -> haptics.value = enabled
            "dark" -> dark.value = enabled
        }
    }

    fun media(key: String) =
        repository.cached(key) ?: saved.value.firstOrNull { it.mediaKey == key }?.media()

    fun operation(block: suspend () -> Unit): Job =
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages.emit(e.message ?: "Could not complete this action.")
            }
        }

    fun toggle(media: Media, collection: String) = operation {
        mutationLock.withLock {
            val dao = repository.dao
            val previous =
                dao.all().firstOrNull { it.mediaKey == media.key && it.collection == collection }
            if (previous == null)
                dao.put(SavedMedia(media.key, collection, media.json().toString()))
            else dao.remove(media.key, collection)
            WatchlistWidget.refresh(getApplication())
        }
    }

    suspend fun changeWatched(media: Media, watched: Boolean): SavedMedia? =
        mutationLock.withLock {
            val dao = repository.dao
            val previous =
                dao.all().firstOrNull { it.mediaKey == media.key && it.collection == "tracking" }
            // Confirm server mutation first. A failed sync never appears as a successful local
            // change.
            repository.setJellyfinWatched(media, watched)
            if (watched)
                dao.put(
                    SavedMedia(
                        media.key,
                        "tracking",
                        media.json().put("played", true).put("progress", 100).toString(),
                    )
                )
            else dao.remove(media.key, "tracking")
            previous
        }

    suspend fun changeEpisodesWatched(
        media: Media,
        episodes: List<Episode>,
        watched: Boolean,
        watchedAt: Long = System.currentTimeMillis(),
    ) =
        mutationLock.withLock {
            val dao = repository.dao
            repository.setJellyfinEpisodesWatched(media, episodes, watched)
            repository.db.withTransaction {
                episodes.forEach { episode ->
                    if (watched)
                        dao.putEpisode(
                            WatchedEpisode(media.key, episode.season, episode.number, watchedAt)
                        )
                    else dao.removeEpisode(media.key, episode.season, episode.number)
                }
                val marks = dao.allEpisodes().filter { it.mediaKey == media.key }
                val total =
                    media.episodes.count { it.season > 0 }.takeIf { it > 0 } ?: media.totalEpisodes
                val complete = total > 0 && marks.size >= total
                val json =
                    media
                        .json()
                        .put("played", complete)
                        .put(
                            "progress",
                            if (total > 0) (marks.size.toFloat() / total * 100).coerceAtMost(100f)
                            else 0f,
                        )
                if (marks.isNotEmpty())
                    dao.put(
                        SavedMedia(
                            media.key,
                            "tracking",
                            json.toString(),
                            marks.maxOf { it.watchedAt },
                        )
                    )
                else dao.remove(media.key, "tracking")
            }
        }

    suspend fun restoreWatched(media: Media, previous: SavedMedia?) =
        mutationLock.withLock {
            val dao = repository.dao
            require(previous == null || previous.accountScope == dao.scope) {
                "Switch back to the original account to undo this action."
            }
            repository.setJellyfinWatched(
                media,
                previous?.let { JSONObject(it.json).optBoolean("played") } ?: false,
            )
            if (previous != null) dao.put(previous) else dao.remove(media.key, "tracking")
        }

    suspend fun syncJellyfin(): String = mutationLock.withLock { repository.syncJellyfin() }

    suspend fun importBackup(text: String) =
        restoreBackup(parseBackupArchive(text), repository.accountScope)

    suspend fun restoreBackup(archive: BackupArchive, expectedScope: String) =
        mutationLock.withLock {
            check(repository.accountScope == expectedScope) {
                "Account changed. Review the backup again."
            }
            val dao = repository.daoFor(expectedScope)
            // Validate combined limits before committing any restored data.
            val existingIndexers = indexers.read()
            require(
                (existingIndexers + archive.indexers).distinctBy { it.kind to it.url }.size <= 5000
            ) {
                "Indexer limit reached."
            }
            val existingLists = repository.hashLists()
            val listIds = existingLists.map { it.optString("id") }.toSet()
            val lists =
                existingLists +
                    archive.lists
                        .distinctBy { it.optString("id") }
                        .filter { it.optString("id") !in listIds }
            require(lists.size <= 1000) { "Hash list limit reached." }
            repository.db.withTransaction {
                dao.merge(archive.entries)
                val existing =
                    dao.allEpisodes().map { Triple(it.mediaKey, it.season, it.episode) }.toSet()
                archive.episodes
                    .filter { Triple(it.mediaKey, it.season, it.episode) !in existing }
                    .forEach { dao.putEpisode(it) }
            }
            check(repository.accountScope == expectedScope) {
                "Account changed. Titles were preserved in the original account; review remaining settings again."
            }
            val editor = preferences.edit()
            archive.settings
                .filterKeys { !preferences.contains(it) }
                .forEach { (name, value) ->
                    if (value is Boolean) editor.putBoolean(name, value)
                    else editor.putString(name, value as String)
                }
            editor
                .putString(repository.hashListKey(), org.json.JSONArray(lists).toString())
                .commit()
                .also { check(it) { "Titles restored, but preferences could not be saved." } }
            indexers.mergeMissing(archive.indexers)
            archive.connections.forEach { (name, value) ->
                if (repository.credentials.get(name).isBlank())
                    repository.credentials.set(name, value)
            }
            options.value =
                preferences.all.filterValues { it is String }.mapValues { it.value as String }
            reduced.value = preferences.getBoolean("reduced", false)
            haptics.value = preferences.getBoolean("haptics", true)
            dark.value = preferences.getBoolean("dark", true)
            WatchlistWidget.refresh(getApplication())
            messages.emit(
                "Backup merged into this account; existing records and settings preserved."
            )
        }
}
