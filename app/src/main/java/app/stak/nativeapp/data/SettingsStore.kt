package app.stak.nativeapp.data

import android.content.Context
import android.content.SharedPreferences

class SettingsStore(context: Context, namespace:String = "") {
    init { require(namespace.length<128&&namespace.matches(Regex("[A-Za-z0-9_-]*"))) }
    internal val applicationContext=context.applicationContext
    internal val storageNamespace=namespace
    var zurgHealthJson:String?
        get()=secure.getSecret("zurg_health")
        set(value)=secure.putSecret("zurg_health",value)
    var zurgConnectionJson:String?
        get()=secure.getSecret("zurg_connection_v1")
        set(value)=secure.putSecret("zurg_connection_v1",value)
    var playbackDeviceJson:String?
        get()=secure.getSecret("playback_device_v1")
        set(value)=secure.putSecret("playback_device_v1",value)
    private val prefs: SharedPreferences = context.getSharedPreferences("${namespace}stak_settings", Context.MODE_PRIVATE)
    private val secure = SecureStorage(context,namespace)
    internal fun backupValues()=encodeBackupPreferences(prefs.all.filterKeys(::cloudBackupKey))
    internal fun backupSecrets()=secure.backupValues()
    internal fun restoreBackupValues(values:com.google.gson.JsonObject,secrets:Map<String,String>){
        val decoded=decodeBackupPreferences(values)
        val editor=prefs.edit();prefs.all.keys.filter(::cloudBackupKey).forEach(editor::remove)
        decoded.forEach{(key,value)->when(value){
            is String->editor.putString(key,value)
            is Boolean->editor.putBoolean(key,value)
            is Int->editor.putInt(key,value)
            is Long->editor.putLong(key,value)
            is Float->editor.putFloat(key,value)
            is Set<*>->editor.putStringSet(key,value.map{it as String}.toSet())
            else->error("Unsupported setting")
        }}
        check(editor.commit()){"Settings restore failed"};secure.restoreValues(secrets)
    }
    internal fun cloudValue(key:String):String?{require(key.startsWith("cloud_"));return secure.getSecret(key)}
    internal fun cloudValue(key:String,value:String?){require(key.startsWith("cloud_"));if(value==null)secure.removeSecretChecked(key)else secure.putSecretChecked(key,value)}
    internal fun listenForBackup(callback:()->Unit):()->Unit {
        val listener=SharedPreferences.OnSharedPreferenceChangeListener{_,key->if(key==null||cloudBackupKey(key))callback()}
        prefs.registerOnSharedPreferenceChangeListener(listener);val secureClose=secure.listenForBackup(callback)
        return {prefs.unregisterOnSharedPreferenceChangeListener(listener);secureClose()}
    }
    var torznabIndexersJson:String?
        get()=secure.getSecret("torznab_indexers_v1")
        set(value)=secure.putSecret("torznab_indexers_v1",value)
    internal fun saveTorznabIndexersChecked(value:String)=secure.putSecretChecked("torznab_indexers_v1",value)
    var streamAddonsJson:String?
        get()=secure.getSecret("stream_addons")
        set(value)=secure.putSecret("stream_addons",value)
    var setupComplete:Boolean
        get()=prefs.getBoolean("setup_1_complete",false)
        set(value)=prefs.edit().putBoolean("setup_1_complete",value).apply()
    fun finishSetup(){ prefs.edit().putBoolean("setup_1_complete",true).putBoolean("onboarded",true).apply() }
    var journalPreferences:String?
        get()=prefs.getString("journal_preferences",null)
        set(value)=prefs.edit().putString("journal_preferences",value).apply()
    var watchlistPreferences:String?
        get()=prefs.getString("watchlist_preferences",null)
        set(value)=prefs.edit().putString("watchlist_preferences",value).apply()
    var libraryApprovalsJson:String?
        get()=secure.getSecret("stak_library_approvals_v1")
        set(value)=secure.putSecret("stak_library_approvals_v1",value)
    internal fun saveLibraryApprovalsChecked(value:String)=secure.putSecretChecked("stak_library_approvals_v1",value)

    /** Per-series auto-add rules + watcher connection (secret, like approvals). */
    var watcherRulesJson:String?
        get()=secure.getSecret("watcher_rules_v1")
        set(value)=secure.putSecret("watcher_rules_v1",value)
    internal fun saveWatcherRulesChecked(value:String)=secure.putSecretChecked("watcher_rules_v1",value)

    /** Transient watcher state cache (excluded from cloud backup: it is re-fetched). */
    var watchStatusCacheJson:String?
        get()=prefs.getString("watch_status_cache",null)
        set(value)=prefs.edit().putString("watch_status_cache",value).apply()
    var playbackJson: String?
        get() = secure.getSecret("playback_state")
        set(value) = secure.putSecret("playback_state", value)

