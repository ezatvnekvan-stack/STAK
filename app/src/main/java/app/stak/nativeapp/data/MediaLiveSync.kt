package app.stak.nativeapp.data

import android.content.Context
import android.net.*
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit

data class LiveSyncState(val status:String="Not connected",val connected:Boolean=false,val scope:String="",val lastMessageAt:Long=0,val lastSuccessAt:Long=0,val revision:Long=0)
data class SyncSignal(val reasons:Set<String> = emptySet(),val ids:Set<String> = emptySet())
fun syncBackoff(attempt:Int):Long=(2_000L*(1L shl attempt.coerceIn(0,8))).coerceAtMost(300_000L)
fun syncMessage(text:String,user:String):SyncSignal? {
    if(text.length>1_000_000)return SyncSignal(setOf("library"))
    val obj=runCatching{JsonParser.parseString(text).asJsonObject}.getOrNull() ?: return null
    val type=obj.text("MessageType") ?: return null
    val data=obj.objectOrNull("Data")
    if(type=="UserDataChanged"&&data?.text("UserId")!=null&&!sameUser(data.text("UserId"),user))return null
    return when(type){
        "UserDataChanged"->SyncSignal(setOf("userdata"),data?.objects("UserDataList")?.mapNotNull{it.text("ItemId")?.takeIf{id->id.matches(Regex("[A-Za-z0-9-]{1,128}"))}}?.take(100)?.toSet().orEmpty())
        "LibraryChanged"->SyncSignal(setOf("library"))
        "Sessions","Playstate","RefreshProgress","ScheduledTasksInfo","ScheduledTaskEnded"->SyncSignal(setOf("session"))
        "UserUpdated","UserDeleted"->SyncSignal(setOf("permissions"))
        "ServerRestarting","ServerShuttingDown"->SyncSignal(setOf("restart"))
        else->null
    }
}
object MediaLiveSync {
    private val appScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    @Volatile private var generation=0L
    private var job:Job?=null;private var socket:WebSocket?=null;private var callback:ConnectivityManager.NetworkCallback?=null;private var network:ConnectivityManager?=null
    private val mutable=MutableStateFlow(LiveSyncState());val state:StateFlow<LiveSyncState> = mutable
    private val pulse=MutableSharedFlow<Unit>(extraBufferCapacity=16);val signals:SharedFlow<Unit> = pulse
    private var pending=SyncSignal()
    fun trigger(reason:String,ids:Set<String> = emptySet()){synchronized(this){pending=SyncSignal(pending.reasons+reason,(pending.ids+ids).take(100).toSet())};pulse.tryEmit(Unit)}
    fun consume():SyncSignal=synchronized(this){pending.also{pending=SyncSignal()}}
    fun success(scope:String){if(mutable.value.scope==scope)mutable.update{it.copy(lastSuccessAt=System.currentTimeMillis())}}
    fun stop(){generation++;job?.cancel();job=null;socket?.cancel();socket=null;callback?.let{runCatching{network?.unregisterNetworkCallback(it)}};callback=null;network=null;mutable.update{it.copy(status=if(it.scope.isBlank())"Not connected" else "Paused in background",connected=false)}}
    fun start(context:Context){
        stop();val epoch=generation;val settings=NativeRuntime.settings
        if(!settings.jellyfinConnected){mutable.value=LiveSyncState();return}
        val scope=jellyfinScope(settings);val server=settings.jellyfinUrl.orEmpty();val token=settings.jellyfinToken.orEmpty();val user=settings.jellyfinUserId.orEmpty();val device=settings.jellyfinDeviceId
        mutable.value=LiveSyncState(status="Connecting",scope=scope,lastSuccessAt=mutable.value.lastSuccessAt.takeIf{mutable.value.scope==scope} ?: 0)
        val changes=Channel<Unit>(Channel.CONFLATED)
        network=context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        callback=object:ConnectivityManager.NetworkCallback(){override fun onAvailable(n:Network){changes.trySend(Unit)};override fun onLost(n:Network){if(epoch!=generation)return;socket?.cancel();mutable.update{it.copy(status="Network changed · reconnecting",connected=false)};changes.trySend(Unit)}}
        runCatching{network?.registerDefaultNetworkCallback(callback!!)}
        job=appScope.launch {
            val reconcile=launch{while(isActive){delay(180_000);if(scope==jellyfinScope())trigger("reconcile")}}
            try{var attempt=0
                while(isActive&&epoch==generation&&scope==jellyfinScope()&&settings.jellyfinConnected){
                    val ended=CompletableDeferred<Unit>();var rejected=false;val started=System.currentTimeMillis()
                    val url=(DirectJellyfin.normalizeServer(server)+"/socket").toHttpUrl().newBuilder().addQueryParameter("api_key",token).addQueryParameter("deviceId",device).build()
                    val client=DirectJellyfin.client.newBuilder().pingInterval(30,TimeUnit.SECONDS).build()
                    val connection=client.newWebSocket(Request.Builder().url(url).build(),object:WebSocketListener(){
                        override fun onOpen(ws:WebSocket,response:Response){if(epoch!=generation||scope!=jellyfinScope()){ws.cancel();return};mutable.update{it.copy(status="Live connection active",connected=true)};trigger("connected")}
                        override fun onMessage(ws:WebSocket,text:String){
                            if(epoch!=generation||scope!=jellyfinScope())return
                            if(text.length>1_000_000){trigger("library");return}
                            val type=runCatching{JsonParser.parseString(text).asJsonObject.text("MessageType")}.getOrNull()
                            if(type=="ForceKeepAlive")ws.send("""{"MessageType":"KeepAlive"}""")
                            mutable.update{it.copy(lastMessageAt=System.currentTimeMillis(),revision=it.revision+1)}
                            syncMessage(text,user)?.let{signal->signal.reasons.forEach{trigger(it,signal.ids)}}
                        }
                        override fun onClosed(ws:WebSocket,code:Int,reason:String){ended.complete(Unit)}
                        override fun onFailure(ws:WebSocket,t:Throwable,response:Response?){rejected=response?.code in listOf(401,403);ended.complete(Unit)}
                    })
                    if(epoch!=generation){connection.cancel();break};socket=connection
                    try{ended.await()}finally{connection.cancel();if(epoch==generation)socket=null}
                    if(epoch!=generation)break
                    if(rejected){mutable.update{it.copy(status="Socket unavailable · API reconciliation continues",connected=false)};awaitCancellation()}
                    if(System.currentTimeMillis()-started>60_000)attempt=0
                    mutable.update{it.copy(status="Reconnecting · REST reconciliation remains available",connected=false)}
                    withTimeoutOrNull(syncBackoff(attempt++)){changes.receive()}
                }
            }finally{reconcile.cancel();if(epoch==generation){socket?.cancel();socket=null}}
        }
    }
}
