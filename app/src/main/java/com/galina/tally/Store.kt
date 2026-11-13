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

    private const val MEM = "tally_layout"

    fun memLoad(c: Context): JSONObject = try {
        JSONObject(c.getSharedPreferences(MEM, 0).getString("mem", "{}") ?: "{}")
    } catch (e: Exception) {
        JSONObject()
    }

    fun memSave(c: Context, m: JSONObject) {
        c.getSharedPreferences(MEM, 0).edit().putString("mem", m.toString()).apply()
    }

    /** Which lists are shown in full and which go to the archive (same rules as the app screen). */
    private fun selection(d: JSONObject, n: Int): Pair<List<JSONObject>, List<JSONObject>> {
        val auto = setting(d, "autoDone", true)
        val vis = lists(d).filter { it.optString("view", "auto") != "hidden" }
        val allPinned = vis.filter { it.optString("view") == "full" }
        val pinLimit = maxOf(n - 2, 0)
        val pinned = allPinned.take(pinLimit)
        val extra = allPinned.drop(pinLimit)
        val coll = vis.filter { it.optString("view") == "collapsed" }
        val autos = vis.filter { it.optString("view", "auto") == "auto" && !isDone(it, auto) }
            .sortedByDescending { it.optLong("created") }
        val free = n - pinned.size
        val need = coll.isNotEmpty() || extra.isNotEmpty() || autos.size > free
        return if (!need) {
            Pair(autos + pinned, emptyList())
        } else {
            val k = maxOf(free - 1, 0)
            Pair(autos.take(k) + pinned, extra + coll + autos.drop(k))
        }
    }

    /**
     * Decides which list sits in which slot number. Every list remembers its home slot,
     * so it returns there when that slot widget is placed again.
     */
    fun plan(c: Context, slotNos: List<Int>, persist: Boolean): Map<Int, Slot> {
        val n = slotNos.size
        if (n == 0) return emptyMap()
        val sorted = slotNos.sorted()
        val d = load(c)
        val (fulls, arch) = selection(d, n)
        val mem = memLoad(c)
        val home = mem.optJSONObject("home") ?: JSONObject()
        val result = HashMap<Int, Slot>()
        val taken = HashSet<Int>()
        val pending = ArrayList<JSONObject>()
        val pin = mem.optInt("archivePin", 0)
        val archSlot = if (arch.isEmpty()) 0 else if (pin in sorted) pin else sorted.last()
        if (archSlot != 0) {
            taken.add(archSlot)
            result[archSlot] = Slot(null, arch)
        }
        for (l in fulls) {
            val h = home.optInt(l.optString("id"), 0)
            if (h in sorted && h !in taken) {
                result[h] = Slot(l, emptyList())
                taken.add(h)
            } else {
                pending.add(l)
            }
        }
        for (l in pending) {
            val free = sorted.firstOrNull { it !in taken } ?: break
            result[free] = Slot(l, emptyList())
            taken.add(free)
            if (home.optInt(l.optString("id"), 0) == 0) home.put(l.optString("id"), free)
        }
        if (persist) {
            val ids = lists(d).map { it.optString("id") }.toSet()
            home.keys().asSequence().toList().forEach { if (it !in ids) home.remove(it) }
            mem.put("home", home)
            memSave(c, mem)
        }
        return result
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

    const val RELOCK_MS = 120_000L

    /** Lists unlocked from a widget lock themselves again after two minutes. */
    fun relockExpired(c: Context): Boolean {
        val d = load(c)
        val now = System.currentTimeMillis()
        var changed = false
        for (l in lists(d)) {
            val t = l.optLong("unlockedAt", 0)
            if (t > 0 && now - t >= RELOCK_MS) {
                l.put("locked", true)
                l.remove("unlockedAt")
                changed = true
            }
        }
        if (changed) saveRaw(c, d.toString())
        return changed
    }

    fun widgetLockToggle(c: Context, listId: String) {
        val d = load(c)
        for (l in lists(d)) {
            if (l.optString("id") != listId) continue
            if (l.optBoolean("locked")) {
                l.put("locked", false)
                l.put("unlockedAt", System.currentTimeMillis())
            } else {
                l.put("locked", true)
                l.remove("unlockedAt")
            }
        }
        saveRaw(c, d.toString())
    }

    fun nextRelockAt(c: Context): Long {
        var best = 0L
        for (l in lists(load(c))) {
            val t = l.optLong("unlockedAt", 0)
            if (t > 0 && !l.optBoolean("locked")) {
                val at = t + RELOCK_MS
                if (best == 0L || at < best) best = at
            }
        }
        return best
    }

    fun isLocked(c: Context, listId: String): Boolean {
        relockExpired(c)
        return isLockedRaw(c, listId)
    }

    private fun isLockedRaw(c: Context, listId: String): Boolean =
        lists(load(c)).any { it.optString("id") == listId && it.optBoolean("locked") }

    fun flip(c: Context, listId: String, key: String) {
        val d = load(c)
        for (l in lists(d)) if (l.optString("id") == listId) l.put(key, !l.optBoolean(key))
        saveRaw(c, d.toString())
    }

    fun deleteItem(c: Context, listId: String, itemId: String) {
        val d = load(c)
        for (l in lists(d)) {
            if (l.optString("id") != listId) continue
            if (l.optBoolean("locked")) return
            val its = l.optJSONArray("items") ?: continue
            val out = JSONArray()
            for (i in 0 until its.length()) {
                val item = its.getJSONObject(i)
                if (item.optString("id") != itemId) out.put(item)
            }
            l.put("items", out)
        }
        saveRaw(c, d.toString())
    }

    private val ABBR = setOf("mr", "mrs", "ms", "dr", "st", "vs", "etc", "jr", "sr", "inc", "co")
    private val DASH = Regex("""^([-*\u2022\u2013\u2014]\s*|\d+[.)]\s+)""")
    private val INTRO = Regex("""(?i)^(please\s+)?(add|adding|put|putting|append|appending)\b.*\blist\b""")

    private fun sentences(t: String): List<String> {
        val out = ArrayList<String>()
        for (p in t.split(Regex("""(?<=[.!?])\s+"""))) {
            val prev = out.lastOrNull()
            if (prev != null && prev.endsWith(".")) {
                val w = prev.trimEnd('.').split(Regex("""\s+""")).last().lowercase()
                if (w in ABBR) {
                    out[out.size - 1] = "$prev $p"
                    continue
                }
            }
            out.add(p)
        }
        return out
    }

    /** "Milk, sugar, and oats" becomes three items. Commas inside numbers like 1,000 are left alone. */
    private fun splitCommas(s: String): List<String> {
        val parts = s.split(Regex(""",\s+"""))
        if (parts.size < 2) return listOf(s)
        return parts.map { it.trim().replace(Regex("""(?i)^(and|or)\s+"""), "") }
            .filter { it.isNotEmpty() }
            .map { it.replaceFirstChar { ch -> ch.uppercase() } }
    }

    /** Splits dictated or typed text into separate items; a spoken "Adding to the list" intro is skipped. */
    fun parseItems(text: String): List<String> {
        val lines = text.split(Regex("""\r?\n""")).map { it.trim() }.filter { it.isNotEmpty() }
        val segs = ArrayList<Pair<String, Boolean>>()
        for (ln in lines) {
            if (DASH.containsMatchIn(ln)) segs.add(Pair(ln.replace(DASH, ""), false))
            else sentences(ln).forEach { segs.add(Pair(it, true)) }
        }
        if (segs.isNotEmpty()) {
            val f = segs[0].first
            val ci = f.indexOf(':')
            if (INTRO.containsMatchIn(f) && ci > -1 && ci < f.length - 1) {
                segs[0] = Pair(f.substring(ci + 1).trim(), segs[0].second)
            } else if (segs.size > 1 && (f.trimEnd().endsWith(":") || INTRO.containsMatchIn(f))) {
                segs.removeAt(0)
            }
        }
        val out = ArrayList<String>()
        for ((t0, split) in segs) {
            val t = t0.trim().trimEnd('.').trim()
            if (t.isEmpty()) continue
            if (split) out.addAll(splitCommas(t)) else out.add(t)
        }
        return out
    }

    fun addItems(c: Context, listId: String, texts: List<String>) {
        val d = load(c)
        for (l in lists(d)) {
            if (l.optString("id") != listId) continue
            if (l.optBoolean("locked")) return
            val its = l.optJSONArray("items") ?: JSONArray().also { l.put("items", it) }
            for (t in texts) {
                its.put(JSONObject().put("id", java.lang.Long.toString(System.nanoTime(), 36)).put("text", t).put("done", false))
            }
            if (l.optBoolean("manualDone")) l.remove("manualDone")
        }
        saveRaw(c, d.toString())
    }

    fun listName(c: Context, listId: String): String =
        lists(load(c)).firstOrNull { it.optString("id") == listId }?.optString("name") ?: "List"

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
