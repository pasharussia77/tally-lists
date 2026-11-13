package com.galina.tally

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class NewListWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val rv = RemoteViews(c.packageName, R.layout.widget_new)
            val i = Intent(c, MainActivity::class.java).setAction(MainActivity.ACTION_NEW)
            rv.setOnClickPendingIntent(
                R.id.new_root,
                PendingIntent.getActivity(c, 7, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )
            m.updateAppWidget(id, rv)
        }
    }
}
