package app.stak.nativeapp.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.stak.nativeapp.data.*
import kotlinx.coroutines.CancellationException
import java.time.*

class ReminderWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    companion object{const val FORCE="force";private const val TAG="STAK-Reminder"}
    override suspend fun doWork():Result {
        val settings=SettingsStore(applicationContext)
        val test=inputData.getBoolean(FORCE,false)
        val token=inputData.getString(ReminderScheduler.SCHEDULE_TOKEN)
        try{
            if(test){
                if(settings.dailyReminder)ReminderNotifications.post(applicationContext,Reminder("Reminders are on","This is how the evening reminder will look. Turn it off any time in Settings."))
                return Result.success()
            }
            checkJournal(settings,token)
        }catch(cancelled:CancellationException){throw cancelled}
        catch(error:Exception){android.util.Log.w(TAG,"Could not check local reminder",error)}
        // Rebase after every completion (including late delivery), never add a fixed 24h.
        ReminderScheduler.followingEvening(applicationContext,token)
        return Result.success()
    }
    private suspend fun checkJournal(settings:SettingsStore,token:String?) {
        if(!settings.dailyReminder||!ReminderScheduler.isCurrent(applicationContext,token)||!ReminderNotifications.canPost(applicationContext))return
        val now=Instant.now();val zone=ZoneId.systemDefault()
        if(reminderAlreadySentToday(settings.lastReminderAt,now,zone))return
        val scope=if(settings.jellyfinConnected)jellyfinScope(settings) else "local"
        val db=DatabaseProvider.get(applicationContext)
        val journal=reminderJournal(db.trackingDao().getAll(),scope)
        val watchlist=db.watchlistDao().getAll()
        if(journal.isEmpty()&&watchlist.isEmpty())return
        val today=now.atZone(zone).toLocalDate()
        val insights=viewingInsights(journal,today=today,zone=zone)
        val reminder=planReminder(insights,watchlist.size,today,insights.lastWatched,zone) ?: return
        // Account changes and toggling off while Room is loading must invalidate this send.
        val currentScope=if(settings.jellyfinConnected)jellyfinScope(settings) else "local"
        if(!settings.dailyReminder||scope!=currentScope||!ReminderScheduler.isCurrent(applicationContext,token))return
        if(ReminderNotifications.post(applicationContext,reminder))settings.lastReminderAt=now.toEpochMilli()
    }
}
