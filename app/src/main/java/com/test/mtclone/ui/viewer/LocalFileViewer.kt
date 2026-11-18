package com.test.mtclone.ui.viewer

import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.test.mtclone.R
import com.test.mtclone.core.file.FileKind
import com.test.mtclone.core.prefs.AppPrefs
import com.test.mtclone.ui.common.AppBar
import com.test.mtclone.ui.common.codeViewer
import com.test.mtclone.ui.common.color
import java.io.File

/** Viewer/editor untuk file LEPAS di disk (bukan entry di dalam APK). Simpan = tulis balik ke file aslinya. */
class LocalFileViewer : AppCompatActivity() {

    private lateinit var file: File
    private val prefs by lazy { AppPrefs(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        file = File(intent.getStringExtra(EXTRA_PATH)!!)
        val bytes = file.readBytes()
        val editable = FileKind.isLikelyText(file.name)
        var content = if (editable) String(bytes, Charsets.UTF_8) else FileKind.hexDump(bytes)
        if (!editable && bytes.size > FileKind.HEX_MAX) content += "\n" + getString(R.string.hex_truncated, bytes.size)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.pane_inactive))
        }
        val (scroll, editText) = codeViewer(content, editable)
        val bar = AppBar(this, R.drawable.ic_arrow_back, R.string.cd_back) { finish() }
        bar.title.text = file.name
        if (editable) bar.addTextAction(R.string.save) { save(editText.text.toString()) }
        root.addView(bar.view)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun save(text: String) {
        try {
            // Pengaturan "Hasilkan file cadangan": salinan lama -> nama.bak sebelum ditimpa.
            if (prefs.backupOnSave && file.exists()) {
                file.copyTo(File(file.parentFile, file.name + ".bak"), overwrite = true)
            }
            file.writeText(text, Charsets.UTF_8)
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.save_failed, e.message ?: e.javaClass.simpleName), Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val EXTRA_PATH = "path"
    }
}
