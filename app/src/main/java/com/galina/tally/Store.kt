package com.galina.tally

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

class Slot(val list: JSONObject?, val archive: List<JSONObject>)

object Store {
    private const val PREF = "tally"
    private const val KEY = "data"
    const val ACTION_CHANGED = "com.galina.tally.DATA_CHANGED"

    fun raw(c: Context): String = c.getSharedPreferences(PREF, 0).getString(KEY, "") ?: ""
    fun saveRaw(c: Context, json: String) {
        c.getSharedPreferences(PREF, 0).edit().putString(KEY, json).apply()
    }

    fun load(c: Context): JSONObject = try {
        val r = raw(c)
        if (r.isEmpty()) JSONObject() else JSONObject(r)
    } catch (e: Exception) {
        JSONObject()
    }

    fun lists(d: JSONObject): List<JSONObject> {
        val a = d.optJSONArray("lists") ?: JSONArray()
        return (0 until a.length()).map { a.getJSONObject(it) }
    }

    fun setting(d: JSONObject, key: String, def: Boolean): Boolean =
        d.optJSONObject("settings")?.optBoolean(key, def) ?: def

    fun isDone(l: JSONObject, auto: Boolean): Boolean {
        if (l.has("manualDone")) return l.optBoolean("manualDone")
        val its = l.optJSONArray("items") ?: return false
        if (its.length() == 0) return false
        return auto && (0 until its.length()).all { its.getJSONObject(it).optBoolean("done") }
    }

    /** Same rules as the app screen: pinned Full lists, newest automatic lists, one archive slot. */
    fun layout(c: Context, n: Int): List<Slot> {
        val d = load(c)
        val auto = setting(d, "autoDone", true)
        val vis = lists(d).filter { it.optString("view", "auto") != "hidden" }
        val pinned = vis.filter { it.optString("view") == "full" }.take(maxOf(n - 2, 0))
        val coll = vis.filter { it.optString("view") == "collapsed" }
        val autos = vis.filter { it.optString("view", "auto") == "auto" && !isDone(it, auto) }
            .sortedByDescending { it.optLong("created") }
        val free = n - pinned.size
        val need = coll.isNotEmpty() || autos.size > free
        val full: List<JSONObject>
        val arch: List<JSONObject>
        if (!need) {
            full = autos
            arch = emptyList()
        } else {
            val k = maxOf(free - 1, 0)
            full = autos.take(k)
            arch = coll + autos.drop(k)
        }
        val slots = (full + pinned).map { Slot(it, emptyList()) }.toMutableList()
        if (arch.isNotEmpty()) slots.add(Slot(null, arch))
        return slots
    }

    fun toggle(c: Context, listId: String, itemId: String) {
        val d = load(c)
        for (l in lists(d)) {
            if (l.optString("id") != listId) continue
            if (l.optBoolean("locked")) return
            val its = l.optJSONArray("items") ?: continue
            for (i in 0 until its.length()) {
                val item = its.getJSONObject(i)
                if (item.optString("id") == itemId) {
                    val nv = !item.optBoolean("done")
                    item.put("done", nv)
                    if (!nv) l.remove("manualDone")
                }
            }
        }
        saveRaw(c, d.toString())
    }

    fun color(l: JSONObject): Int = try {
        Color.parseColor(l.optString("color", "#888888"))
    } catch (e: Exception) {
        Color.GRAY
    }

    fun textOn(col: Int): Int {
        fun lin(v: Int): Double {
            val x = v / 255.0
            return if (x <= 0.03928) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4)
        }
        val lum = 0.2126 * lin(Color.red(col)) + 0.7152 * lin(Color.green(col)) + 0.0722 * lin(Color.blue(col))
        return if (lum > 0.3) Color.parseColor("#1F2430") else Color.WHITE
    }

    fun fmtAlarm(ts: Long): String =
        SimpleDateFormat("EEE, MMM d, h:mm a", Locale.getDefault()).format(java.util.Date(ts))
}
