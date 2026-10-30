package app.stak.nativeapp.data

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class PreparedSource(val source:SourceRelease,val material:NabMaterial)

/** Small, RAM-only private torrent cache, partitioned by account and indexer credentials. */
object SourceMaterials {
    private data class Entry(val at:Long,val material:NabMaterial,val bytes:Int)
    private val entries=linkedMapOf<String,Entry>()
    private val permits=Semaphore(3)
    private val indexerPermits=java.util.concurrent.ConcurrentHashMap<String,Semaphore>()
    private fun identity(source:SourceRelease,key:String,indexer:TorznabIndexer)=credentialScope("${credentialScope(key)}|${indexer.id}|${indexer.url}|${indexer.apiKey}|${source.sourceToken?:source.torrentFileUrl}")
    private fun material(source:SourceRelease,entry:NabMaterial):PreparedSource {
        val hash=when(entry){is NabMaterial.Torrent->torrentInfoHash(entry.upload);is NabMaterial.Magnet->entry.input.identity}
        if(!hash.matches(Regex("[a-f0-9]{40}")))throw NabFailure("No supported TorBox cache identifier was available.")
        if(source.infoHash!=null&&source.infoHash.lowercase()!=hash)throw NabFailure("Torrent metadata changed. Refresh sources before adding.")
        return PreparedSource(source.copy(infoHash=hash),entry)
    }
    suspend fun prepare(source:SourceRelease,key:String):PreparedSource=withContext(Dispatchers.Default){
        if(source.torznabIndexerId==null||source.torrentFileUrl==null){
            val input=parseMagnetInput(source.magnetUrl?:source.infoHash?:throw NabFailure("No torrent metadata was available."))
            return@withContext material(source,NabMaterial.Magnet(input))
        }
        val indexer=TorznabStore(NativeRuntime.settings).read().firstOrNull{it.id==source.torznabIndexerId&&it.enabled}?:throw NabFailure("This indexer is no longer enabled. Refresh sources.")
        val id=identity(source,key,indexer)
        synchronized(entries){entries.entries.removeAll{System.currentTimeMillis()-it.value.at>120000};entries[id]}?.let{return@withContext material(source,it.material)}
        permits.withPermit{indexerPermits.getOrPut(credentialScope(indexer.id+indexer.url+indexer.apiKey)){Semaphore(1)}.withPermit indexer@{
            synchronized(entries){entries[id]}?.let{return@indexer material(source,it.material)}
            currentCoroutineContext().ensureActive()
            val fetched=TorznabClient().torrent(indexer,source)
            currentCoroutineContext().ensureActive()
            val ready=material(source,fetched)
            val size=if(fetched is NabMaterial.Torrent)fetched.upload.bytes.size else (fetched as NabMaterial.Magnet).input.magnet.length
            synchronized(entries){
                entries[id]=Entry(System.currentTimeMillis(),fetched,size)
                while(entries.size>32||entries.values.sumOf{it.bytes}>16*1024*1024)entries.remove(entries.keys.first())
            };ready
        }}
    }
}

data class CachedSourceSnapshot(val releases:List<SourceRelease>,val hashes:Set<String>,val remaining:Int,val checked:Int,val metadataFailures:Int)

/** A search session verifies known hashes first, then eight metadata-only releases per user batch. */
class CachedSourceSession(private val key:String,private val check:suspend(List<String>,String)->Set<String>,private val prepare:suspend(SourceRelease,String)->PreparedSource=SourceMaterials::prepare){
    private val candidates=linkedMapOf<String,SourceRelease>()
    private val checked=mutableSetOf<String>();private val cached=mutableSetOf<String>();private val attempted=mutableSetOf<String>();private var failures=0
    fun accept(items:List<SourceRelease>){
        for(source in items.filter{it.canImport}){
            val id=source.sourceIdentity
            if(candidates.size<5000||id in candidates)candidates[id]=candidates[id]?.let{mergeSourceCandidates(listOf(it,source)).single()}?:source
        }
    }
    fun snapshot():CachedSourceSnapshot=CachedSourceSnapshot(mergeSourceCandidates(candidates.values.filter{it.infoHash in cached}),cached.toSet(),candidates.count{(id,s)->s.infoHash==null&&id !in attempted},checked.size,failures)
    suspend fun known(update:suspend(CachedSourceSnapshot)->Unit){
        val hashes=bestQualityOrder(candidates.values.toList(),emptySet(),false).mapNotNull{it.infoHash?.lowercase()}.filter{it.matches(Regex("[a-f0-9]{40}"))&&it !in checked}.distinct()
        for(batch in hashes.chunked(80)){
            currentCoroutineContext().ensureActive()
            val result=check(batch,key).intersect(batch.toSet());checked+=batch;cached+=result;update(snapshot())
        }
        update(snapshot())
    }
    suspend fun more(update:suspend(CachedSourceSnapshot)->Unit){
        val next=bestQualityOrder(candidates.filter{(id,s)->s.infoHash==null&&id !in attempted}.values.toList(),emptySet(),false).take(8)
        for(group in next.chunked(3)){
            val resolved=supervisorScope{group.map{source->async{
                val id=source.sourceIdentity
                try{id to prepare(source,key).source}catch(c:CancellationException){throw c}catch(_:Exception){id to null}
            }}.awaitAll()}
            for((id,source)in resolved){attempted+=id;if(source==null)failures++ else candidates[id]=source}
            known(update)
        }
    }
}
