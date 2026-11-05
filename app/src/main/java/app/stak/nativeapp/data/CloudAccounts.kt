package app.stak.nativeapp.data

import android.content.Context
import androidx.work.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import java.util.concurrent.TimeUnit

data class CloudAccountState(val configured:Boolean=false,val username:String?=null,val busy:Boolean=false,val savedAt:Long=0,val message:String?=null)
data class CloudSession(val auth:CloudAuth,val key:String,val backend:String,val revision:Long=0,val fingerprint:String="",val savedAt:Long=0)
internal object CloudAccountLock {val mutex=Mutex()}

@OptIn(FlowPreview::class,kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CloudAccountController(
    private val settings:SettingsStore,
    private val snapshots:CloudSnapshotStore=CloudSnapshotStore(settings),
    private val gateway:CloudAccountGateway=CloudAccountApi(),
    private val scope:CoroutineScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
) {
    private val mutable=MutableStateFlow(CloudAccountState(gateway.config.configured));val state:StateFlow<CloudAccountState> = mutable
    private var started=false
    private fun session():CloudSession? {
        val raw=settings.cloudValue("cloud_session")?:return null
        val value=Gson().fromJson(raw,CloudSession::class.java)
        require(value.auth.userId.isNotBlank()&&value.auth.accessToken.isNotBlank()&&value.auth.refreshToken.isNotBlank());UUID.fromString(value.auth.userId)
        require(value.backend==gateway.config.scope&&Base64.getDecoder().decode(value.key).size==32&&value.revision>=0)
        return value
    }
    private fun store(value:CloudSession){settings.cloudValue("cloud_session",Gson().toJson(value))}
    private fun bind(value:CloudSession){settings.cloudValue("cloud_bound_user",value.backend+":"+value.auth.userId);settings.cloudValue("cloud_bound_name",value.auth.username)}
    private fun show(busy:Boolean=false,message:String?=null){
        val saved=runCatching{session()}.getOrNull()
        mutable.value=CloudAccountState(gateway.config.configured,saved?.auth?.username,busy,saved?.savedAt?:0,message)
    }
    private fun failure(error:Throwable)=when(error){is CloudFailure->error.safeMessage;is CloudInputException->error.message.orEmpty();else->"Could not finish. Your data is still on this device."}
    suspend fun recoverPending()=withContext(Dispatchers.IO){CloudAccountLock.mutex.withLock{snapshots.recoverPending();show()}}
    fun start(){
        if(started)return;started=true;show()
        if(!gateway.config.configured)return
        val settingChanges=callbackFlow<Unit>{val close=settings.listenForBackup{trySend(Unit)};trySend(Unit);awaitClose{close()}}
        val dataChanges=snapshots.changes()
        scope.launch{merge(settingChanges,dataChanges).debounce(2500).collect{
            if(runCatching{session()}.getOrNull()!=null){schedule();syncNow(false)}
        }}
        if(runCatching{session()}.getOrNull()!=null)schedule()
    }
    private fun schedule(){
        if(settings.storageNamespace.isNotEmpty())return // isolated test stores never enqueue production workers
        val work=WorkManager.getInstance(settings.applicationContext)
        val constraints=Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        work.enqueueUniquePeriodicWork("stak-account-backup-periodic",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<CloudBackupWorker>(15,TimeUnit.MINUTES).setConstraints(constraints).build())
        work.enqueueUniqueWork("stak-account-backup-soon",ExistingWorkPolicy.REPLACE,OneTimeWorkRequestBuilder<CloudBackupWorker>().setInitialDelay(6,TimeUnit.SECONDS).setConstraints(constraints).build())
    }
    private fun usable(value:CloudSession):CloudSession {
        if(value.auth.expiresAt>System.currentTimeMillis()+60_000)return value
        return value.copy(auth=gateway.refresh(value.auth)).also(::store)
    }
    private fun verified(remote:CloudRemote,key:ByteArray,userId:String):CloudSnapshot {
        require(remote.revision>0&&remote.envelope!=null)
        return validateCloudSnapshot(CloudBackupCodec.open(remote.envelope,key,userId)).also{require(it.fingerprint==remote.fingerprint)}
    }
    fun signIn(username:String,password:String,create:Boolean,onRestored:()->Unit={}){
        if(mutable.value.busy)return
        mutable.value=mutable.value.copy(busy=true,message=null)
        scope.launch{
            var restored=false
            CloudAccountLock.mutex.withLock{
                var provisional:CloudAuth?=null
                try{
                    snapshots.recoverPending()
                    val name=CloudBackupCodec.username(username);CloudBackupCodec.password(password)
                    if(create&&settings.cloudValue("cloud_bound_name")?.let{it!=name}==true)throw CloudFailure("binding","This device already has data from another account. Sign in to that account.")
                    val auth=gateway.authenticate(name,password,create);provisional=auth
                    val key=CloudBackupCodec.key(password.toCharArray(),auth.userId)
                    val remote=gateway.read(auth)
                    val candidate=CloudSession(auth,Base64.getEncoder().encodeToString(key),gateway.config.scope,remote.revision,remote.fingerprint.orEmpty(),remote.savedAt)
                    if(remote.revision>0){
                        val snapshot=verified(remote,key,auth.userId)
                        snapshots.restore(snapshot){store(candidate);bind(candidate)}
                        restored=true
                    }else{
                        val bound=settings.cloudValue("cloud_bound_user")
                        if(bound!=null&&bound!=candidate.backend+":"+auth.userId)throw CloudFailure("binding","This device already has data from another account. Sign in to that account.")
                        // Capture before binding. Never replace an existing server backup on login.
                        val snapshot=snapshots.capture();val envelope=CloudBackupCodec.seal(snapshot.json,key,auth.userId)
                        val ack=gateway.write(auth,0,snapshot.fingerprint,envelope,UUID.randomUUID().toString())
                        require(ack.revision==1L&&ack.fingerprint==snapshot.fingerprint)
                        store(candidate.copy(revision=1,fingerprint=snapshot.fingerprint,savedAt=ack.savedAt));bind(candidate)
                    }
                    provisional=null;show();schedule()
                }catch(cancelled:CancellationException){throw cancelled}
                catch(error:Throwable){provisional?.let{runCatching{gateway.logout(it)}};show(message=failure(error))}
            }
            if(restored)withContext(Dispatchers.Main){onRestored()}
        }
    }
    suspend fun syncNow(checkRemote:Boolean=true):Boolean=withContext(Dispatchers.IO){CloudAccountLock.mutex.withLock{
        try{
            if(!gateway.config.configured)return@withLock true
            snapshots.recoverPending()
            var saved=session()?:return@withLock true
            require(settings.cloudValue("cloud_bound_user")==saved.backend+":"+saved.auth.userId)
            val snapshot=snapshots.capture()
            if(!checkRemote&&snapshot.fingerprint==saved.fingerprint)return@withLock true
            saved=usable(saved);val key=Base64.getDecoder().decode(saved.key)
            val remote=gateway.read(saved.auth)
            if(remote.revision>0)verified(remote,key,saved.auth.userId)
            if(remote.revision>0&&remote.fingerprint==snapshot.fingerprint){
                store(saved.copy(revision=remote.revision,fingerprint=remote.fingerprint,savedAt=remote.savedAt));show();return@withLock true
            }
            if(remote.revision!=saved.revision||remote.fingerprint.orEmpty()!=saved.fingerprint)throw CloudFailure("conflict","A newer backup exists. Sign in again to restore it.")
            if(snapshot.fingerprint==saved.fingerprint)return@withLock true
            val ack=gateway.write(saved.auth,remote.revision,snapshot.fingerprint,CloudBackupCodec.seal(snapshot.json,key,saved.auth.userId),UUID.randomUUID().toString())
            require(ack.revision==remote.revision+1&&ack.fingerprint==snapshot.fingerprint)
            store(saved.copy(revision=ack.revision,fingerprint=ack.fingerprint,savedAt=ack.savedAt));show();true
        }catch(cancelled:CancellationException){throw cancelled}catch(error:Throwable){show(message=failure(error));false}
    }}
    fun signOut(){
        if(mutable.value.busy)return
        mutable.value=mutable.value.copy(busy=true)
        scope.launch{CloudAccountLock.mutex.withLock{
            val previous=runCatching{session()}.getOrNull()
            try{settings.cloudValue("cloud_session",null);show();if(settings.storageNamespace.isEmpty()){WorkManager.getInstance(settings.applicationContext).cancelUniqueWork("stak-account-backup-soon");WorkManager.getInstance(settings.applicationContext).cancelUniqueWork("stak-account-backup-periodic")};previous?.let{runCatching{gateway.logout(it.auth)}}}
            catch(error:Throwable){show(message=failure(error))}
        }}
    }
    companion object{
        @Volatile private var instance:CloudAccountController?=null
        fun get(context:Context):CloudAccountController=instance?:synchronized(this){instance?:CloudAccountController(SettingsStore(context.applicationContext)).also{instance=it}}
    }
}
class CloudBackupWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters){
    override suspend fun doWork():Result=if(CloudAccountController.get(applicationContext).syncNow())Result.success()else if(runAttemptCount<3)Result.retry()else Result.failure()
}
