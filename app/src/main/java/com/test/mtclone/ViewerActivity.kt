package com.test.mtclone

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

/**
 * Menampilkan isi satu entry APK sesuai jenisnya:
 *  - classes*.dex  -> daftar class, tap untuk lihat smali (read-only)
 *  - AndroidManifest.xml -> didekode dari binary XML (read-only, best-effort)
 *  - file teks lain -> bisa diedit, simpan sebagai APK baru (belum ditandatangani)
 *  - lainnya -> hex dump
 */
class ViewerActivity : AppCompatActivity() {

    private lateinit var apk: File
    private var zipName: String = ""
    private var dexClassShown: String? = null // null = daftar class, terisi = smali class itu

    private val saveAs = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri: Uri? -> if (uri != null) doSave(uri) }

    private var pendingNewContent: ByteArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apk = File(intent.getStringExtra("apkPath")!!)
        zipName = intent.getStringExtra("zipName")!!
        render()
    }

    private fun render() {
        when {
            FileKind.isDex(zipName) -> renderDex()
            FileKind.isManifest(zipName) -> renderText(zipName, AxmlDecoder.decode(ZipTree.readBytes(apk, zipName)), editable = false)
            FileKind.isLikelyText(zipName) -> renderText(zipName, String(ZipTree.readBytes(apk, zipName), Charsets.UTF_8), editable = true)
            else -> renderText(zipName, FileKind.hexDump(ZipTree.readBytes(apk, zipName)), editable = false)
        }
    }

    // ---------------------------------------------------------------- dex

    private fun renderDex() {
        val cls = dexClassShown
        if (cls == null) {
            val dex = DexBrowser.open(apk, zipName)
            val names = DexBrowser.classNames(dex)
            setContentView(buildListUi(zipName, names) { picked ->
                dexClassShown = picked
                render()
            })
        } else {
            val dex = DexBrowser.open(apk, zipName)
            val smali = DexBrowser.smaliOf(dex, cls)
            setContentView(buildTextUi(cls, smali, editable = false, onBack = { dexClassShown = null; render() }, onSave = null))
        }
    }

    override fun onBackPressed() {
        if (FileKind.isDex(zipName) && dexClassShown != null) {
            dexClassShown = null
            render()
        } else {
            super.onBackPressed()
        }
    }

    // --------------------------------------------------------------- text

    private fun renderText(title: String, content: String, editable: Boolean) {
        setContentView(buildTextUi(title, content, editable, onBack = { finish() }, onSave = if (editable) { text ->
            pendingNewContent = text.toByteArray(Charsets.UTF_8)
            saveAs.launch("modified.apk")
        } else null))
    }

    private fun doSave(uri: Uri) {
        val content = pendingNewContent ?: return
        contentResolver.openOutputStream(uri)?.use { out ->
            RepackUtil.rebuildWithReplacedEntry(apk, zipName, content, out)
        }
        Toast.makeText(this, "Disimpan. APK ini belum ditandatangani.", Toast.LENGTH_LONG).show()
    }

    // ------------------------------------------------------------------ UI

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun toolbar(title: String, onBack: () -> Unit, action: Pair<String, () -> Unit>? = null): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(28), dp(16), dp(12))
            setBackgroundColor(Color.parseColor("#111A2E"))
        }
        val back = TextView(this).apply {
            text = "←"; textSize = 20f; setTextColor(Color.WHITE)
            setPadding(0, 0, dp(12), 0)
            setOnClickListener { onBack() }
        }
        val t = TextView(this).apply {
            text = title; textSize = 15f; setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            setSingleLine(true)
        }
        bar.addView(back)
        bar.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (action != null) {
            val btn = TextView(this).apply {
                text = action.first; textSize = 14f; setTextColor(Color.parseColor("#60A5FA"))
                setOnClickListener { action.second() }
            }
            bar.addView(btn)
        }
        return bar
    }

    private fun buildListUi(title: String, items: List<String>, onClick: (String) -> Unit): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B1220"))
        }
        root.addView(toolbar("$title (${items.size} class)", { finish() }))
        val rv = RecyclerView(this).apply { layoutManager = LinearLayoutManager(context) }
        rv.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val tv = TextView(parent.context).apply {
                    textSize = 13f
                    typeface = Typeface.MONOSPACE
                    setTextColor(Color.parseColor("#E2E8F0"))
                    setPadding(dp(16), dp(10), dp(16), dp(10))
                }
                return object : RecyclerView.ViewHolder(tv) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val tv = holder.itemView as TextView
                tv.text = items[position]
                tv.setOnClickListener { onClick(items[position]) }
            }
            override fun getItemCount() = items.size
        }
        root.addView(rv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun buildTextUi(
        title: String, content: String, editable: Boolean,
        onBack: () -> Unit, onSave: ((String) -> Unit)?
    ): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B1220"))
        }
        lateinit var editText: EditText
        root.addView(toolbar(title, onBack, if (onSave != null) "Simpan sbg APK" to { onSave(editText.text.toString()) } else null))

        val scroll = ScrollView(this)
        editText = EditText(this).apply {
            setText(content)
            isEnabled = editable
            isFocusable = editable
            isFocusableInTouchMode = editable
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
