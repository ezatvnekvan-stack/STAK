package app.stak.nativeapp.data

import kotlinx.coroutines.*
object SourceSearch {
    suspend fun search(query:NabQuery,onPartial:suspend(AddonSearchResult)->Unit={}):AddonSearchResult=supervisorScope{
        val settings=NativeRuntime.settings;val addons=StreamAddonStore(settings).all().filter{it.enabled};val indexers=TorznabStore(settings).all().filter{it.enabled}
        require(addons.isNotEmpty()||indexers.isNotEmpty()){ "Enable an addon or Torznab indexer in Search sources." }
        val addon=async{val result=if(addons.isEmpty())AddonSearchResult(emptyList())else try{AddonSourceClient({addons}).search(query.type,query.imdb,query.season,query.episode){onPartial(it)}}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){AddonSearchResult(emptyList(),listOf("Stremio addons could not finish searching. Check their configuration."))};onPartial(result);result}
        val nab=indexers.chunked(3).flatMap{group->group.map{item->async{try{AddonSearchResult(TorznabClient().search(item,query){onPartial(AddonSearchResult(it))})}catch(cancelled:CancellationException){throw cancelled}catch(error:Exception){AddonSearchResult(emptyList(),listOf("${item.name}: "+if(error is NabFailure)error.message.orEmpty()else "indexer search failed. Check the URL and API key."))}}}.awaitAll()}
        val results=listOf(addon.await())+nab
        AddonSearchResult(mergeSourceCandidates(results.flatMap{it.releases}).take(5000),results.flatMap{it.warnings})
    }
}

fun mergeSourceCandidates(items:List<SourceRelease>):List<SourceRelease> = items.groupBy{if(it.canImport)it.sourceIdentity else "${it.addonName}:${it.streamUrl}:${it.name}"}.values.map{group->
    group.sortedWith(compareByDescending<SourceRelease>{it.torrentFileUrl!=null}.thenByDescending{it.magnetUrl!=null}.thenByDescending{it.torrentSizeBytes!=null}.thenByDescending{it.sizeBytes!=null}).first()
}
