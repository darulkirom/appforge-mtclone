package com.test.mtclone.ui.apk

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.test.mtclone.R
import com.test.mtclone.core.apk.RepackUtil
import com.test.mtclone.core.apk.ZipTree
import com.test.mtclone.core.axml.AxmlDecoder
import com.test.mtclone.core.dex.DexBrowser
import com.test.mtclone.core.file.FileKind
import com.test.mtclone.core.sign.ApkSigning
import com.test.mtclone.ui.common.AppBar
import com.test.mtclone.ui.common.codeViewer
import com.test.mtclone.ui.common.color
import com.test.mtclone.ui.common.dp
import com.test.mtclone.ui.common.rippleDrawable
import java.io.File

/**
 * Menampilkan isi satu entry APK sesuai jenisnya:
 *  - classes*.dex  -> daftar class, ketuk untuk lihat smali (read-only)
 *  - AndroidManifest.xml -> didekode dari binary XML (read-only, best-effort)
 *  - file teks lain -> bisa diedit; "Simpan sbg APK" menulis APK baru yang otomatis
 *    ditandatangani testkey AOSP
 *  - lainnya -> hex dump
 */
class ApkEntryViewerActivity : AppCompatActivity() {

    private lateinit var apk: File
    private var zipName: String = ""
    private var dexClassShown: String? = null // null = daftar class, terisi = smali class itu
    private var pendingNewContent: ByteArray? = null

    private val saveAs = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri: Uri? -> if (uri != null) doSave(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apk = File(intent.getStringExtra(EXTRA_APK_PATH)!!)
        zipName = intent.getStringExtra(EXTRA_ZIP_NAME)!!
        render()
    }

    private fun render() {
        when {
            FileKind.isDex(zipName) -> renderDex()
            FileKind.isManifest(zipName) ->
                renderText(zipName, AxmlDecoder.decode(ZipTree.readBytes(apk, zipName)), editable = false)
            FileKind.isLikelyText(zipName) ->
                renderText(zipName, String(ZipTree.readBytes(apk, zipName), Charsets.UTF_8), editable = true)
            else -> {
                val bytes = ZipTree.readBytes(apk, zipName)
                var dump = FileKind.hexDump(bytes)
                if (bytes.size > FileKind.HEX_MAX) dump += "\n" + getString(R.string.hex_truncated, bytes.size)
                renderText(zipName, dump, editable = false)
            }
        }
    }

    // -------------------------------------------------------------------- dex

    private fun renderDex() {
        val cls = dexClassShown
        val dex = DexBrowser.open(apk, zipName)
        if (cls == null) {
            val names = DexBrowser.classNames(dex)
            setContentView(
                buildListUi(getString(R.string.dex_title, zipName, names.size), names) { picked ->
                    dexClassShown = picked
                    render()
                }
            )
        } else {
            val smali = DexBrowser.smaliOf(dex, cls) ?: getString(R.string.smali_failed, cls)
            setContentView(buildTextUi(cls, smali, editable = false, onBack = { dexClassShown = null; render() }, onSave = null))
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (FileKind.isDex(zipName) && dexClassShown != null) {
            dexClassShown = null
            render()
        } else {
            super.onBackPressed()
        }
    }

    // ------------------------------------------------------------------- teks

    private fun renderText(title: String, content: String, editable: Boolean) {
        val saveHandler: (String) -> Unit = { text ->
            pendingNewContent = text.toByteArray(Charsets.UTF_8)
            saveAs.launch("modified.apk")
        }
        setContentView(
            buildTextUi(title, content, editable, onBack = { finish() }, onSave = if (editable) saveHandler else null)
        )
    }

    /** Susun ulang APK dengan entry yang diganti, lalu tanda tangani (testkey AOSP) dan tulis ke [uri]. */
    private fun doSave(uri: Uri) {
        val content = pendingNewContent ?: return
        val appContext = applicationContext
        Toast.makeText(this, R.string.signing_progress, Toast.LENGTH_SHORT).show()
        Thread {
            val tmp = File(cacheDir, "repack_tmp.apk")
            val signed = File(cacheDir, "repack_signed.apk")
            val error: String? = try {
                tmp.outputStream().use { RepackUtil.rebuildWithReplacedEntry(apk, zipName, content, it) }
                ApkSigning.sign(appContext, tmp, signed)
                contentResolver.openOutputStream(uri)?.use { out -> signed.inputStream().use { it.copyTo(out) } }
                null
            } catch (e: Exception) {
                e.message ?: e.javaClass.simpleName
            } finally {
                tmp.delete()
                signed.delete()
            }
            runOnUiThread {
                val msg = if (error == null) getString(R.string.save_signed_done) else getString(R.string.sign_failed, error)
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    // --------------------------------------------------------------------- UI

    private fun buildListUi(title: String, items: List<String>, onClick: (String) -> Unit): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.pane_inactive))
        }
        val bar = AppBar(this, R.drawable.ic_arrow_back, R.string.cd_back) { finish() }
        bar.title.text = title
        root.addView(bar.view)

        val rv = RecyclerView(this).apply { layoutManager = LinearLayoutManager(context) }
        rv.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val tv = TextView(parent.context).apply {
                    textSize = 13f
                    typeface = android.graphics.Typeface.MONOSPACE
                    setTextColor(color(R.color.text_primary))
                    setPadding(dp(16), dp(10), dp(16), dp(10))
                    background = rippleDrawable()
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
            setBackgroundColor(color(R.color.pane_inactive))
        }
        val (scroll, editText) = codeViewer(content, editable)
        val bar = AppBar(this, R.drawable.ic_arrow_back, R.string.cd_back) { onBack() }
        bar.title.text = title
        if (onSave != null) bar.addTextAction(R.string.save_as_apk) { onSave(editText.text.toString()) }
        root.addView(bar.view)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    companion object {
        const val EXTRA_APK_PATH = "apkPath"
        const val EXTRA_ZIP_NAME = "zipName"
    }
}
