package com.galina.tally

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONObject

class MainActivity : Activity() {
    companion object {
        const val ACTION_NEW = "com.galina.tally.NEW_LIST"
        const val ACTION_OPEN = "com.galina.tally.OPEN_LIST"
        const val EXTRA_LIST = "listId"
    }

    private lateinit var web: WebView
    private var pageReady = false
    private var pending: Intent? = null

    private val changed = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = reload()
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        web = WebView(this)
        web.setBackgroundColor(getColor(R.color.bg))
        val root = FrameLayout(this)
        root.setBackgroundColor(getColor(R.color.bg))
        root.addView(web, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val i = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(i.left, i.top, i.right, i.bottom)
            WindowInsetsCompat.CONSUMED
        }

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.addJavascriptInterface(Bridge(), "Android")
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                pageReady = true
                pending?.let { handle(it) }
                pending = null
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult): Boolean {
                AlertDialog.Builder(this@MainActivity).setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result.confirm() }
                    .setNegativeButton("Cancel") { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }.show()
                return true
            }

            override fun onJsPrompt(
                view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult
            ): Boolean {
                val et = EditText(this@MainActivity)
                et.setText(defaultValue)
                AlertDialog.Builder(this@MainActivity).setMessage(message).setView(et)
                    .setPositiveButton("OK") { _, _ -> result.confirm(et.text.toString()) }
                    .setNegativeButton("Cancel") { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }.show()
                return true
            }
        }
        web.loadUrl("file:///android_asset/index.html")

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        pending = intent
    }

    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i)
        setIntent(i)
        if (pageReady) handle(i) else pending = i
    }

    private fun handle(i: Intent) {
        when (i.action) {
            ACTION_NEW -> {
                js("window.__newList&&window.__newList()")
                web.requestFocus()
                web.postDelayed({
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                        .showSoftInput(web, InputMethodManager.SHOW_IMPLICIT)
                }, 400)
            }
            ACTION_OPEN -> {
                val id = i.getStringExtra(EXTRA_LIST)
                if (!id.isNullOrEmpty()) js("window.__openList&&window.__openList(" + JSONObject.quote(id) + ")")
            }
        }
        i.action = null
    }

    private fun js(code: String) = web.evaluateJavascript(code, null)
    private fun reload() = js("window.__reload&&window.__reload()")

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this, changed, IntentFilter(Store.ACTION_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(changed) } catch (e: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        AlarmScheduler.sync(this)
        reload()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        web.evaluateJavascript("window.__back&&window.__back()") { r ->
            if (r != "true") finish()
        }
    }

    inner class Bridge {
        @JavascriptInterface
        fun getData(): String = Store.raw(this@MainActivity)

        @JavascriptInterface
        fun saveData(json: String) {
            Store.saveRaw(this@MainActivity, json)
            AlarmScheduler.sync(this@MainActivity)
            SlotWidgetProvider.updateAll(this@MainActivity)
        }

        @JavascriptInterface
        fun getSlotCount(): Int = Widgets.slotIds(this@MainActivity).size
    }
}
