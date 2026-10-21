package app.stak.nativeapp

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.*

class WatchlistWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val scope = Credentials(context).scope
            val database = openStakDatabase(context, scope)
            try {
                val titles =
                    ScopedStakDao(database.dao(), scope)
                        .all()
                        .filter { it.collection == "watchlist" }
                        .take(3)
                        .map { it.media().name }
                if (Credentials(context).scope != scope) return@launch
                ids.forEach { id ->
                    val views = RemoteViews(context.packageName, R.layout.watchlist_widget)
                    views.setTextViewText(
                        R.id.widget_titles,
                        if (titles.isEmpty()) "Add titles to your watchlist"
                        else titles.joinToString("\n"),
                    )
                    val intent =
                        Intent(context, MainActivity::class.java)
                            .putExtra("destination", "watchlist")
                    views.setOnClickPendingIntent(
                        R.id.widget_root,
                        PendingIntent.getActivity(
                            context,
                            id,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        ),
                    )
                    manager.updateAppWidget(id, views)
                }
            } finally {
                database.close()
                pending.finish()
            }
        }
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WatchlistWidget::class.java))
            if (ids.isNotEmpty())
                context.sendBroadcast(
                    Intent(context, WatchlistWidget::class.java)
                        .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                )
        }
    }
}
