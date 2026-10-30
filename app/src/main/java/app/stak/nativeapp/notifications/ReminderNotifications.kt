package app.stak.nativeapp.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.stak.nativeapp.MainActivity
import app.stak.nativeapp.R
import app.stak.nativeapp.data.Reminder

/**
 * Daily reminder channel and posting. Everything is best effort: a denied
 * permission or a missing channel must never crash a background worker.
 */
object ReminderNotifications {
    private const val CHANNEL_ID = "stak-reminders"
    private const val NOTIFICATION_ID = 4711

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Daily reminder",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "A quiet nudge when saved titles are waiting or the journal has gone quiet."
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        } catch (_: Exception) {
        }
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun post(context: Context, reminder: Reminder): Boolean {
        if (!canPost(context)) return false
        try {
            ensureChannel(context)
            val manager = context.getSystemService(NotificationManager::class.java) ?: return false
            if (!manager.areNotificationsEnabled() || manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) return false
            val open = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending = PendingIntent.getActivity(
                context,
                0,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_stak)
                .setContentTitle(reminder.title)
                .setContentText(reminder.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.body))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
            return true
        } catch (_: Exception) {
            return false
        }
    }

    fun cancel(context: Context) {
        try {
            context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {
        }
    }
}
