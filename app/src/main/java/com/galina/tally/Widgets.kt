package com.galina.tally

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

object Widgets {
    fun slotIds(c: Context): List<Int> =
        AppWidgetManager.getInstance(c)
            .getAppWidgetIds(ComponentName(c, SlotWidgetProvider::class.java))
            .sorted()

    fun slotFor(c: Context, widgetId: Int): Slot? {
        val ids = slotIds(c)
        val idx = ids.indexOf(widgetId)
        if (idx < 0) return null
        return Store.layout(c, ids.size).getOrNull(idx)
    }

    /** Update every widget and tell the app screen (if open) to reload. */
    fun refresh(c: Context) {
        SlotWidgetProvider.updateAll(c)
        c.sendBroadcast(Intent(Store.ACTION_CHANGED).setPackage(c.packageName))
    }
}
