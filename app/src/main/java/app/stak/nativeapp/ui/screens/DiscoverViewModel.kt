package app.stak.nativeapp.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stak.nativeapp.data.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DiscoverViewModel(private val repository: MediaRepository = MediaRepository()) : ViewModel() {
    private val saved = readSaved()
    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items
    private var allItems = emptyList<MediaItem>()
    private var cursor: Map<String,Int>? = null
    private var explicitBrowse = false
    private val _browsing = MutableStateFlow(true)
    val browsing: StateFlow<Boolean> = _browsing
    private val _partialErrors = MutableStateFlow<List<String>>(emptyList())
    val partialErrors: StateFlow<List<String>> = _partialErrors
    private var watched = emptySet<String>()
    private var library = emptySet<String>()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading
    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _offline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = _offline
    private val _type = MutableStateFlow(saved.type)
    val type: StateFlow<String> = _type
    private val _category = MutableStateFlow(saved.category)
    val category: StateFlow<String> = _category
    private val _region = MutableStateFlow(saved.region)
    val region: StateFlow<String> = _region
    private val _filters = MutableStateFlow(saved.filters)
    val filters: StateFlow<DiscoveryFilters> = _filters
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query
    private val _page = MutableStateFlow(1)
    val page: StateFlow<Int> = _page
    private val _totalPages = MutableStateFlow(1)
    val totalPages: StateFlow<Int> = _totalPages
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice
    private val _ratingErrors = MutableStateFlow(0)
    val ratingErrors: StateFlow<Int> = _ratingErrors
    private val _verifiedImdb = MutableStateFlow(false)
    val verifiedImdb: StateFlow<Boolean> = _verifiedImdb
    private val _genres = MutableStateFlow(DirectDiscovery.fallbackGenres.map { DiscoveryOption(it,it) })
    val genres: StateFlow<List<DiscoveryOption>> = _genres
    private val _providers = MutableStateFlow<List<DiscoveryOption>>(emptyList())
    val providers: StateFlow<List<DiscoveryOption>> = _providers
    private val _regions = MutableStateFlow(listOf(DiscoveryOption(saved.region, saved.region)))
    val regions: StateFlow<List<DiscoveryOption>> = _regions
    private val _servicesLoading = MutableStateFlow(false)
    val servicesLoading: StateFlow<Boolean> = _servicesLoading
    private val _servicesError = MutableStateFlow<String?>(null)
    val servicesError: StateFlow<String?> = _servicesError
    private var job: Job? = null
    private var serviceJob: Job? = null
    private var generation = 0
    private var serviceGeneration = 0
    private var year: Int? = null
    private var requireTmdb = true

    init { load(); refreshServices() }

    private fun readSaved(): DiscoveryState = try {
        val s = Gson().fromJson(NativeRuntime.settings.discoveryJson, DiscoveryState::class.java) ?: DiscoveryState()
        if (s.type !in listOf("movie","series") || s.category !in discoveryCategories.map { it.first } || !s.region.matches(Regex("[A-Z]{2}")) || s.filters.validationError() != null) DiscoveryState() else s
    } catch (_: Exception) { DiscoveryState() }
    private fun persist() {
        if (requireTmdb) NativeRuntime.settings.discoveryJson = Gson().toJson(DiscoveryState(_type.value, _category.value, _region.value, _filters.value))
    }
    fun openDiscover() {
        requireTmdb = true; explicitBrowse = false
        val s = readSaved()
        _type.value = s.type; _category.value = s.category; _region.value = s.region; _filters.value = s.filters
        _query.value = ""; year = null
        load(); refreshServices()
    }
    fun search(query: String) {
        requireTmdb = false; explicitBrowse = false; _type.value = "all"; year = null; _filters.value = DiscoveryFilters()
        setQuery(query); refreshServices()
    }
    fun browse(section: HomeSection) {
        requireTmdb = false; explicitBrowse = true; _query.value = ""; _type.value = section.type
        _category.value = if (section.year == null) (if(section.category=="trending")"trakt_trending"else section.category) else "popular"
        year = null; _filters.value = DiscoveryFilters(yearFrom = section.year?.toString().orEmpty(), yearTo = section.year?.toString().orEmpty())
        load(); refreshServices()
    }
    fun setType(value: String) {
        if (value !in listOf("movie","series","all") || requireTmdb && value == "all" || value == _type.value) return
        _type.value = value; year = null
        _filters.value = _filters.value.copy(genre = "", certification = "")
        persist(); load(); refreshServices()
    }
    fun setCategory(value: String) {
        if (value !in discoveryCategories.map { it.first } || value == _category.value) return
        _category.value = value; year = null; persist(); load()
    }
    fun setRegion(value: String) {
        if (!value.matches(Regex("[A-Z]{2}")) || value == _region.value) return
        _region.value = value; persist(); load(); refreshServices()
    }
    fun applyFilters(value: DiscoveryFilters): Boolean {
        if (value.validationError() != null) return false
        _filters.value = value; year = null; if (!requireTmdb) explicitBrowse = true; persist(); load(); return true
    }
    fun clearFilter(field: String) { applyFilters(_filters.value.without(field)) }
    fun resetFilters() { applyFilters(DiscoveryFilters()) }
    fun clearSearch() { _query.value = ""; _filters.value = DiscoveryFilters(); explicitBrowse = false; year = null; load() }
    fun setQuery(value: String) { _query.value = value; schedule(280) }
    fun setKnownTitles(watched: Set<String>, library: Set<String>) {
        this.watched = watched; this.library = library; updateVisible()
    }
    private fun updateVisible() {
        val f = _filters.value
        val visible = allItems.filter { (!f.hideWatched || it.mediaKey !in watched && it.id !in watched) && (!f.hideLibrary || it.mediaKey !in library && it.id !in library) }
        _items.value = if (_query.value.isBlank()) visible else rankedSearch(visible,_query.value,true)
    }
    fun load() { schedule(0) }
    private fun schedule(debounce: Long) {
        val token = ++generation
        job?.cancel()
        allItems = emptyList(); _items.value = emptyList(); _page.value = 1; _totalPages.value = 1
        _error.value = null; _notice.value = null; _ratingErrors.value = 0; _verifiedImdb.value = false; _partialErrors.value = emptyList(); cursor = null
        _browsing.value = requireTmdb || explicitBrowse || _query.value.isNotBlank() || _filters.value != DiscoveryFilters()
        if (!_browsing.value) { _loading.value = false; _loadingMore.value = false; return }
        _loading.value = true; _loadingMore.value = false
        val type = _type.value; val category = _category.value; val query = _query.value; val filters = _filters.value; val region = _region.value
        val browseYear = year; val strict = requireTmdb
        job = viewModelScope.launch {
            try {
                if (debounce > 0) delay(debounce)
                val result = repository.discover(type, category, query, 1, browseYear, strict, filters, region)
                if (token != generation) return@launch
                allItems = result.items; updateVisible(); _totalPages.value = result.totalPages
                _notice.value = result.notice; _ratingErrors.value = result.ratingErrors; _verifiedImdb.value = result.verifiedImdb
                cursor = result.nextTypePages; _partialErrors.value = result.partialErrors
                _offline.value = result.source == "cinemeta" && NativeRuntime.settings.tmdbKey.isNullOrBlank()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (token == generation) { _error.value = error.message ?: "Could not load titles. Please retry."; _offline.value = true } }
            finally { if (token == generation) _loading.value = false }
        }
    }
    fun loadMore() {
        if (_loading.value || _loadingMore.value || _page.value >= _totalPages.value) return
        val token = generation; val next = _page.value + 1; val nextCursor = cursor
        val type = _type.value; val category = _category.value; val query = _query.value; val filters = _filters.value; val region = _region.value
        val browseYear = year; val strict = requireTmdb
        _loadingMore.value = true
        job = viewModelScope.launch {
            try {
                val result = repository.discover(type, category, query, next, browseYear, strict, filters, region, nextCursor)
                if (token != generation) return@launch
                allItems = (allItems + result.items).distinctBy { "${it.type}:${it.mediaKey}" }; updateVisible()
                _page.value = next; _totalPages.value = result.totalPages; _ratingErrors.value += result.ratingErrors; _error.value = null
                cursor = result.nextTypePages; _partialErrors.value = (_partialErrors.value + result.partialErrors).distinct()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (token == generation) _error.value = error.message ?: "Could not load the next page. Please retry." }
            finally { if (token == generation) _loadingMore.value = false }
        }
    }
    fun refreshServices() {
        val token = ++serviceGeneration; serviceJob?.cancel()
        val mediaType = _type.value
        val type = if (mediaType == "all") "movie" else mediaType; val region = _region.value
        val servicesNeeded = requireTmdb
        _servicesLoading.value = true; _servicesError.value = null; _providers.value = emptyList()
        serviceJob = viewModelScope.launch {
            try {
                supervisorScope {
                    val genres = async { if (mediaType == "all") (repository.discoveryGenres("movie") + repository.discoveryGenres("series")).distinctBy { it.name } else repository.discoveryGenres(type) }
                    if (!servicesNeeded) {
                        try { val result = genres.await(); if (token == serviceGeneration) _genres.value = result }
                        catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { }
                        return@supervisorScope
                    }
                    val regions = async { repository.discoveryRegions() }
                    try {
                        val providers = repository.discoveryProviders(type, region)
                        if (token == serviceGeneration) _providers.value = providers
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { if (token == serviceGeneration) _servicesError.value = error.message ?: "Could not load services." }
                    try { val result = genres.await(); if (token == serviceGeneration) _genres.value = result }
                    catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { }
                    try { val result = regions.await(); if (token == serviceGeneration) _regions.value = result }
                    catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { }
                }
            } finally { if (token == serviceGeneration) _servicesLoading.value = false }
        }
    }
}
