package app.stak.nativeapp.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.security.MessageDigest
import java.util.LinkedHashMap

/** Public TMDb JSON only. Credentials are hashed into the key, never stored on disk.
 * Short TTL keeps new episode/release data fresh. No account/playback/source cache. */
internal object MetadataResponseCache {
    private const val TTL=15*60*1000L
    private const val LIMIT=192
    private var memoryBytes=0L
    private val memory=LinkedHashMap<String,Pair<Long,String>>(32,.75f,true)
    fun key(url:String,params:Map<String,String>,bearer:String?):String? {
        if(!url.startsWith("https://api.themoviedb.org/3/"))return null
        val material=url+"\n"+params.toSortedMap().entries.joinToString("\n"){"${it.key}=${it.value}"}+"\n"+bearer.orEmpty()
        return MessageDigest.getInstance("SHA-256").digest(material.toByteArray()).joinToString(""){"%02x".format(it)}
    }
    private fun file(key:String)=PublicMetadataCache.directory?.resolve("tmdb")?.resolve("$key.json")
    @Synchronized fun read(key:String):JsonObject?=try {
        val now=System.currentTimeMillis()
        val cached=memory[key]?.takeIf{now-it.first in 0 until TTL}?.second
        val disk=if(cached==null)file(key)?.takeIf{it.isFile&&now-it.lastModified() in 0 until TTL&&it.length()<=4*1024*1024}?.readText() else null
        (cached?:disk)?.let{JsonParser.parseString(it).asJsonObject}
    }catch(_:Exception){null}
    @Synchronized fun write(key:String,value:JsonObject){
        val json=value.toString();if(json.length>4*1024*1024)return
        memory.remove(key)?.let{memoryBytes-=it.second.length*2L}
        memory[key]=System.currentTimeMillis() to json;memoryBytes+=json.length*2L
        while(memory.size>LIMIT||memoryBytes>16L*1024*1024){
            val removed=memory.remove(memory.keys.first()) ?: break
            memoryBytes-=removed.second.length*2L
        }
        try {
            val target=file(key)?:return
            target.parentFile?.mkdirs()
            val temporary=File(target.path+".tmp");temporary.writeText(json)
            try { java.nio.file.Files.move(temporary.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE) }
            catch(_:java.nio.file.AtomicMoveNotSupportedException){java.nio.file.Files.move(temporary.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING)}
            val files=target.parentFile?.listFiles().orEmpty().filter{it.extension=="json"}.sortedByDescending{it.lastModified()}
            var bytes=0L
            files.forEachIndexed{i,f->bytes+=f.length();if(i>=LIMIT||bytes>24*1024*1024)f.delete()}
        }catch(_:Exception){/* cache failure must not fail a provider request */}
    }
}
