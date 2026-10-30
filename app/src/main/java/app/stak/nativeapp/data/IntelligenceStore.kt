package app.stak.nativeapp.data

import androidx.room.withTransaction
import app.stak.nativeapp.data.db.*
import com.google.gson.Gson
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class ZurgConnection(val endpoint:String="",val username:String="",val password:String="",val libraryId:String="",val mountPath:String="",val jellyfinAccount:String="",val scanWhenVisible:Boolean=false){override fun toString()="ZurgConnection(credentials hidden)"}
data class PlaybackDevice(val enabled:Boolean=false,val name:String="Apple TV + Samsung TV",val hdr10:String="Unknown",val hdr10Plus:String="Unknown",val dolbyVision:String="Unknown",val hevc:String="Unknown",val av1:String="Unknown",val atmos:String="Unknown",val dts:String="Unknown")
data class MediaEvent(val id:String=UUID.randomUUID().toString(),val scope:String,val kind:String,val itemId:String,val title:String,val at:Long=System.currentTimeMillis(),val detail:String="",val source:String="STAK observed")
data class MediaObservation(val id:String,val scope:String,val played:Boolean,val positionMs:Long,val eventPositionMs:Long=positionMs,val at:Long=System.currentTimeMillis())
data class DeliveryRecord(val id:String=UUID.randomUUID().toString(),val torboxId:String,val torboxAccount:String,val jellyfinAccount:String?,val media:MediaItem?=null,val episode:Episode?=null,val hash:String?=null,val releaseName:String="",val phase:String="added",val note:String="Confirmed TorBox identifier recorded.",val at:Long=System.currentTimeMillis(),val updatedAt:Long=at,val zurgPath:String?=null,val jellyfinId:String?=null,val scanRequestedAt:Long?=null)
data class IntelligenceData(val version:Int=1,val recording:Boolean=true,val startedAt:Long=System.currentTimeMillis(),val events:List<MediaEvent> = emptyList(),val observations:List<MediaObservation> = emptyList(),val deliveries:List<DeliveryRecord> = emptyList(),val lastSync:Map<String,Long> = emptyMap())
fun observeMedia(data:IntelligenceData,scope:String,items:List<MediaItem>,now:Long):IntelligenceData {
    val seen=data.observations.associateBy{it.scope+":"+it.id}.toMutableMap();val events=data.events.toMutableList()
    items.distinctBy{it.jellyfinId}.forEach{item->val id=item.jellyfinId ?: return@forEach;if(item.jellyfinUserDataKnown!=true)return@forEach;val key="$scope:$id";val old=seen[key]
        val changed=old!=null&&(old.played!=item.watched||kotlin.math.abs(old.eventPositionMs-item.positionMs)>=30_000)
        if(changed&&data.recording)events+=MediaEvent(scope=scope,kind=if(old!!.played!=item.watched)if(item.watched)"Watched" else "Unwatched" else "Progress changed",itemId=id,title=item.name.take(160),at=now,detail=if(old!!.played==item.watched)"Position ${item.positionMs/1000}s; observation is not measured watch time." else "Jellyfin user state",source="Jellyfin observed")
        seen[key]=MediaObservation(id,scope,item.watched,item.positionMs,if(changed||old==null)item.positionMs else old.eventPositionMs,now)
    }
    return data.copy(events=events.takeLast(1200),observations=seen.values.sortedBy{it.at}.takeLast(2000))
}
class IntelligenceStore(private val db:AppDatabase,private val key:String="media_intelligence_v1") {
    companion object{private val writes=Mutex();fun get(settings:SettingsStore=NativeRuntime.settings)=IntelligenceStore(DatabaseProvider.get(settings.applicationContext),settings.storageNamespace+"media_intelligence_v1")}
    private val gson=Gson()
    private fun decode(raw:String?):IntelligenceData=if(raw==null)IntelligenceData() else gson.fromJson(raw,IntelligenceData::class.java).also{require(it.version==1){"Unsupported media journal version; existing records were kept."}}
    val flow=db.preferenceDao().observe(key).map{decode(it)}.flowOn(Dispatchers.IO)
    suspend fun read()=withContext(Dispatchers.IO){decode(db.preferenceDao().get(key))}
    suspend fun update(change:(IntelligenceData)->IntelligenceData):IntelligenceData=writes.withLock{db.withTransaction{
        var next=change(decode(db.preferenceDao().get(key)))
        next=next.copy(events=next.events.takeLast(1200),observations=next.observations.takeLast(2000),deliveries=next.deliveries.sortedByDescending{it.at}.take(100))
        var raw=gson.toJson(next)
        while(raw.toByteArray(Charsets.UTF_8).size>1_000_000&&(next.events.isNotEmpty()||next.observations.isNotEmpty())){next=next.copy(events=next.events.drop(100),observations=next.observations.drop(100));raw=gson.toJson(next)}
        db.preferenceDao().set(PreferenceEntity(key,raw));next
    }}
    suspend fun observe(scope:String,items:List<MediaItem>)=update{observeMedia(it,scope,items,System.currentTimeMillis()).copy(lastSync=it.lastSync+(scope to System.currentTimeMillis()))}
    suspend fun added(id:String,account:String,scope:String?,media:MediaItem?=null,episode:Episode?=null,hash:String?=null,name:String=""){require(id.matches(Regex("\\d+")));update{data->
        val compact=media?.copy(overview=null,background=null,poster=null,aliases=emptyList(),genres=emptyList(),logo=null)
        val old=data.deliveries.firstOrNull{it.torboxId==id&&it.torboxAccount==account}
        val record=old?.copy(media=compact ?: old.media,episode=episode ?: old.episode,hash=hash ?: old.hash,releaseName=name.ifBlank{old.releaseName}) ?: DeliveryRecord(torboxId=id,torboxAccount=account,jellyfinAccount=scope,media=compact,episode=episode,hash=hash,releaseName=name)
        data.copy(deliveries=(data.deliveries.filterNot{it.id==record.id}+record),events=if(old!=null||!data.recording)data.events else data.events+MediaEvent(scope=scope ?: account,kind="TorBox added",itemId=id,title=(media?.name ?: name).take(160),detail="Confirmed TorBox item $id"))
    }}
    suspend fun delivery(record:DeliveryRecord)=update{data->val old=data.deliveries.firstOrNull{it.id==record.id};data.copy(deliveries=data.deliveries.map{if(it.id==record.id)record else it},events=if(data.recording&&record.phase=="jellyfin"&&old?.phase!="jellyfin")data.events+MediaEvent(scope=record.jellyfinAccount ?: record.torboxAccount,kind="Jellyfin indexed",itemId=record.jellyfinId.orEmpty(),title=(record.media?.name ?: record.releaseName).take(160),detail="File identity matched to indexed metadata") else data.events)}
}
