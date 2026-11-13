package com.galina.tally

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.text.SpannableString
import android.text.style.StrikethroughSpan
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService

class SlotWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        Factory(applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))

    private class Row(
        val listId: String,
        val itemId: String?,
        val text: String,
        val done: Boolean,
        val color: Int
    )

    private class Factory(private val c: Context, private val wid: Int) : RemoteViewsFactory {
        private var rows: List<Row> = emptyList()
        private var hide = false
        private var dim = true

        override fun onCreate() {}
        override fun onDestroy() {}

        override fun onDataSetChanged() {
            val d = Store.load(c)
            hide = Store.setting(d, "hideChecked", false)
            dim = Store.setting(d, "dim", true)
            val slot = Widgets.slotFor(c, wid)
            val sl = slot?.list
            rows = when {
                slot == null -> emptyList()
                sl != null -> {
                    val its = sl.optJSONArray("items")
                    val col = Store.color(sl)
                    val out = ArrayList<Row>()
                    if (its != null) for (i in 0 until its.length()) {
                        val item = its.getJSONObject(i)
                        val done = item.optBoolean("done")
                        if (hide && done) continue
                        out.add(Row(sl.optString("id"), item.optString("id"), item.optString("text"), done, col))
                    }
                    out
                }
                else -> slot.archive.map {
                    val ts = it.optLong("alarm", 0)
                    val label = it.optString("name") + if (ts > 0) "   \u23F0 " + Store.fmtAlarm(ts) else ""
                    Row(it.optString("id"), null, label, false, Store.color(it))
                }
            }
        }

        override fun getCount() = rows.size

        override fun getViewAt(position: Int): RemoteViews {
            val r = rows[position]
            return if (r.itemId != null) itemView(r) else archiveView(r)
        }

        private fun itemView(r: Row): RemoteViews {
            val rv = RemoteViews(c.packageName, R.layout.widget_item)
            rv.setInt(R.id.ring, "setColorFilter", r.color)
            rv.setInt(R.id.fill, "setColorFilter", r.color)
            rv.setInt(R.id.mark, "setColorFilter", Store.textOn(r.color))
            val vis = if (r.done) View.VISIBLE else View.GONE
            rv.setViewVisibility(R.id.fill, vis)
            rv.setViewVisibility(R.id.mark, vis)
            val s = SpannableString(r.text)
            if (r.done) s.setSpan(StrikethroughSpan(), 0, s.length, 0)
            rv.setTextViewText(R.id.text, s)
            rv.setTextColor(R.id.text, c.getColor(if (r.done && dim) R.color.muted else R.color.ink))
            val fill = Intent()
                .putExtra(SlotWidgetProvider.EXTRA_LIST, r.listId)
                .putExtra(SlotWidgetProvider.EXTRA_ITEM, r.itemId)
            rv.setOnClickFillInIntent(R.id.row_root, fill)
            return rv
        }

        private fun archiveView(r: Row): RemoteViews {
            val rv = RemoteViews(c.packageName, R.layout.widget_archive_row)
            rv.setInt(R.id.bar, "setColorFilter", r.color)
            rv.setTextViewText(R.id.name, r.text)
            rv.setTextColor(R.id.name, Store.textOn(r.color))
            rv.setOnClickFillInIntent(
                R.id.row_root,
                Intent().putExtra(MainActivity.EXTRA_LIST, r.listId)
            )
            return rv
        }

        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 2
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = true
    }
}
