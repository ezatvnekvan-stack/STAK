package app.stak.nativeapp.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.stak.nativeapp.data.SettingsStore

/** Recompute the pending local-evening instant when the clock or zone changes. */
class ReminderClockReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        if(intent.action==Intent.ACTION_TIME_CHANGED||intent.action==Intent.ACTION_TIMEZONE_CHANGED){
            ReminderScheduler.apply(context,SettingsStore(context).dailyReminder,reschedule=true)
        }
    }
}
