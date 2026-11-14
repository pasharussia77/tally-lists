package com.galina.tally

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

object AlarmScheduler {
    private const val PREF = "tally_alarms"
    private const val CHANNEL = "alarms"

    private fun pending(c: Context, id: String): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java)
            .setAction("com.galina.tally.ALARM")
            .setData(Uri.parse("tally://alarm/$id"))
            .putExtra("id", id)
        return PendingIntent.getBroadcast(
            c, id.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showIntent(c: Context): PendingIntent =
        PendingIntent.getActivity(
            c, 99, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Make the system alarms match the alarms stored on the lists. */
    fun sync(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val d = Store.load(c)
        val now = System.currentTimeMillis()
        val prefs = c.getSharedPreferences(PREF, 0)
        val old = prefs.getStringSet("ids", emptySet())!!.toSet()
        val keep = mutableSetOf<String>()
        val overdue = mutableListOf<String>()
        for (l in Store.lists(d)) {
            val id = l.optString("id")
            val ts = l.optLong("alarm", 0)
            if (ts > now) {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(ts, showIntent(c)), pending(c, id))
                keep.add(id)
            } else if (ts > 0) {
                overdue.add(id)
            }
        }
        for (id in old - keep) am.cancel(pending(c, id))
        prefs.edit().putStringSet("ids", keep).apply()
        overdue.forEach { fire(c, it) }
    }

    fun fire(c: Context, id: String) {
        val d = Store.load(c)
        var name = "Your list"
        for (l in Store.lists(d)) {
            if (l.optString("id") == id) {
                name = l.optString("name", name)
                l.remove("alarm")
            }
        }
        Store.saveRaw(c, d.toString())

        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "List alarms", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(
            c, id.hashCode(),
            Intent(c, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN).putExtra(MainActivity.EXTRA_LIST, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(name)
            .setContentText("Alarm for your list")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .build()
        nm.notify(id.hashCode(), n)
        Widgets.refresh(c)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getStringExtra("id") ?: return
        AlarmScheduler.fire(c, id)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        AlarmScheduler.sync(c)
        RelockReceiver.schedule(c)
        Widgets.refresh(c)
    }
}

class RelockReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (Store.relockExpired(c)) Widgets.refresh(c)
        schedule(c)
    }

    companion object {
        fun schedule(c: Context) {
            val at = Store.nextRelockAt(c)
            val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(
                c, 555, Intent(c, RelockReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (at == 0L) {
                am.cancel(pi)
                return
            }
            val t = at + 300
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
            }
        }
    }
}
