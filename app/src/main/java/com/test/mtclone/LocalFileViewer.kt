package com.test.mtclone

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/** Viewer/editor untuk file LEPAS di disk (bukan entry di dalam APK). Simpan = tulis balik ke file aslinya. */
class LocalFileViewer : AppCompatActivity() {

    private lateinit var file: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        file = File(intent.getStringExtra("path")!!)
        val bytes = file.readBytes()
        val editable = FileKind.isLikelyText(file.name)
        val content = if (editable) String(bytes, Charsets.UTF_8) else FileKind.hexDump(bytes)
        setContentView(buildUi(file.name, content, editable))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi(title: String, content: String, editable: Boolean): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B1220"))
        }
        lateinit var editText: EditText

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(28), dp(16), dp(12))
            setBackgroundColor(Color.parseColor("#111A2E"))
        }
        bar.addView(TextView(this).apply {
            text = "←"; textSize = 20f; setTextColor(Color.WHITE)
            setPadding(0, 0, dp(12), 0)
            setOnClickListener { finish() }
        })
        bar.addView(TextView(this).apply {
            text = title; textSize = 15f; setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            setSingleLine(true)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (editable) {
            bar.addView(TextView(this).apply {
                text = "Simpan"; textSize = 14f; setTextColor(Color.parseColor("#60A5FA"))
                setOnClickListener {
                    file.writeText(editText.text.toString(), Charsets.UTF_8)
                    Toast.makeText(this@LocalFileViewer, "Tersimpan.", Toast.LENGTH_SHORT).show()
                }
            })
        }
        root.addView(bar)

        val scroll = ScrollView(this)
        editText = EditText(this).apply {
            setText(content)
            isEnabled = editable; isFocusable = editable; isFocusableInTouchMode = editable
            typeface = Typeface.MONOSPACE
            textSize = 12.5f
            setTextColor(Color.parseColor("#E2E8F0"))
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            gravity = Gravity.TOP or Gravity.START
            movementMethod = ScrollingMovementMethod()
        }
        scroll.addView(editText, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }
}
