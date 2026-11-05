package app.stak.nativeapp.data

import androidx.room.withTransaction
import app.stak.nativeapp.data.db.*
import com.google.gson.*

internal fun cloudBackupKey(key:String)=key.isNotBlank()&&key.length<=200&&!key.startsWith("cloud_")&&!key.startsWith("__androidx_security_crypto_")&&key !in setOf("jellyfin_device_id","last_reminder_at","watch_status_cache")
internal fun encodeBackupPreferences(values:Map<String,*>):JsonObject=JsonObject().apply{
    values.toSortedMap().forEach{(key,value)->require(cloudBackupKey(key));add(key,JsonObject().apply{
        val type=when(value){is String->"string";is Boolean->"boolean";is Int->"int";is Long->"long";is Float->"float";is Set<*>->"strings";else->error("Unsupported setting")}
        addProperty("type",type);add("value",if(value is Set<*>)Gson().toJsonTree(value.map{require(it is String);it}.sorted())else Gson().toJsonTree(value))
    })}
}
internal fun decodeBackupPreferences(values:JsonObject):Map<String,Any> {
    require(values.size()<=2000)
    return values.entrySet().associate{(key,raw)->
        require(cloudBackupKey(key)&&raw.isJsonObject);val item=raw.asJsonObject;val value=item.get("value");require(value!=null&&!value.isJsonNull)
        val decoded:Any=when(item.get("type")?.asString){
            "string"->{require(value.isJsonPrimitive&&value.asJsonPrimitive.isString);value.asString}
            "boolean"->{require(value.isJsonPrimitive&&value.asJsonPrimitive.isBoolean);value.asBoolean}
            "int"->{require(value.isJsonPrimitive&&value.asJsonPrimitive.isNumber);value.asString.toInt()}
            "long"->{require(value.isJsonPrimitive&&value.asJsonPrimitive.isNumber);value.asString.toLong()}
            "float"->{require(value.isJsonPrimitive&&value.asJsonPrimitive.isNumber);value.asFloat.also{require(it.isFinite())}}
            "strings"->{require(value.isJsonArray&&value.asJsonArray.size()<=10000);value.asJsonArray.map{require(it.isJsonPrimitive&&it.asJsonPrimitive.isString);it.asString}.toSet()}
            else->error("Unsupported setting type")
        };key to decoded
    }
}
data class CloudSnapshot(val json:JsonObject,val watchlist:List<WatchlistEntity>,val tracking:List<TrackingEntity>,val documents:List<PreferenceEntity>,val secrets:Map<String,String>){val fingerprint get()=CloudBackupCodec.fingerprint(json)}
internal fun validateCloudSnapshot(root:JsonObject):CloudSnapshot {
    require(root.toString().toByteArray(Charsets.UTF_8).size<=CloudBackupCodec.MAX_PLAIN)
    require(root.get("format")?.asInt==1&&root.get("schema")?.asInt==4)
    require(root.keySet()==setOf("format","schema","settings","secrets","watchlist","tracking","documents"))
    decodeBackupPreferences(root.getAsJsonObject("settings")?:error("Missing settings"))
    val secrets=root.getAsJsonObject("secrets")?:error("Missing secrets");require(secrets.size()<=2000)
    val secure=secrets.entrySet().associate{(key,value)->require(cloudBackupKey(key)&&value.isJsonPrimitive&&value.asJsonPrimitive.isString);key to value.asString}
    fun array(key:String):JsonArray {val value=root.getAsJsonArray(key)?:error("Missing records");require(value.size()<=100000);require(value.all{it.isJsonObject});return value}
    fun text(row:JsonObject,key:String){require(row.get(key)?.let{it.isJsonPrimitive&&it.asJsonPrimitive.isString}==true)}
    val gson=Gson()
    val watchlist=array("watchlist").map{entry->val row=entry.asJsonObject;listOf("imdbId","type","name").forEach{text(row,it)};require(row.get("progress")?.asFloat?.isFinite()==true);gson.fromJson(row,WatchlistEntity::class.java).also{require(it.progress in 0f..1f)}}
    val tracking=array("tracking").map{entry->val row=entry.asJsonObject;listOf("id","type","name","scope").forEach{text(row,it)};require(row.get("played")?.asJsonPrimitive?.isBoolean==true);require(row.get("progress")?.asFloat?.isFinite()==true);gson.fromJson(row,TrackingEntity::class.java).also{require(it.progress in 0f..1f)}}
    val documents=array("documents").map{entry->val row=entry.asJsonObject;text(row,"key");text(row,"value");gson.fromJson(row,PreferenceEntity::class.java).also{require(cloudBackupKey(it.key))}}
    require(watchlist.distinctBy{it.imdbId to it.type}.size==watchlist.size&&tracking.distinctBy{it.id to it.scope}.size==tracking.size&&documents.distinctBy{it.key}.size==documents.size)
    return CloudSnapshot(root,watchlist,tracking,documents,secure)
}

