package com.galina.tally

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Checks the public tally-updates repo for a newer build and installs it. */
object Updater {
    private const val API = "https://api.github.com/repos/pasharussia77/tally-updates/releases/latest"

    fun currentBuild(c: Context): Long =
        c.packageManager.getPackageInfo(c.packageName, 0).longVersionCode

    fun versionName(c: Context): String =
        (c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "") + " (build " + currentBuild(c) + ")"

    fun check(a: Activity, manual: Boolean) {
        Thread {
            try {
                val conn = URL(API).openConnection() as HttpURLConnection
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val j = JSONObject(conn.inputStream.bufferedReader().readText())
                val n = j.optString("tag_name").removePrefix("build-").toLongOrNull() ?: 0L
                var url = ""
                val assets = j.optJSONArray("assets")
                if (assets != null) for (i in 0 until assets.length()) {
                    val x = assets.getJSONObject(i)
                    if (x.optString("name").endsWith(".apk")) url = x.optString("browser_download_url")
                }
                if (n > currentBuild(a) && url.isNotEmpty()) {
                    a.runOnUiThread { offer(a, n, url) }
                } else if (manual) {
                    a.runOnUiThread { Toast.makeText(a, "You\u2019re up to date", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                if (manual) a.runOnUiThread {
                    Toast.makeText(a, "Couldn\u2019t check for updates", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun offer(a: Activity, n: Long, url: String) {
        AlertDialog.Builder(a)
            .setTitle("Update available")
            .setMessage("A newer version (build $n) is ready.")
            .setPositiveButton("Install") { _, _ -> download(a, url) }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun download(a: Activity, url: String) {
        if (!a.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(a, "Allow installs from Tally Lists, then tap Check for updates again", Toast.LENGTH_LONG).show()
            a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.packageName)))
            return
        }
        val dlg = AlertDialog.Builder(a).setMessage("Downloading\u2026").setCancelable(false).show()
        Thread {
            try {
                val dir = File(a.cacheDir, "updates").apply { mkdirs() }
                val f = File(dir, "TallyLists.apk")
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.inputStream.use { input -> f.outputStream().use { input.copyTo(it) } }
                a.runOnUiThread { dlg.dismiss(); install(a, f) }
            } catch (e: Exception) {
                a.runOnUiThread {
                    dlg.dismiss()
                    Toast.makeText(a, "Download failed. Try again.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun install(a: Activity, f: File) {
        val uri = FileProvider.getUriForFile(a, a.packageName + ".files", f)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        a.startActivity(i)
    }
}