    var movieMetadata: String
        get() = prefs.getString("metadata_movie", "tmdb") ?: "tmdb"
        set(value) = prefs.edit().putString("metadata_movie", if (value == "cinemeta") value else "tmdb").apply()
    var seriesMetadata: String
        get() = prefs.getString("metadata_series", "tmdb") ?: "tmdb"
        set(value) = prefs.edit().putString("metadata_series", if (value == "cinemeta") value else "tmdb").apply()
    var ratingsEnabled: Boolean
        get() = prefs.getBoolean("metadata_ratings", true)
        set(value) = prefs.edit().putBoolean("metadata_ratings", value).apply()
    var imdbOnLists: Boolean
        get() = prefs.getBoolean("metadata_imdb_lists", false)
        set(value) = prefs.edit().putBoolean("metadata_imdb_lists", value).apply()
    var jellyfinUserName: String?
        get() = prefs.getString("jellyfin_user_name", null)
        set(value) = prefs.edit().putString("jellyfin_user_name", value).apply()
    val jellyfinDeviceId: String
        get() = prefs.getString("jellyfin_device_id", null) ?: java.util.UUID.randomUUID().toString().also { prefs.edit().putString("jellyfin_device_id", it).apply() }

    var savedPublicLists:String?
        get()=prefs.getString("saved_public_lists",null)
        set(value)=prefs.edit().putString("saved_public_lists",value).apply()

    var discoveryJson: String?
        get() = prefs.getString("discovery_state", null)
        set(value) = prefs.edit().putString("discovery_state", value).apply()

    var serverUrl: String
        get() = prefs.getString("server_url", DEFAULT_SERVER) ?: DEFAULT_SERVER
        set(value) {
            prefs.edit().putString("server_url", value).apply()
            ApiClient.configure(value)
        }

    var hasOnboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(value) = prefs.edit().putBoolean("onboarded", value).apply()

    var navigationJson: String?
        get() = prefs.getString("navigation", null)
        set(value) = prefs.edit().putString("navigation", value).apply()

    var homeRowsJson: String?
        get() = prefs.getString("home_rows", null)
        set(value) = prefs.edit().putString("home_rows", value).apply()

    var accentColor: Long
        get() = prefs.getLong("accent_color", DEFAULT_ACCENT)
        set(value) = prefs.edit().putLong("accent_color", value).apply()

    var torboxKey: String?
        get() = secure.getSecret("torbox_key")
        set(value) = secure.putSecret("torbox_key", value)

    var tmdbKey: String?
        get() = secure.getSecret("tmdb_key")
        set(value) = secure.putSecret("tmdb_key", value)

    internal fun saveTraktClientIdChecked(value:String)=secure.putSecretChecked("trakt_client_id",value)
    var traktClientId:String?
        get()=secure.getSecret("trakt_client_id")
        set(value)=secure.putSecret("trakt_client_id",value)

    var omdbKey: String?
        get() = secure.getSecret("omdb_key")
        set(value) = secure.putSecret("omdb_key", value)

    var jellyfinUrl: String?
        get() = prefs.getString("jellyfin_url", null)
        set(value) = prefs.edit().putString("jellyfin_url", value).apply()

    var jellyfinToken: String?
        get() = secure.getSecret("jellyfin_token")
        set(value) = secure.putSecret("jellyfin_token", value)

    var jellyfinUserId: String?
        get() = secure.getSecret("jellyfin_user_id")
        set(value) = secure.putSecret("jellyfin_user_id", value)

    val jellyfinConnected: Boolean get() = !jellyfinUrl.isNullOrBlank() && !jellyfinToken.isNullOrBlank() && !jellyfinUserId.isNullOrBlank()

    var reducedMotion: Boolean
        get() = prefs.getBoolean("reduced_motion", false)
        set(value) = prefs.edit().putBoolean("reduced_motion", value).apply()

    var haptics: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(value) = prefs.edit().putBoolean("haptics", value).apply()

    var ambientEnabled: Boolean
        get() = prefs.getBoolean("ambient", true)
        set(value) = prefs.edit().putBoolean("ambient", value).apply()

    /** Daily reminder; off until the owner enables it (needs the notification permission). */
    var dailyReminder: Boolean
        get() = prefs.getBoolean("daily_reminder", false)
        set(value) = prefs.edit().putBoolean("daily_reminder", value).apply()

    /** Last reminder posted, used to keep it to at most one per day. */
    var lastReminderAt: Long
        get() = prefs.getLong("last_reminder_at", 0L)
        set(value) = prefs.edit().putLong("last_reminder_at", value).apply()

    /** One-time seed so the offline experience matches the shipped reference. */
    var watchlistSeeded: Boolean
        get() = prefs.getBoolean("watchlist_seeded", false)
        set(value) = prefs.edit().putBoolean("watchlist_seeded", value).apply()

    companion object {
        const val DEFAULT_SERVER = "http://10.0.2.2:8000/api/"
        const val DEFAULT_ACCENT = 0xFFD71921L
    }
}
