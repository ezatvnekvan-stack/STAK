package app.stak.nativeapp.data

import android.content.Context
import androidx.work.*
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Compatibility tombstone: a pending 3.x work request can never submit another torrent. */
class AcquisitionWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
    override suspend fun doWork()=Result.success()
}
suspend fun removeRetiredAcquisition(context:Context)=withContext(Dispatchers.IO){
    val work=WorkManager.getInstance(context)
    work.cancelUniqueWork("stak-acquisition-monitor").result.get()
    val dao=DatabaseProvider.get(context).preferenceDao()
    val raw=dao.get("automation_v1")
    val ids=runCatching{JsonParser.parseString(raw).asJsonObject.getAsJsonArray("jobs").mapNotNull{it.asJsonObject.get("id")?.asString}}.getOrDefault(emptyList())
    ids.forEach{work.cancelUniqueWork("stak-acquisition-$it").result.get()}
    dao.delete("automation_v1")
    context.getSharedPreferences("stak_settings",0).edit().remove("automation_background").apply()
}
