package app.stak.nativeapp.data

import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ServerSession(val id:String,val title:String,val device:String,val client:String,val userId:String?,val paused:Boolean,val positionMs:Long,val durationMs:Long,val volume:Int?,val canControl:Boolean,val canSeek:Boolean,val canVolume:Boolean,val canQueue:Boolean,val playMethod:String?,val reason:String)
data class JellyfinDashboard(val name:String="Jellyfin",val version:String="",val admin:Boolean=false,val sessions:List<ServerSession> = emptyList(),val libraries:List<JellyfinCollection> = emptyList(),val scanState:String="Unavailable",val issues:List<String> = emptyList())
private fun JsonObject.flag(key:String)=get(key)?.takeIf{it.isJsonPrimitive}?.asBoolean==true
fun safeJellyfinId(id:String):String {require(id.matches(Regex("[A-Za-z0-9-]{1,128}"))){"Invalid Jellyfin identifier."};return id}
fun sameUser(a:String?,b:String?)=a!=null&&b!=null&&a.replace("-","").equals(b.replace("-",""),true)
fun serverSession(raw:JsonObject,user:String,otherControl:Boolean):ServerSession {
    val state=raw.objectOrNull("PlayState");val item=raw.objectOrNull("NowPlayingItem");val caps=raw.objectOrNull("Capabilities")
    val commands=listOf(raw,caps).filterNotNull().flatMap{it.get("SupportedCommands")?.takeIf{it.isJsonArray}?.asJsonArray?.mapNotNull{c->c.takeIf{it.isJsonPrimitive}?.asString}.orEmpty()}.toSet()
    val control=raw.flag("SupportsRemoteControl")&&(raw.flag("SupportsMediaControl")||caps?.flag("SupportsMediaControl")==true)&&(sameUser(raw.text("UserId"),user)||otherControl)
    val queue=(raw.get("NowPlayingQueue")?.takeIf{it.isJsonArray}?.asJsonArray?.size() ?: 0)>1
    return ServerSession(safeJellyfinId(raw.text("Id").orEmpty()),item?.text("SeriesName")?.let{"$it · ${item.text("Name").orEmpty()}"} ?: item?.text("Name") ?: "Nothing playing",raw.text("DeviceName") ?: "Unknown device",raw.text("Client") ?: "Unknown client",raw.text("UserId"),state?.flag("IsPaused")==true,((state?.number("PositionTicks") ?: 0.0)/10000).toLong().coerceAtLeast(0),((item?.number("RunTimeTicks") ?: 0.0)/10000).toLong().coerceAtLeast(0),state?.number("VolumeLevel")?.toInt()?.coerceIn(0,100),control&&item!=null,control&&state?.flag("CanSeek")==true&&item?.number("RunTimeTicks")!=null,control&&"SetVolume" in commands,control&&queue,state?.text("PlayMethod"),if(control)"Commands depend on this client's advertised capabilities." else "Remote control is not advertised by this client or permitted for this user.")
}
object JellyfinIntelligence {
    private val mutations=Mutex()
    private val settings get()=NativeRuntime.settings
    suspend fun item(id:String):JsonObject {val user=safeJellyfinId(settings.jellyfinUserId.orEmpty());val target=safeJellyfinId(id);return DirectJellyfin.compatible("/Users/$user/Items/$target","/Items/$target",mapOf("UserId" to user,"Fields" to "MediaSources,MediaStreams,ProviderIds,Path,Genres,UserData")).asJsonObject}
    suspend fun items(params:Map<String,String>):JsonObject=DirectJellyfin.sessionRequest("/Items",params+mapOf("UserId" to safeJellyfinId(settings.jellyfinUserId.orEmpty()),"EnableUserData" to "true"),expectedScope=jellyfinScope())
    suspend fun views():List<JellyfinCollection>{val user=safeJellyfinId(settings.jellyfinUserId.orEmpty());return DirectJellyfin.compatible("/Users/$user/Views","/UserViews",mapOf("UserId" to user)).asJsonObject.objects("Items").mapNotNull{row->row.text("Id")?.let{JellyfinCollection(safeJellyfinId(it),row.text("Name") ?: "Library","library")}}}
    suspend fun browse(parent:String?,offset:Int=0):Pair<List<JsonObject>,Int?> {
        require(offset>=0)
        val params=mapOf("Recursive" to if(parent==null)"true" else "false","SortBy" to "SortName","StartIndex" to offset.toString(),"Limit" to "200","Fields" to "MediaSources,MediaStreams,ProviderIds,Path,Genres,UserData,DateCreated")+(parent?.let{mapOf("ParentId" to safeJellyfinId(it))} ?: mapOf("IncludeItemTypes" to "Movie,Series"))
        val data=items(params);return data.objects("Items") to data.number("TotalRecordCount")?.toInt()
    }
    suspend fun favorites():List<JsonObject> = loadLibraryPages(identity={it.text("Id").orEmpty()},fetch={offset,limit->
        val page=items(mapOf("Recursive" to "true","Filters" to "IsFavorite","Limit" to limit.toString(),"StartIndex" to offset.toString(),"Fields" to "UserData,MediaSources,MediaStreams,ProviderIds","SortBy" to "SortName"))
        LibraryPage(page.objects("Items"),page.number("TotalRecordCount")?.toInt())
    })
    suspend fun recent():List<JsonObject> = items(mapOf("Recursive" to "true","IncludeItemTypes" to "Movie,Episode","Limit" to "30","Fields" to "UserData,ProviderIds,MediaSources,MediaStreams","SortBy" to "DateCreated","SortOrder" to "Descending")).objects("Items")
    suspend fun policy():JsonObject=DirectJellyfin.sessionRequest("/Users/${safeJellyfinId(settings.jellyfinUserId.orEmpty())}",expectedScope=jellyfinScope()).objectOrNull("Policy") ?: JsonObject()
    suspend fun dashboard():JellyfinDashboard=coroutineScope {
        val account=jellyfinScope()
        val issues=java.util.Collections.synchronizedList(mutableListOf<String>())
        val policy=try{policy()}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){issues+="User permissions unavailable.";JsonObject()}
        val info=async{try{DirectJellyfin.sessionRequest("/System/Info/Public",expectedScope=jellyfinScope())}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){issues+="Server info unavailable.";JsonObject()}}
        val sessions=async{try{val response=DirectJellyfin.sessionValue("/Sessions",mapOf("ActiveWithinSeconds" to "600"),expectedScope=jellyfinScope());require(response.isJsonArray);response.asJsonArray.mapNotNull{runCatching{it.asJsonObject.takeIf{row->policy.flag("IsAdministrator")||policy.flag("EnableRemoteControlOfOtherUsers")||sameUser(row.text("UserId"),settings.jellyfinUserId)}?.let{row->serverSession(row,settings.jellyfinUserId.orEmpty(),policy.flag("EnableRemoteControlOfOtherUsers"))}}.getOrNull()}}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){issues+="Sessions are unavailable or restricted for this user.";emptyList()}}
        val libraries=async{try{views()}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){issues+="Libraries unavailable.";emptyList()}}
        val scan=async{if(!policy.flag("IsAdministrator"))"Requires administrator permission" else try{DirectJellyfin.sessionValue("/ScheduledTasks",expectedScope=jellyfinScope()).asJsonArray.mapNotNull{it.takeIf{it.isJsonObject}?.asJsonObject}.firstOrNull{it.text("Key")=="RefreshLibrary"}?.let{task->listOfNotNull(task.text("State"),task.number("CurrentProgressPercentage")?.let{"${it.toInt()}%"}).joinToString(" · ")} ?: "Not reported"}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){"Scan status unavailable"}}
        val server=info.await();val devices=sessions.await();val views=libraries.await();val scanStatus=scan.await();require(account==jellyfinScope()){ "Jellyfin account changed. Retry this check." };JellyfinDashboard(server.text("ServerName") ?: "Jellyfin",server.text("Version").orEmpty(),policy.flag("IsAdministrator"),devices,views,scanStatus,issues.toList())
    }
    suspend fun favorite(id:String,enabled:Boolean)=mutations.withLock{val user=safeJellyfinId(settings.jellyfinUserId.orEmpty());val target=safeJellyfinId(id);DirectJellyfin.compatible("/Users/$user/FavoriteItems/$target","/UserFavoriteItems/$target",mapOf("UserId" to user),if(enabled)"POST" else "DELETE");MediaLiveSync.trigger("favorites")}
    suspend fun watched(id:String,played:Boolean) = Unit
    suspend fun scanLibraries(){require(policy().flag("IsAdministrator")){"Administrator permission is required for a library scan."};DirectJellyfin.sessionValue("/Library/Refresh",method="POST",expectedScope=jellyfinScope());MediaLiveSync.trigger("scan")}
    suspend fun command(session:ServerSession,command:String,value:Long?=null){
        require(session.canControl){"This client does not support remote control."};val scope=jellyfinScope();val id=safeJellyfinId(session.id)
        require(command in setOf("PlayPause","Stop","Seek","NextTrack","PreviousTrack","SetVolume"))
        // Re-read the selected session and user policy before mutating; do not trust a stale UI capability.
        val fresh=dashboard().sessions.firstOrNull{it.id==id} ?: error("This session is no longer active.")
        require(fresh.canControl){"Remote control is no longer available."}
        if(command=="SetVolume"){
            require(fresh.canVolume&&value!=null&&value in 0..100)
            val body=JsonObject().apply{addProperty("Name","SetVolume");addProperty("ControllingUserId",settings.jellyfinUserId);add("Arguments",JsonObject().apply{addProperty("Volume",value.toString())})}
            DirectJellyfin.sessionValue("/Sessions/$id/Command",body=body,expectedScope=scope)
        }else{
            if(command=="Seek")require(fresh.canSeek&&value!=null&&value>=0&&value<=fresh.durationMs)
            if(command in setOf("NextTrack","PreviousTrack"))require(fresh.canQueue){"The client did not report a controllable queue."}
            DirectJellyfin.sessionValue("/Sessions/$id/Playing/$command",params=mapOf("ControllingUserId" to settings.jellyfinUserId.orEmpty())+(if(command=="Seek")mapOf("SeekPositionTicks" to (value!!*10000).toString())else emptyMap()),method="POST",expectedScope=scope)
        }
    }
}
