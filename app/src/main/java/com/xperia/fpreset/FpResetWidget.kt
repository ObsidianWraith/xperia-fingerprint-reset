package com.xperia.fpreset

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class FpResetWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)
    companion object {
        fun refresh(context: Context) {
            ResetStore.load(context)
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, FpResetWidget::class.java))
            val action = PendingIntent.getForegroundService(context, 1,
                Intent(context, ResetService::class.java).setAction(ResetService.RESET),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val views = RemoteViews(context.packageName, R.layout.reset_widget).apply {
                setOnClickPendingIntent(R.id.widget_root, action)
                setTextViewText(R.id.widget_status, ResetStore.state.value.title)
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
