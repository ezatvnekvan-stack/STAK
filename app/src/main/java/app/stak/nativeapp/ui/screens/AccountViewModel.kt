package app.stak.nativeapp.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stak.nativeapp.data.ApiClient
import app.stak.nativeapp.data.SettingsStore
import app.stak.nativeapp.ui.theme.StakAccent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class ConnectionState { Unknown, Checking, Online, Offline }

class AccountViewModel(private val settings: SettingsStore, private val onDailyReminder: (Boolean) -> Unit = {}, private val onTestReminder: () -> Unit = {}) : ViewModel() {
    private val _serverUrl = MutableStateFlow(settings.serverUrl)
    val serverUrl: StateFlow<String> = _serverUrl
    private val _torboxKey = MutableStateFlow(settings.torboxKey.orEmpty())
    val torboxKey: StateFlow<String> = _torboxKey
    private val _tmdbKey = MutableStateFlow(settings.tmdbKey.orEmpty())
    val tmdbKey: StateFlow<String> = _tmdbKey
    private val _omdbKey = MutableStateFlow(settings.omdbKey.orEmpty())
    val omdbKey: StateFlow<String> = _omdbKey
    private val _connection = MutableStateFlow(ConnectionState.Unknown)
    val connection: StateFlow<ConnectionState> = _connection
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    private val _accent = MutableStateFlow(settings.accentColor)
    val accent: StateFlow<Long> = _accent
    private val _jellyfinUrl = MutableStateFlow(settings.jellyfinUrl.orEmpty())
    val jellyfinUrl: StateFlow<String> = _jellyfinUrl
    private val _jellyfinConnected = MutableStateFlow(settings.jellyfinConnected)
    private val _credentialRevision=MutableStateFlow(0)
    val credentialRevision:StateFlow<Int> = _credentialRevision
    val jellyfinConnected: StateFlow<Boolean> = _jellyfinConnected
    private val _reducedMotion = MutableStateFlow(settings.reducedMotion)
    val reducedMotion: StateFlow<Boolean> = _reducedMotion
    private val _haptics = MutableStateFlow(settings.haptics)
    val haptics: StateFlow<Boolean> = _haptics
    private val _backgroundStyle = MutableStateFlow(settings.backgroundStyle)
    val backgroundStyle: StateFlow<app.stak.nativeapp.data.BackgroundStyle> = _backgroundStyle
    fun setBackgroundStyle(value: app.stak.nativeapp.data.BackgroundStyle) {
        settings.backgroundStyle = value
        _backgroundStyle.value = value
    }
    private val _ambient = MutableStateFlow(settings.ambientEnabled)
    val ambient: StateFlow<Boolean> = _ambient
    private val _dailyReminder = MutableStateFlow(settings.dailyReminder)
    val dailyReminder: StateFlow<Boolean> = _dailyReminder

    private val _navigation = MutableStateFlow(app.stak.nativeapp.data.NavigationPreferences.read(settings.navigationJson))
    val navigation: StateFlow<List<String>> = _navigation

    fun setNavigation(id: String, enabled: Boolean) {
        val next = if (enabled) _navigation.value + id else _navigation.value.filter { it != id }
        _navigation.value = app.stak.nativeapp.data.NavigationPreferences.normalize(next)
        settings.navigationJson = com.google.gson.Gson().toJson(_navigation.value)
    }

    fun moveNavigation(id: String, direction: Int) {
        val next = _navigation.value.toMutableList()
        val index = next.indexOf(id)
        val target = index + direction
        if (index < 0 || target !in next.indices) return
        next.add(target, next.removeAt(index))
        _navigation.value = next
        settings.navigationJson = com.google.gson.Gson().toJson(next)
    }

    private val _homeSections = MutableStateFlow(app.stak.nativeapp.data.HomeSections.read(settings.homeRowsJson))
    val homeSections: StateFlow<List<app.stak.nativeapp.data.HomeSection>> = _homeSections

    fun setHomeSection(id: String, enabled: Boolean) {
        _homeSections.value = _homeSections.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
        settings.homeRowsJson = com.google.gson.Gson().toJson(_homeSections.value.map { mapOf("id" to it.id, "on" to it.enabled) })
    }

    fun moveHomeSection(id: String, direction: Int) {
        val rows = _homeSections.value.toMutableList()
        val index = rows.indexOfFirst { it.id == id }
        val target = index + direction
        if (index < 0 || target !in rows.indices) return
        val item = rows.removeAt(index)
        rows.add(target, item)
        _homeSections.value = rows
        settings.homeRowsJson = com.google.gson.Gson().toJson(rows.map { mapOf("id" to it.id, "on" to it.enabled) })
    }

    val services = ConnectionSettingsController(settings, viewModelScope, changed = ::refreshServiceSettings)
    private fun refreshServiceSettings() {
        _credentialRevision.value++
        _torboxKey.value = settings.torboxKey.orEmpty()
        _tmdbKey.value = settings.tmdbKey.orEmpty()
        _omdbKey.value = settings.omdbKey.orEmpty()
        _jellyfinUrl.value = settings.jellyfinUrl.orEmpty()
        _jellyfinConnected.value = settings.jellyfinConnected
        _connection.value = when {
            settings.torboxKey.isNullOrBlank() -> ConnectionState.Unknown
            services.state.value.account != null -> ConnectionState.Online
            else -> _connection.value
        }
    }

    init { applyAccent(settings.accentColor); testConnection() }

    fun setServerUrl(value: String) { _serverUrl.value = value }
    fun setTorboxKey(value: String) { _torboxKey.value = value }
    fun setTmdbKey(value: String) { _tmdbKey.value = value }
    fun setOmdbKey(value: String) { _omdbKey.value = value }
    fun setJellyfinUrl(value: String) { _jellyfinUrl.value = value }

    fun setReducedMotion(value: Boolean) {
        _reducedMotion.value = value
        settings.reducedMotion = value
    }

    fun setHaptics(value: Boolean) {
        _haptics.value = value
        settings.haptics = value
    }

    fun setAmbient(value: Boolean) {
        _ambient.value = value
        settings.ambientEnabled = value
    }

    fun setDailyReminder(value: Boolean) {
        _dailyReminder.value = value
        settings.dailyReminder = value
        onDailyReminder(value)
    }

    fun sendTestReminder() = onTestReminder()

    fun setAccent(value: Long) {
        _accent.value = value
        settings.accentColor = value
        applyAccent(value)
    }

    private fun applyAccent(value: Long) {
        StakAccent = Color(value.toInt())
    }

    fun testConnection() {
        viewModelScope.launch {
            _connection.value = ConnectionState.Checking
            _message.value = null
            if (_torboxKey.value.isBlank()) {
                _connection.value = ConnectionState.Unknown
                _message.value = "No TorBox key configured"
                return@launch
            }
            val result = try { Result.success(app.stak.nativeapp.data.DirectServices.account(_torboxKey.value.trim())) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { Result.failure(error) }
            if (result.isSuccess) {
                _connection.value = ConnectionState.Online
                _message.value = result.getOrNull()?.let { "Connected as $it" } ?: "TorBox connected"
            } else {
                _connection.value = ConnectionState.Offline
                _message.value = "Could not verify TorBox. Check the key and your connection."
            }
        }
    }
}
