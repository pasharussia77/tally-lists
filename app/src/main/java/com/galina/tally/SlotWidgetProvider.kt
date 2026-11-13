package com.galina.tally

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.RemoteViews

class SlotWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) = updateAll(c)
    override fun onDeleted(c: Context, ids: IntArray) = updateAll(c)

    override fun onReceive(c: Context, i: Intent) {
        if (i.action == ACTION_TOGGLE) {
            val l = i.getStringExtra(EXTRA_LIST) ?: return
            val item = i.getStringExtra(EXTRA_ITEM) ?: return
            Store.toggle(c, l, item)
            Widgets.refresh(c)
            return
        }
        super.onReceive(c, i)
    }

    companion object {
        const val ACTION_TOGGLE = "com.galina.tally.TOGGLE"
        const val EXTRA_LIST = "listId"
        const val EXTRA_ITEM = "itemId"

        fun updateAll(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            val ids = Widgets.slotIds(c)
            ids.forEach { m.updateAppWidget(it, build(c, it)) }
            if (ids.isNotEmpty()) m.notifyAppWidgetViewDataChanged(ids.toIntArray(), R.id.list)
        }

        private fun build(c: Context, id: Int): RemoteViews {
            val rv = RemoteViews(c.packageName, R.layout.widget_slot)
            val slot = Widgets.slotFor(c, id)
            val gray = Color.parseColor("#9A8F8A")
            val list = slot?.list
            val col: Int
            val title: String
            var alarmTxt = ""
            var openId = ""
            when {
                list != null -> {
                    col = Store.color(list)
                    title = (if (list.optBoolean("locked")) "\uD83D\uDD12 " else "") + list.optString("name")
                    openId = list.optString("id")
                    val ts = list.optLong("alarm", 0)
                    if (ts > 0) alarmTxt = "\u23F0 " + Store.fmtAlarm(ts)
                }
                slot != null -> { col = gray; title = "Older lists" }
                else -> { col = gray; title = "Empty slot" }
            }
            val empty = slot == null
            rv.setViewVisibility(R.id.slot_num, if (empty) View.VISIBLE else View.GONE)
            if (empty) rv.setTextViewText(R.id.slot_num, (Widgets.slotIds(c).indexOf(id) + 1).toString())
            rv.setViewVisibility(R.id.outline, if (empty) View.VISIBLE else View.GONE)
            rv.setViewVisibility(R.id.frame, if (empty) View.GONE else View.VISIBLE)
            rv.setViewVisibility(R.id.inner_bg, if (empty) View.GONE else View.VISIBLE)
            rv.setViewVisibility(R.id.content, if (empty) View.GONE else View.VISIBLE)
            rv.setInt(R.id.frame, "setColorFilter", col)
            rv.setInt(R.id.hdr_bg, "setColorFilter", col)
            rv.setTextViewText(R.id.title, title)
            rv.setTextColor(R.id.title, Store.textOn(col))
            rv.setTextViewText(R.id.alarm, alarmTxt)
            rv.setViewVisibility(R.id.alarm, if (alarmTxt.isEmpty()) View.GONE else View.VISIBLE)

            val open = Intent(c, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN)
                .putExtra(MainActivity.EXTRA_LIST, openId)
            rv.setOnClickPendingIntent(
                R.id.hdr_wrap,
                PendingIntent.getActivity(c, id * 10 + 1, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )

            val svc = Intent(c, SlotWidgetService::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            svc.data = Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME))
            rv.setRemoteAdapter(R.id.list, svc)
            rv.setEmptyView(R.id.list, R.id.empty)

            val template: PendingIntent = if (slot != null && slot.list == null) {
                val i = Intent(c, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN)
                PendingIntent.getActivity(c, id * 10 + 2, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            } else {
                val i = Intent(c, SlotWidgetProvider::class.java).setAction(ACTION_TOGGLE)
                PendingIntent.getBroadcast(c, id * 10 + 3, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            }
            rv.setPendingIntentTemplate(R.id.list, template)
            return rv
        }
    }
}
