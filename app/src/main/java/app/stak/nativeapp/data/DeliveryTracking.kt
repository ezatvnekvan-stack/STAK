package app.stak.nativeapp.data

import com.google.gson.Gson
import kotlinx.coroutines.CancellationException

fun readZurg(settings:SettingsStore=NativeRuntime.settings):ZurgConnection=runCatching{settings.zurgConnectionJson?.let{Gson().fromJson(it,ZurgConnection::class.java)}}.getOrNull() ?: ZurgConnection()
fun readPlaybackDevice(settings:SettingsStore=NativeRuntime.settings):PlaybackDevice=runCatching{settings.playbackDeviceJson?.let{Gson().fromJson(it,PlaybackDevice::class.java)}}.getOrNull() ?: PlaybackDevice()
fun mappedMediaPath(config:ZurgConnection,relative:String):String? {
    if(config.mountPath.isBlank())return null
    val segments=relative.replace('\\','/').split('/').filter{it.isNotEmpty()}
    if(segments.any{it=="."||it==".."||it.contains('\u0000')})return null
    return config.mountPath.trimEnd('/')+"/"+segments.joinToString("/")
}
fun matchDavFiles(torrent:TorBoxTorrent,entries:List<DavEntry>):DavEntry? {
    val files=entries.filter{!it.directory&&mediaFile(it.name)}
    val hash=torrent.hash?.lowercase()?.takeIf{it.matches(Regex("[a-f0-9]{40}"))}
    val exact=files.filter{entry->torrent.files.any{file->file.size>0&&entry.size==file.size&&file.name.replace('\\','/').substringAfterLast('/')==entry.name}}
    val scoped=if(hash!=null)exact.filter{entry->Regex("(?i)(?<![a-f0-9])${Regex.escape(hash)}(?![a-f0-9])").containsMatchIn(entry.relativePath)}else emptyList()
    return scoped.singleOrNull() ?: exact.singleOrNull()
}
data class DavMatches(val expected:Int,val files:List<DavEntry>,val ambiguous:Int){val complete get()=expected>0&&files.size==expected&&ambiguous==0}
fun matchDavPack(torrent:TorBoxTorrent,entries:List<DavEntry>):DavMatches {
    val expected=torrent.files.filter{mediaFile(it.name)&&it.size>0}.distinctBy{it.name to it.size}
    val matches=expected.map{file->matchDavFiles(torrent.copy(files=listOf(file)),entries)}
    val unique=matches.filterNotNull().distinctBy{it.url}
    return DavMatches(expected.size,unique,matches.count{it==null}+(matches.filterNotNull().size-unique.size))
}
fun indexedFileMatches(item:com.google.gson.JsonObject,torrent:TorBoxTorrent,entry:DavEntry,config:ZurgConnection):Boolean {
    val path=mappedMediaPath(config,entry.relativePath)
    return item.objects("MediaSources").any{source->
        val actual=source.text("Path")?.replace('\\','/') ?: return@any false
        if(path!=null)actual==path&&(entry.size==null||source.number("Size")?.toLong()==entry.size)
        else actual.substringAfterLast('/')==entry.name&&entry.size!=null&&entry.size>0&&source.number("Size")?.toLong()==entry.size&&torrent.files.count{it.name.replace('\\','/').substringAfterLast('/')==entry.name&&it.size==entry.size}==1
    }
}
interface DeliveryGateway {
    suspend fun torrents(key:String):List<TorBoxTorrent>
    suspend fun list(config:ZurgConnection,path:String?=null):DavListing
    suspend fun items(params:Map<String,String>):com.google.gson.JsonObject
    suspend fun scan()
}
object DirectDeliveryGateway:DeliveryGateway {
    override suspend fun torrents(key:String)=DirectServices.torrents(key)
    override suspend fun list(config:ZurgConnection,path:String?)=WebDavClient().list(config,path)
    override suspend fun items(params:Map<String,String>)=JellyfinIntelligence.items(params)
    override suspend fun scan()=JellyfinIntelligence.scanLibraries()
}
object DeliveryTracking {
    suspend fun record(id:String,key:String,media:MediaItem?=null,episode:Episode?=null,source:SourceRelease?=null){
        val settings=NativeRuntime.settings;val account=credentialScope("torbox:$key")
        if(account!=torboxScope(settings))return
        IntelligenceStore.get(settings).added(id,account,if(settings.jellyfinConnected)jellyfinScope(settings) else null,media,episode,source?.infoHash,source?.name.orEmpty());DeliveryMonitor.request()
    }
    suspend fun check(record:DeliveryRecord,requestScan:Boolean=false,gateway:DeliveryGateway=DirectDeliveryGateway):DeliveryRecord {
        val settings=NativeRuntime.settings;require(record.torboxAccount==torboxScope(settings)){"This addition belongs to another TorBox account."}
        val store=IntelligenceStore.get(settings);var next=record.copy(updatedAt=System.currentTimeMillis())
        try {
            val torrent=gateway.torrents(settings.torboxKey.orEmpty()).firstOrNull{it.id==record.torboxId}
                ?: return record.copy(phase="failed",note="TorBox did not return this item. It may have been removed.",updatedAt=System.currentTimeMillis()).also{store.delivery(it)}
            require(record.torboxAccount==torboxScope(settings)){"TorBox account changed. Retry this check."}
            if(!torrent.finished)return next.copy(phase="processing",note="TorBox state: ${torrent.state}. Completion has not been confirmed.").also{store.delivery(it)}
            next=next.copy(phase="torbox",note="TorBox download_finished is true. Zurg and Jellyfin are checked separately.")
            val config=readZurg(settings)
            if(config.endpoint.isBlank())return next.copy(note="Available in TorBox. Configure Zurg WebDAV to verify the next stage.").also{store.delivery(it)}
            if(config.jellyfinAccount.isNotBlank()&&config.jellyfinAccount!=jellyfinScope(settings))return next.copy(note="Relink Zurg settings to the current Jellyfin account before checking indexing.").also{store.delivery(it)}
            val root=gateway.list(config);val entries=root.entries.toMutableList()
            // User-initiated, bounded discovery: only matching torrent directories, never a recursive media download.
            val queue=java.util.ArrayDeque(root.entries.filter{it.directory&&(it.name==torrent.name||(torrent.hash!=null&&it.relativePath.contains(torrent.hash,true)))})
            val visited=mutableSetOf<String>();var folders=0
            while(queue.isNotEmpty()&&folders<30&&entries.size<10000){val folder=queue.removeFirst();if(!visited.add(folder.url))continue;folders++;val children=gateway.list(config,folder.url).entries;entries+=children;children.filter{it.directory}.forEach{queue.addLast(it)}}
            val visible=matchDavPack(torrent,entries)
            if(!visible.complete)return next.copy(note="${visible.files.size}/${visible.expected} TorBox media files uniquely matched in Zurg. Unmatched or ambiguous files are unverified. Discovery is limited to the root and 30 matching subfolders.").also{store.delivery(it)}
            next=next.copy(phase="zurg",zurgPath=visible.files.first().relativePath,note="All ${visible.expected} media files matched by filename + size through WebDAV. Linux mount health is unverified.")
            if(!settings.jellyfinConnected)return next.also{store.delivery(it)}
            if(record.jellyfinAccount!=null&&record.jellyfinAccount!=jellyfinScope(settings))return next.copy(note="This addition is associated with a different Jellyfin account.").also{store.delivery(it)}
            var scanNote=""
            if((requestScan||config.scanWhenVisible)&&(record.scanRequestedAt==null||(requestScan&&System.currentTimeMillis()-record.scanRequestedAt>60000))){
                try{gateway.scan();next=next.copy(scanRequestedAt=System.currentTimeMillis())}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){scanNote=" Scan acceptance was not confirmed; check administrator permission and server connectivity."}
            }
            val target=record.media;val imdb=target?.imdbId
            val candidates=mutableListOf<com.google.gson.JsonObject>()
            var parent=config.libraryId.takeIf{it.isNotBlank()}
            if(target?.isSeries==true&&imdb!=null){
                val series=gateway.items(mapOf("Recursive" to "true","IncludeItemTypes" to "Series","AnyProviderIdEquals" to "imdb.$imdb","Limit" to "100")+(parent?.let{mapOf("ParentId" to it)} ?: emptyMap())).objects("Items")
                parent=series.singleOrNull()?.text("Id") ?: parent
            }
            var offset=0;var more=true
            while(more&&offset<5000){
                val params=mapOf("Recursive" to "true","IncludeItemTypes" to "Movie,Episode","Fields" to "MediaSources,MediaStreams,Path,ProviderIds","Limit" to "200","StartIndex" to offset.toString())+(parent?.let{mapOf("ParentId" to it)} ?: emptyMap())+(if(target?.isSeries!=true&&imdb!=null)mapOf("AnyProviderIdEquals" to "imdb.$imdb")else emptyMap())
                val page=gateway.items(params);val rows=page.objects("Items");candidates+=rows;offset+=rows.size
                more=rows.isNotEmpty()&&(page.number("TotalRecordCount")?.let{offset<it} ?: (rows.size==200))
            }
            val indexed=visible.files.mapNotNull{file->candidates.distinctBy{it.text("Id")}.filter{indexedFileMatches(it,torrent,file,config)}.singleOrNull()}
            next=if(indexed.size==visible.expected)next.copy(phase="jellyfin",jellyfinId=indexed.first().text("Id"),note="All ${visible.expected} media files match indexed metadata for this user by ${if(config.mountPath.isBlank())"unique filename + size"else "mapped path + size"}. Playback and mount health remain unverified.") else next.copy(phase=if(next.scanRequestedAt!=null)"scan" else "awaiting",note="${indexed.size}/${visible.expected} exact files verified in Jellyfin. Existing title metadata alone is not proof of indexing.${if(more)" Search capped at 5,000 items; configure a narrower library."else ""}$scanNote")
            return next.also{store.delivery(it)}
        }catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){return next.copy(note="Check could not finish. Verify endpoint reachability, VPN and account permissions; previous confirmed stages are retained.").also{store.delivery(it)}}
    }
}
