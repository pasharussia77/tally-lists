package com.galina.tally

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast

/** Small pop-up opened from the widget so you can add items without opening the app. */
class AddItemActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val id = intent.getStringExtra(SlotWidgetProvider.EXTRA_LIST)
        if (id == null) { finish(); return }
        val et = EditText(this)
        et.hint = "Add items, one per line or sentence"
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        et.minLines = 2
        et.maxLines = 8
        et.gravity = Gravity.TOP or Gravity.START
        val box = FrameLayout(this)
        val pad = (20 * resources.displayMetrics.density).toInt()
        box.setPadding(pad, pad / 2, pad, 0)
        box.addView(et)
        val dlg = AlertDialog.Builder(this)
            .setTitle(Store.listName(this, id))
            .setView(box)
            .setPositiveButton("Add") { _, _ ->
                val items = Store.parseItems(et.text.toString())
                if (items.isNotEmpty()) {
                    Store.addItems(this, id, items)
                    Store.touchUnlock(this, id)
                    RelockReceiver.schedule(this)
                    Widgets.refresh(this)
                    Toast.makeText(this, if (items.size == 1) "Added 1 item" else "Added ${items.size} items", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .setOnDismissListener { finish() }
            .create()
        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dlg.show()
        et.requestFocus()
    }
}
