package app.stak.nativeapp.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.MessageDigest

data class PlaybackRecord(val media: MediaItem, val scope: String, val positionMs: Long, val durationMs: Long, val played: Boolean, val updatedAt: Long, val jellyfinItemId: String? = null, val pending: Boolean = false, val manual: Boolean = false,val playedAt:Long?=null,val localOnly:Boolean=false) {
    val key: String get() = "$scope:${media.mediaKey}:${media.season ?: 0}:${media.episode ?: 0}"
}
fun credentialScope(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
fun jellyfinScope(settings: SettingsStore = NativeRuntime.settings): String = credentialScope("jellyfin:${settings.jellyfinUrl.orEmpty().trimEnd('/')}:${settings.jellyfinUserId.orEmpty()}")
fun torboxScope(settings: SettingsStore = NativeRuntime.settings): String = credentialScope("torbox:${settings.torboxKey.orEmpty()}")
fun playbackCompleted(positionMs: Long, durationMs: Long, ended: Boolean): Boolean = durationMs > 0 && (ended || positionMs.toDouble()/durationMs >= .95)

/** Credential-scoped, bounded and encrypted; pending updates survive a process restart. */
class PlaybackStore(private val settings: SettingsStore = NativeRuntime.settings) {
    companion object { private val lock = Any() }
    fun all(): List<PlaybackRecord> = synchronized(lock) { runCatching { Gson().fromJson<List<PlaybackRecord>>(settings.playbackJson ?: "[]", object:TypeToken<List<PlaybackRecord>>(){}.type).orEmpty() }.getOrDefault(emptyList()) }
    fun save(record: PlaybackRecord) = synchronized(lock) {
        val next = (all().filterNot { it.key == record.key || (it.scope==record.scope&&it.media.sameIdentity(record.media)&&it.media.season==record.media.season&&it.media.episode==record.media.episode) } + record).sortedByDescending { it.updatedAt }.take(300)
        settings.playbackJson = Gson().toJson(next)
    }
    fun acknowledge(record: PlaybackRecord) = synchronized(lock) {
        val next = all().map { if (it == record) it.copy(pending = false) else it }
        settings.playbackJson = Gson().toJson(next)
    }
    fun markLocalOnly(record:PlaybackRecord)=synchronized(lock){
        settings.playbackJson=Gson().toJson(all().map{if(it==record)it.copy(pending=false,localOnly=true)else it})
    }
    /** A confirmed matching library item can promote a local-only change; unrelated items and accounts cannot. */
    fun requeueAvailable(scope:String,items:List<MediaItem>)=synchronized(lock){
        val next=all().map{record->
            if(!record.localOnly||record.scope!=scope)record else {
                val item=items.firstOrNull{it.jellyfinUserDataKnown==true&&it.jellyfinId!=null&&matchingWatch(it,record.media)}
                if(item==null)record else record.copy(media=record.media.copy(jellyfinId=item.jellyfinId,jellyfinSeriesId=item.jellyfinSeriesId?:record.media.jellyfinSeriesId),jellyfinItemId=item.jellyfinId,pending=true,localOnly=false)
            }
        }
        if(next!=all())settings.playbackJson=Gson().toJson(next)
    }
    fun latest(scope: String, media: MediaItem): PlaybackRecord? = all().firstOrNull { it.scope == scope && it.media.mediaKey == media.mediaKey && it.media.season == media.season && it.media.episode == media.episode }
    fun continuing(scopes: Set<String>): List<MediaItem> = all().filter { it.scope in scopes && it.positionMs > 0 && !it.played && it.durationMs > 0 }.map { r -> r.media.copy(positionMs = r.positionMs,durationMs = r.durationMs,progress = (r.positionMs.toDouble()/r.durationMs).toFloat().coerceIn(0f,1f),jellyfinId = r.jellyfinItemId ?: r.media.jellyfinId) }
}

