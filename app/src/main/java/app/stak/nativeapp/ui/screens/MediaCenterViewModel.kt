package app.stak.nativeapp.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stak.nativeapp.data.*
import com.google.gson.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class MediaCenterViewModel:ViewModel(){
    val settings=NativeRuntime.settings
    val store=IntelligenceStore.get(settings)
    private val _error=MutableStateFlow<String?>(null);val error:StateFlow<String?> = _error
    private val _message=MutableStateFlow<String?>(null);val message:StateFlow<String?> = _message
    private val _busy=MutableStateFlow(false);val busy:StateFlow<Boolean> = _busy
    val journal=store.flow.catch{_error.value="Could not read the local integration journal. Existing records were not replaced."}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),IntelligenceData())
    val dashboard=MutableStateFlow<JellyfinDashboard?>(null)
    val rows=MutableStateFlow<List<JsonObject>>(emptyList());val total=MutableStateFlow<Int?>(null)
    val folder=MutableStateFlow<String?>(null);private val parents=mutableListOf<Pair<String?,String>>()
    val folderName=MutableStateFlow("Libraries")
    val dav=MutableStateFlow<DavListing?>(null);val davPath=MutableStateFlow<String?>(null);private val davParents=mutableListOf<String>()
    val davCheckedAt=MutableStateFlow<Long?>(lastDavHealth(settings)?.successfulAt)
    val davLatency=MutableStateFlow<Long?>(lastDavHealth(settings)?.latencyMs)
    private fun checkedDav(value:DavListing){val at=System.currentTimeMillis();davCheckedAt.value=at;davLatency.value=value.latencyMs;settings.zurgHealthJson=Gson().toJson(DavHealth(davHealthScope(readZurg(settings)),at,value.latencyMs))}
    val quality=MutableStateFlow<QualityReport?>(null)
    fun action(work:suspend()->Unit){if(_busy.value)return;_busy.value=true;_error.value=null;_message.value=null;viewModelScope.launch{try{work()}catch(cancelled:CancellationException){throw cancelled}catch(error:Exception){_error.value=when(error){is JellyfinHttpFailure->error.message;is UnconfirmedTorrentMutation->error.message;is DavHttpFailure->error.message;is IllegalArgumentException->error.message?.take(180);else->"Could not complete this check. Verify the configured endpoint, network/VPN and permissions."}}finally{_busy.value=false}}}
    fun clearMessage(){_error.value=null;_message.value=null}
    fun refreshServer()=action{dashboard.value=JellyfinIntelligence.dashboard()}
    fun browse(id:String?=null,name:String="Libraries",push:Boolean=false)=action{
        if(push)parents+=folder.value to folderName.value
        folder.value=id;folderName.value=name
        rows.value=if(id==null)JellyfinIntelligence.views().map{JsonObject().apply{addProperty("Id",it.id);addProperty("Name",it.name);addProperty("Type","CollectionFolder")}}else JellyfinIntelligence.browse(id).also{total.value=it.second}.first
    }
    fun parent(){if(parents.isEmpty()){browse();return};val old=parents.removeAt(parents.lastIndex);browse(old.first,old.second)}
    fun resetAccount(){dashboard.value=null;rows.value=emptyList();total.value=null;parents.clear();quality.value=null}
    fun more()=action{val next=JellyfinIntelligence.browse(folder.value,rows.value.size);rows.value=(rows.value+next.first).distinctBy{it.text("Id")};total.value=next.second}
    fun favorites()=action{rows.value=JellyfinIntelligence.favorites();total.value=rows.value.size}
    fun recent()=action{rows.value=JellyfinIntelligence.recent();total.value=rows.value.size}
    fun favorite(row:JsonObject)=action{val id=row.text("Id") ?: return@action;val old=row.objectOrNull("UserData")?.get("IsFavorite")?.asBoolean==true;JellyfinIntelligence.favorite(id,!old);rows.value=rows.value.map{if(it.text("Id")==id)it.deepCopy().apply{val data=objectOrNull("UserData") ?: JsonObject().also{add("UserData",it)};data.addProperty("IsFavorite",!old)}else it}}
    fun watched(row:JsonObject) = Unit
    fun inspect(id:String)=action{quality.value=verifiedQuality(JellyfinIntelligence.item(id))}
    fun command(session:ServerSession,command:String,value:Long?=null)=action{JellyfinIntelligence.command(session,command,value);_message.value="Command accepted by the server. Client execution is checked separately.";delay(1200);dashboard.value=JellyfinIntelligence.dashboard()}
    fun scan()=action{JellyfinIntelligence.scanLibraries();_message.value="Jellyfin accepted the library scan request. Indexing is not complete yet.";dashboard.value=JellyfinIntelligence.dashboard()}
    fun saveZurg(config:ZurgConnection)=action{val endpoint=davEndpoint(config.endpoint);require(config.mountPath.isBlank()||(config.mountPath.startsWith('/')&&config.mountPath.split('/').none{it==".."})){"Use an absolute Linux media path without '..'."};if(config.libraryId.isNotBlank())safeJellyfinId(config.libraryId)
        settings.zurgConnectionJson=Gson().toJson(config.copy(endpoint=endpoint.toString(),jellyfinAccount=if(settings.jellyfinConnected)jellyfinScope(settings)else ""));dav.value=null;davCheckedAt.value=null;davLatency.value=null;settings.zurgHealthJson=null;_message.value="Zurg configuration saved securely. Test WebDAV to verify reachability."
    }
    fun checkDav()=action{dav.value=WebDavClient().list(readZurg(settings),depth=0);checkedDav(dav.value!!);_message.value="WebDAV responded and accepted authentication. Linux filesystem mount health remains unverified."}
    fun openDav(path:String?=null,push:Boolean=false)=action{val config=readZurg(settings);val target=path ?: config.endpoint;val listing=WebDavClient().list(config,target);if(push)davPath.value?.let{davParents+=it};dav.value=listing;davPath.value=target;checkedDav(listing)}
    fun davParent()=action{val previous=davParents.lastOrNull() ?: readZurg(settings).endpoint;val listing=WebDavClient().list(readZurg(settings),previous);if(davParents.isNotEmpty())davParents.removeAt(davParents.lastIndex);dav.value=listing;davPath.value=previous;checkedDav(listing)}
    val davTorrent=MutableStateFlow<TorBoxTorrent?>(null)
    private var davTorrentScope=""
    fun resetDavTorrent(){davTorrent.value=null;davTorrentScope="";clearMessage()}
    fun findDavTorrent(entry:DavEntry)=action{davTorrent.value=null;val key=settings.torboxKey.orEmpty();val account=torboxScope(settings);val all=DirectServices.torrents(key);require(account==torboxScope(settings)){"TorBox account changed. Retry."};davTorrent.value=matchingTorBoxOwner(entry,all);davTorrentScope=account;if(davTorrent.value==null)_message.value="No unique TorBox file match. Manage this item from the TorBox library."}
    fun removeDavTorrent(torrent:TorBoxTorrent)=action{require(davTorrentScope==torboxScope(settings)&&davTorrent.value?.id==torrent.id){"Refresh the TorBox match before removing this item."};DirectTorBoxMutations().control(settings.torboxKey.orEmpty(),torrent.id,TorrentOperation.Delete);davTorrent.value=null;_message.value="Removed from TorBox. Zurg and Jellyfin may need time to refresh.";dav.value=null;val list=WebDavClient().list(readZurg(settings),davPath.value);dav.value=list;checkedDav(list)}
    fun connectDav(value:ZurgConnection)=action{val endpoint=davEndpoint(value.endpoint);settings.zurgConnectionJson=Gson().toJson(value.copy(endpoint=endpoint.toString()));settings.zurgHealthJson=null;dav.value=null;davParents.clear();davPath.value=endpoint.toString();val list=WebDavClient().list(readZurg(settings));dav.value=list;checkedDav(list)}
    fun device(value:PlaybackDevice)=action{settings.playbackDeviceJson=Gson().toJson(value);_message.value="Playback device preferences saved."}
    fun checkDelivery(record:DeliveryRecord,scan:Boolean=false)=action{DeliveryTracking.check(record,scan)}
    fun recording(enabled:Boolean)=action{store.update{it.copy(recording=enabled)}}
    fun clearJournal()=action{val account=if(settings.jellyfinConnected)jellyfinScope(settings)else torboxScope(settings);store.update{it.copy(events=it.events.filterNot{e->e.scope==account},observations=it.observations.filterNot{o->o.scope==account})}}
}