/** Logical export + durable encrypted rollback: database files/master keys never travel. */
class CloudSnapshotStore(private val settings:SettingsStore,private val db:AppDatabase=DatabaseProvider.get(settings.applicationContext)) {
    internal fun changes()=kotlinx.coroutines.flow.combine(db.watchlistDao().observeAll(),db.trackingDao().observeAll(),db.preferenceDao().observeForBackup()){_,_,_->Unit}
    suspend fun capture():CloudSnapshot=db.withTransaction{
        val gson=Gson();val root=JsonObject().apply{
            addProperty("format",1);addProperty("schema",4);add("settings",settings.backupValues());add("secrets",gson.toJsonTree(settings.backupSecrets()))
            add("watchlist",gson.toJsonTree(db.watchlistDao().getAll().sortedWith(compareBy<WatchlistEntity>{it.imdbId}.thenBy{it.type})))
            add("tracking",gson.toJsonTree(db.trackingDao().getAll().sortedWith(compareBy<TrackingEntity>{it.id}.thenBy{it.scope})))
            add("documents",gson.toJsonTree(db.preferenceDao().getAllForBackup().filter{cloudBackupKey(it.key)}))
        };validateCloudSnapshot(root)
    }
    private suspend fun apply(snapshot:CloudSnapshot)=db.withTransaction{
        db.watchlistDao().clearForRestore();snapshot.watchlist.forEach{db.watchlistDao().upsert(it)}
        db.trackingDao().clearForRestore();db.trackingDao().upsertAll(snapshot.tracking)
        db.preferenceDao().clearForRestore();snapshot.documents.forEach{db.preferenceDao().set(it)}
        settings.restoreBackupValues(snapshot.json.getAsJsonObject("settings"),snapshot.secrets)
    }
    private fun metadata()=JsonObject().apply{listOf("cloud_session","cloud_bound_user","cloud_bound_name").forEach{key->add(key,settings.cloudValue(key)?.let(::JsonPrimitive)?:JsonNull.INSTANCE)}}
    private fun metadata(value:JsonObject){listOf("cloud_session","cloud_bound_user","cloud_bound_name").forEach{key->settings.cloudValue(key,value.get(key)?.takeUnless{it.isJsonNull}?.asString)}}
    suspend fun recoverPending(){
        val raw=settings.cloudValue("cloud_restore_recovery")?:return
        val journal=JsonParser.parseString(raw).asJsonObject;require(journal.get("version")?.asInt==1)
        val previous=validateCloudSnapshot(journal.getAsJsonObject("previous"));apply(previous);metadata(journal.getAsJsonObject("metadata"));settings.cloudValue("cloud_restore_recovery",null)
    }
    suspend fun restore(snapshot:CloudSnapshot,onCommitted:()->Unit){
        // Validate completely before any local write. A process crash replays only rollback.
        val next=validateCloudSnapshot(snapshot.json);val old=capture();val oldMetadata=metadata()
        val journal=JsonObject().apply{addProperty("version",1);add("previous",old.json);add("metadata",oldMetadata)}
        settings.cloudValue("cloud_restore_recovery",journal.toString())
        try{
            apply(next);onCommitted()
            settings.cloudValue("cloud_restore_previous",journal.toString())
            settings.cloudValue("cloud_restore_recovery",null)
        }catch(error:Throwable){
            try{apply(old);metadata(oldMetadata);settings.cloudValue("cloud_restore_recovery",null)}catch(rollback:Throwable){error.addSuppressed(rollback)}
            throw error
        }
    }
}
