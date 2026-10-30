package app.stak.nativeapp.data

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** Foreground verification of already-added items, never acquisition or quality automation. */
object DeliveryMonitor {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val requests=Channel<Unit>(Channel.CONFLATED)
    private var job:Job?=null
    fun request(){requests.trySend(Unit)}
    fun stop(){job?.cancel();job=null}
    fun start(){
        stop();val settings=NativeRuntime.settings
        if(settings.torboxKey.isNullOrBlank())return
        val account=torboxScope(settings)
        job=scope.launch {
            delay(1500)
            while(isActive&&account==torboxScope(settings)){
                try {
                    val now=System.currentTimeMillis();val config=readZurg(settings)
                    val records=IntelligenceStore.get(settings).read().deliveries.filter{
                        it.torboxAccount==account&&it.phase !in setOf("jellyfin","failed")&&now-it.at<48*60*60*1000L&&
                        (it.phase=="added"||now-it.updatedAt>120000)&&!(it.phase=="torbox"&&config.endpoint.isBlank())
                    }.sortedBy{it.updatedAt}.take(2)
                    for(record in records){ensureActive();withTimeoutOrNull(60000){DeliveryTracking.check(record)}}
                }catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){/* Next foreground reconciliation retries without discarding stored evidence. */}
                withTimeoutOrNull(120000){requests.receive();delay(1500)}
            }
        }
    }
}
