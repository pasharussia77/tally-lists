package com.galina.tally

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout

/** Small pop-up opened from the widget so you can add an item without opening the app. */
class AddItemActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val id = intent.getStringExtra(SlotWidgetProvider.EXTRA_LIST)
        if (id == null) { finish(); return }
        val et = EditText(this)
        et.hint = "Add an item"
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        et.imeOptions = EditorInfo.IME_ACTION_DONE
        et.isSingleLine = true
        val box = FrameLayout(this)
        val pad = (20 * resources.displayMetrics.density).toInt()
        box.setPadding(pad, pad / 2, pad, 0)
        box.addView(et)
        fun add(): Boolean {
            val t = et.text.toString().trim().replace(Regex("^[-*\u2022\u2013\u2014]\\s*"), "")
            if (t.isNotEmpty()) {
                Store.addItem(this, id, t)
                Widgets.refresh(this)
            }
            return true
        }
        val dlg = AlertDialog.Builder(this)
            .setTitle(Store.listName(this, id))
            .setView(box)
            .setPositiveButton("Add") { _, _ -> add() }
            .setNegativeButton("Cancel", null)
            .setOnDismissListener { finish() }
            .create()
        et.setOnEditorActionListener { _, _, _ -> add(); dlg.dismiss(); true }
        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dlg.show()
        et.requestFocus()
    }
}
