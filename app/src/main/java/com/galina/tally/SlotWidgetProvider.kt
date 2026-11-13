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
import android.widget.Toast

class SlotWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) = updateAll(c)
    override fun onDeleted(c: Context, ids: IntArray) = updateAll(c)

    override fun onReceive(c: Context, i: Intent) {
        val l = i.getStringExtra(EXTRA_LIST)
        when (i.action) {
            ACTION_TOGGLE -> {
                val item = i.getStringExtra(EXTRA_ITEM)
                if (l == null || item == null) return
                if (Store.isLocked(c, l)) {
                    Toast.makeText(c, "This list is locked. Tap the lock to edit.", Toast.LENGTH_SHORT).show()
                    return
                }
                if (i.getStringExtra(EXTRA_OP) == "del") Store.deleteItem(c, l, item) else Store.toggle(c, l, item)
                Widgets.refresh(c)
            }
            ACTION_LOCK -> if (l != null) { Store.widgetLockToggle(c, l); RelockReceiver.schedule(c); Widgets.refresh(c) }
            ACTION_SORT -> if (l != null) { Store.flip(c, l, "sortChecked"); Widgets.refresh(c) }
            else -> super.onReceive(c, i)
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.galina.tally.TOGGLE"
        const val ACTION_LOCK = "com.galina.tally.LOCK"
        const val ACTION_SORT = "com.galina.tally.SORT"
        const val EXTRA_LIST = "listId"
        const val EXTRA_ITEM = "itemId"
        const val EXTRA_OP = "op"

        fun updateAll(c: Context) {
            Store.relockExpired(c)
            Widgets.persist(c)
            val m = AppWidgetManager.getInstance(c)
            val ids = Widgets.slotIds(c)
            ids.forEach { m.updateAppWidget(it, build(c, it)) }
            if (ids.isNotEmpty()) m.notifyAppWidgetViewDataChanged(ids.toIntArray(), R.id.list)
        }

        private fun bcast(c: Context, id: Int, req: Int, action: String, listId: String): PendingIntent {
            val i = Intent(c, SlotWidgetProvider::class.java).setAction(action).putExtra(EXTRA_LIST, listId)
            return PendingIntent.getBroadcast(c, id * 10 + req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun build(c: Context, id: Int): RemoteViews {
            val rv = RemoteViews(c.packageName, R.layout.widget_slot)
            val slot = Widgets.slotFor(c, id)
            val gray = Color.parseColor("#9A8F8A")
            val list = slot?.list
            val locked = list?.optBoolean("locked") ?: false
            val col: Int
            val title: String
            var alarmTxt = ""
            when {
                list != null -> {
                    col = Store.color(list)
                    title = list.optString("name")
                    val ts = list.optLong("alarm", 0)
                    if (ts > 0) alarmTxt = "\u23F0 " + Store.fmtAlarm(ts)
                }
                slot != null -> { col = gray; title = "Older lists" }
                else -> { col = gray; title = "Empty slot" }
            }
            val empty = slot == null
            rv.setViewVisibility(R.id.slot_num, View.VISIBLE)
            rv.setTextViewText(R.id.slot_num, Widgets.slotNo(c, id).toString())
            rv.setViewVisibility(R.id.outline, if (empty) View.VISIBLE else View.GONE)
            rv.setViewVisibility(R.id.frame, if (empty) View.GONE else View.VISIBLE)
            rv.setViewVisibility(R.id.inner_bg, if (empty) View.GONE else View.VISIBLE)
            rv.setViewVisibility(R.id.content, if (empty) View.GONE else View.VISIBLE)
            rv.setInt(R.id.frame, "setColorFilter", col)
            rv.setInt(R.id.hdr_bg, "setColorFilter", col)
            val tc = Store.textOn(col)
            rv.setTextViewText(R.id.title, title)
            rv.setTextColor(R.id.title, tc)
            rv.setTextViewText(R.id.alarm, alarmTxt)
            rv.setViewVisibility(R.id.alarm, if (alarmTxt.isEmpty()) View.GONE else View.VISIBLE)

            val showBtns = list != null
            for (b in listOf(R.id.btn_sort, R.id.btn_lock, R.id.btn_add, R.id.btn_open)) {
                rv.setInt(b, "setColorFilter", tc)
            }
            rv.setViewVisibility(R.id.btn_sort, if (showBtns) View.VISIBLE else View.GONE)
            rv.setViewVisibility(R.id.btn_lock, if (showBtns) View.VISIBLE else View.GONE)
            rv.setViewVisibility(R.id.btn_add, if (showBtns && !locked) View.VISIBLE else View.GONE)
            rv.setViewVisibility(R.id.btn_open, if (showBtns) View.VISIBLE else View.GONE)
            if (list != null) {
                val lid = list.optString("id")
                rv.setImageViewResource(R.id.btn_lock, if (locked) R.drawable.ic_lock_closed else R.drawable.ic_lock_open)
                rv.setInt(R.id.btn_sort, "setImageAlpha", if (list.optBoolean("sortChecked")) 255 else 110)
                rv.setOnClickPendingIntent(R.id.btn_lock, bcast(c, id, 5, ACTION_LOCK, lid))
                rv.setOnClickPendingIntent(R.id.btn_sort, bcast(c, id, 4, ACTION_SORT, lid))
                val open = Intent(c, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN)
                    .putExtra(MainActivity.EXTRA_LIST, lid)
                rv.setOnClickPendingIntent(
                    R.id.btn_open,
                    PendingIntent.getActivity(c, id * 10 + 1, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                )
                val add = Intent(c, AddItemActivity::class.java).putExtra(EXTRA_LIST, lid)
                rv.setOnClickPendingIntent(
                    R.id.btn_add,
                    PendingIntent.getActivity(c, id * 10 + 6, add, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                )
            }

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
