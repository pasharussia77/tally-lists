package com.galina.tally

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

object Widgets {
    fun slotIds(c: Context): List<Int> =
        AppWidgetManager.getInstance(c)
            .getAppWidgetIds(ComponentName(c, SlotWidgetProvider::class.java))
            .sorted()

    /** Every slot widget keeps a stable number. A deleted widget frees its number for the next one. */
    @Synchronized
    fun slotNumbers(c: Context): Map<Int, Int> {
        val ids = slotIds(c)
        val mem = Store.memLoad(c)
        val w = mem.optJSONObject("widgets") ?: JSONObject()
        val live = ids.map { it.toString() }.toSet()
        w.keys().asSequence().toList().forEach { if (it !in live) w.remove(it) }
        val used = HashSet<Int>()
        w.keys().forEach { used.add(w.getInt(it)) }
        for (id in ids) {
            if (!w.has(id.toString())) {
                var n = 1
                while (n in used) n++
                w.put(id.toString(), n)
                used.add(n)
            }
        }
        mem.put("widgets", w)
        mem.put("maxSeen", maxOf(mem.optInt("maxSeen", 0), used.maxOrNull() ?: 0))
        Store.memSave(c, mem)
        return ids.associateWith { w.getInt(it.toString()) }
    }

    fun slotNo(c: Context, widgetId: Int): Int = slotNumbers(c)[widgetId] ?: 0

    fun slotFor(c: Context, widgetId: Int): Slot? {
        val nums = slotNumbers(c)
        val mine = nums[widgetId] ?: return null
        return Store.plan(c, nums.values.sorted(), false)[mine]
    }

    /** Save the current list-to-slot assignment so it can be restored later. */
    fun persist(c: Context) {
        val nums = slotNumbers(c)
        Store.plan(c, nums.values.sorted(), true)
    }

    fun refresh(c: Context) {
        SlotWidgetProvider.updateAll(c)
        c.sendBroadcast(Intent(Store.ACTION_CHANGED).setPackage(c.packageName))
    }

    /** Swap what two slots hold (a list, the archive, or nothing). */
    fun moveSlot(c: Context, from: Int, to: Int) {
        if (from == to) return
        val placed = slotNumbers(c).values.sorted()
        if (from !in placed || to !in placed) return
        Store.plan(c, placed, true)
        val plan = Store.plan(c, placed, false)
        val a = plan[from]
        val b = plan[to]
        val mem = Store.memLoad(c)
        val home = mem.optJSONObject("home") ?: JSONObject()
        fun setSlot(s: Slot?, no: Int) {
            if (s == null) return
            if (s.list != null) home.put(s.list.optString("id"), no) else mem.put("archive", no)
        }
        setSlot(a, to)
        setSlot(b, from)
        mem.put("home", home)
        Store.memSave(c, mem)
        refresh(c)
    }

    fun forgetMissing(c: Context) {
        val placed = slotNumbers(c).values
        val mem = Store.memLoad(c)
        mem.put("maxSeen", placed.maxOrNull() ?: 0)
        Store.memSave(c, mem)
        refresh(c)
    }

    fun forgetLayout(c: Context) {
        val placed = slotNumbers(c).values
        val mem = Store.memLoad(c)
        mem.remove("home")
        mem.remove("archive")
        mem.put("maxSeen", placed.maxOrNull() ?: 0)
        Store.memSave(c, mem)
        refresh(c)
    }

    /** Status for the Widgets page in the app. */
    fun info(c: Context): String {
        val nums = slotNumbers(c)
        val placed = nums.values.toSet()
        val mem = Store.memLoad(c)
        val expected = maxOf(mem.optInt("maxSeen", 0), placed.maxOrNull() ?: 0)
        val plan = Store.plan(c, placed.sorted(), false)
        val d = Store.load(c)
        val byId = Store.lists(d).associateBy { it.optString("id") }
        val slots = JSONArray()
        val missing = ArrayList<Int>()
        for (no in 1..expected) {
            val o = JSONObject()
            o.put("no", no)
            val s = plan[no]
            when {
                no !in placed -> { o.put("kind", "missing"); missing.add(no) }
                s == null -> o.put("kind", "empty")
                s.list != null -> {
                    o.put("kind", "list")
                    o.put("name", s.list.optString("name"))
                    o.put("color", s.list.optString("color"))
                    o.put("locked", s.list.optBoolean("locked"))
                    val its = s.list.optJSONArray("items")
                    val arr = JSONArray()
                    if (its != null) for (i in 0 until minOf(its.length(), 4)) {
                        val item = its.getJSONObject(i)
                        arr.put(JSONObject().put("t", item.optString("text")).put("d", item.optBoolean("done")))
                    }
                    o.put("items", arr)
                    o.put("total", its?.length() ?: 0)
                }
                else -> {
                    o.put("kind", "archive")
                    o.put("count", s.archive.size)
                    val names = JSONArray()
                    s.archive.take(4).forEach {
                        names.put(JSONObject().put("n", it.optString("name")).put("c", it.optString("color")))
                    }
                    o.put("names", names)
                }
            }
            slots.put(o)
        }
        val waiting = JSONArray()
        val home = mem.optJSONObject("home") ?: JSONObject()
        home.keys().forEach { id ->
            val slot = home.optInt(id)
            val l = byId[id]
            if (l != null && slot in missing && l.optString("view", "auto") != "hidden") {
                waiting.put(
                    JSONObject().put("name", l.optString("name")).put("color", l.optString("color")).put("home", slot)
                )
            }
        }
        return JSONObject()
            .put("slots", slots)
            .put("expected", expected)
            .put("found", placed.size)
            .put("waiting", waiting)
            .toString()
    }
}
