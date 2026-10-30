package app.stak.nativeapp.notifications

import android.content.Context
import androidx.work.*
import app.stak.nativeapp.data.SettingsStore
import app.stak.nativeapp.data.nextReminderRun
import java.time.*
import java.util.concurrent.TimeUnit

/** One local-evening request at a time; delivery is best effort, not an exact alarm. */
object ReminderScheduler {
    const val UNIQUE_NAME="stak-local-evening-reminder"
    private const val LEGACY_NAME="stak-daily-reminder"
    private const val TEST_NAME="stak-reminder-test"
    private const val CLOCK_PREFS="stak_reminder_clock"
    const val SCHEDULE_TOKEN="schedule_token"

    @Synchronized fun apply(context:Context,enabled:Boolean,reschedule:Boolean=false) {
        try {
            val manager=WorkManager.getInstance(context)
            manager.cancelUniqueWork(LEGACY_NAME)
            if(!enabled){context.getSharedPreferences(CLOCK_PREFS,0).edit().remove("token").apply();manager.cancelUniqueWork(UNIQUE_NAME);manager.cancelUniqueWork(TEST_NAME);ReminderNotifications.cancel(context);return}
            val zoneChanged=context.getSharedPreferences(CLOCK_PREFS,0).getString("zone",null)!=ZoneId.systemDefault().id
            if(reschedule||zoneChanged)enqueue(context,ExistingWorkPolicy.REPLACE) else {
                // Reuse the persisted token if KEEP ignores a duplicate app-start request.
                enqueue(context,ExistingWorkPolicy.KEEP,reuseToken=true)
            }
        }catch(_:Exception){}
    }
    /** Append after the running request; never cancel the worker from inside itself. */
    @Synchronized fun followingEvening(context:Context,token:String?) {
        if(!SettingsStore(context).dailyReminder||!isCurrent(context,token))return
        try{enqueue(context,ExistingWorkPolicy.APPEND_OR_REPLACE)}catch(_:Exception){}
    }
    fun isCurrent(context:Context,token:String?):Boolean = token!=null&&context.getSharedPreferences(CLOCK_PREFS,0).getString("token",null)==token
    private fun enqueue(context:Context,policy:ExistingWorkPolicy,reuseToken:Boolean=false) {
        ReminderNotifications.ensureChannel(context)
        val prefs=context.getSharedPreferences(CLOCK_PREFS,0)
        val token=if(reuseToken)prefs.getString("token",null) ?: java.util.UUID.randomUUID().toString() else java.util.UUID.randomUUID().toString()
        val request=OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(workDataOf(SCHEDULE_TOKEN to token))
            .setInitialDelay(millisUntilNextRun(),TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build()).build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME,policy,request)
        prefs.edit().putString("zone",ZoneId.systemDefault().id).putString("token",token).apply()
    }
    fun sendTest(context:Context) {
        try{
            ReminderNotifications.ensureChannel(context)
            WorkManager.getInstance(context).enqueueUniqueWork(TEST_NAME,ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ReminderWorker>().setInputData(workDataOf(ReminderWorker.FORCE to true)).build())
        }catch(_:Exception){}
    }
    fun millisUntilNextRun(now:ZonedDateTime=ZonedDateTime.now(),target:LocalTime=LocalTime.of(19,0)):Long =
        Duration.between(now,nextReminderRun(now,target)).toMillis().coerceAtLeast(0)
}
