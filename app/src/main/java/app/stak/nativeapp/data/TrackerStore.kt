package app.stak.nativeapp.data

import androidx.room.withTransaction
import app.stak.nativeapp.data.db.*
import com.google.gson.Gson
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Versioned, account-partitioned Room documents. No schema reset and no truncation of personal viewing history. */
class TrackerStore(private val db:AppDatabase) {
    private fun key(scope:String)="personal-tracker-v1:$scope"
    private fun decode(raw:String?):TrackerState {
        if(raw==null)return TrackerState()
        return try{val root=com.google.gson.JsonParser.parseString(raw).asJsonObject;require(root.get("version")?.asInt==1);if(!root.has("pins"))root.add("pins",com.google.gson.JsonArray());require(root.get("pins").isJsonArray);listOf("events","plans","ratings","remote","imported","suppressed").forEach{require(root.get(it)?.isJsonArray==true)};Gson().fromJson(root,TrackerState::class.java)}catch(e:Exception){throw IOException("Saved tracking could not be read. It was not overwritten.",e)}
    }
    suspend fun read(scope:String)=withContext(Dispatchers.IO){decode(db.preferenceDao().get(key(scope)))}
    fun observe(scope:String):Flow<TrackerState> {
        fun source(s:String)=db.preferenceDao().observe(key(s)).map{decode(it)}
        if(scope=="local")return source(scope)
        return combine(source("local"),source(scope)){local,current->current.copy(events=(local.events+current.events).distinctBy{it.id},plans=(current.plans+local.plans.filter{l->current.plans.none{sameSeries(it.media,l.media)}}),ratings=(current.ratings+local.ratings).distinctBy{it.key},pins=current.pins+local.pins.filter{p->current.pins.none{sameWatchingTitle(it.media,p.media)}})}
    }
    suspend fun update(scope:String,change:(TrackerState)->TrackerState)=withContext(Dispatchers.IO){db.withTransaction{
        val old=decode(db.preferenceDao().get(key(scope)));val next=change(old)
        if(next!=old)db.preferenceDao().set(PreferenceEntity(key(scope),Gson().toJson(next)))
    }}
    suspend fun persistRows(rows:List<TrackingEntity>)=withContext(Dispatchers.IO){db.withTransaction{val previous=db.trackingDao().getAll();manual(rows,previous);db.trackingDao().upsertAll(rows)}}
    suspend fun importLegacy(rows:List<TrackingEntity>){rows.map{it.scope}.distinct().forEach{scope->update(scope){it.importRows(rows,scope)}}}
    suspend fun manual(rows:List<TrackingEntity>,previous:List<TrackingEntity>){rows.groupBy{it.scope}.forEach{(scope,items)->update(scope){state->items.fold(state){acc,row->acc.addManual(row,previous.firstOrNull{it.id==row.id&&it.scope==scope})}}}}
    suspend fun remote(scope:String,rows:List<JournalEntry>)=update(scope){it.observeRemote(rows,scope)}
    suspend fun pin(scope:String,media:MediaItem,active:Boolean)=update(scope){it.copy(pins=it.pins.filterNot{p->sameWatchingTitle(p.media,media)}+WatchingPin(journalSeriesTitle(media),active))}
    suspend fun savePlan(scope:String,plan:SeriesPlan)=update(scope){it.copy(plans=it.plans.filterNot{p->sameSeries(p.media,plan.media)}+plan)}
    suspend fun log(scope:String,media:MediaItem,watchedAt:Long,id:String=java.util.UUID.randomUUID().toString())=update(scope){it.copy(events=it.events.filterNot{e->e.id==id}+WatchEvent(id,media,watchedAt,"manual",scope))}
    private fun aliases(state:TrackerState,event:WatchEvent):List<String> {
        val related=state.remote.filter{r->matchingWatch(r.media,event.media)&&r.watchedAt==event.watchedAt}
        return listOf(event.id,"watch:${watchIdentity(event.media)}:${event.watchedAt?:"unknown"}")+related.flatMap{r->listOf("jellyfin:${credentialScope(r.media.jellyfinId?:watchIdentity(r.media))}:${r.watchedAt?:"unknown"}","watch:${watchIdentity(r.media)}:${r.watchedAt?:"unknown"}")}
    }
    suspend fun edit(event:WatchEvent,date:Long)=update(event.scope){state->state.copy(events=state.events.map{e->if(e.id==event.id)e.copy(watchedAt=date,source="manual")else e},suppressed=(state.suppressed+aliases(state,event)).distinct())}
    suspend fun remove(event:WatchEvent)=update(event.scope){state->state.copy(events=state.events.filterNot{e->e.id==event.id},suppressed=(state.suppressed+aliases(state,event)).distinct())}
    suspend fun rate(scope:String,media:MediaItem,value:Int?)=update(scope){state->
        require(value==null||value in 1..10)
        state.copy(ratings=state.ratings.filterNot{it.key=="${media.type}:${media.mediaKey}"}+(value?.let{listOf(PersonalRating("${media.type}:${media.mediaKey}",journalSeriesTitle(media),it))}?:emptyList()))
    }
}
