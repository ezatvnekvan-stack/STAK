package app.stak.nativeapp.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stak.nativeapp.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class DiscoverShelf(val id:String,val items:List<MediaItem> = emptyList(),val loading:Boolean=true,val error:String?=null)
data class DiscoverDeck(val shelves:List<DiscoverShelf> = listOf("top","trending","hidden").map{DiscoverShelf(it)})

/** Three bounded metadata requests; partial failures never discard successful shelves. */
class DiscoverDeckViewModel(private val fetch:suspend(String,String,String)->DiscoverResult={type,category,region->
    if(category=="top")StreamingPicks().load(type,region)else {
    val tmdb=!NativeRuntime.settings.tmdbKey.isNullOrBlank()
    if(category=="hidden"&&!tmdb)throw IllegalStateException("Hidden selections need TMDb metadata.")
    MediaRepository().discover(type,if(category=="trending"&&!tmdb)"trakt_trending"else category,"",1,region=region,requireTmdb=category=="hidden")
    }
}):ViewModel(){
    private val mutable=MutableStateFlow(DiscoverDeck())
    val state:StateFlow<DiscoverDeck> = mutable
    private var job:Job?=null
    private var identity:String?=null
    private var generation=0
    private val cache=linkedMapOf<String,Pair<Long,DiscoverDeck>>()
    fun load(type:String,region:String,refresh:Boolean=false){
        val key="$type:$region"
        if(!refresh&&identity==key)return
        job?.cancel();identity=key;val token=++generation
        cache[key]?.takeIf{!refresh&&System.currentTimeMillis()-it.first<300_000}?.let{mutable.value=it.second;return}
        mutable.value=DiscoverDeck()
        job=viewModelScope.launch{
            coroutineScope{listOf("top","trending","hidden").forEach{category->launch{
                val row=try{
                    val result=fetch(type,category,region)
                    DiscoverShelf(category,result.items.distinctBy{"${it.type}:${it.mediaKey}"}.take(12),false,
                        result.partialErrors.takeIf{it.isNotEmpty()}?.let{"Some titles could not load."})
                }catch(cancelled:CancellationException){throw cancelled}
                catch(_:Exception){DiscoverShelf(category,loading=false,error="This selection could not load.")}
                if(token==generation)mutable.update{deck->deck.copy(shelves=deck.shelves.map{if(it.id==category)row else it})}
            }}}
            if(token==generation){
                cache[key]=System.currentTimeMillis() to mutable.value
                while(cache.size>6)cache.remove(cache.keys.first())
            }
        }
    }
}
