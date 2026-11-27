package com.assem.copyapp

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FocusWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, manager, appWidgetIds)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val state = (context.applicationContext as App).container.repository.sessionFlow().first()
            val label = context.getString(if (state.active) R.string.tile_active else R.string.tile_start)
            withContext(Dispatchers.Main) {
                for (id in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_focus)
                    views.setTextViewText(R.id.widget_label, label)
                    val intent = Intent(context, MainActivity::class.java)
                    val pending = PendingIntent.getActivity(
                        context,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_root, pending)
                    manager.updateAppWidget(id, views)
                }
            }
        }
    }

    companion object {
        fun refresh(context: Context) {
            val manager = android.appwidget.AppWidgetManager.getInstance(context)
            val component = ComponentName(context, FocusWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_root)
        }
    }
}
