package com.test.mtclone.ui.apk

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.test.mtclone.R
import com.test.mtclone.core.apk.ZipNode
import com.test.mtclone.core.apk.ZipTree
import com.test.mtclone.core.file.FileOps
import com.test.mtclone.ui.common.AppBar
import com.test.mtclone.ui.common.IconKind
import com.test.mtclone.ui.common.bindKind
import com.test.mtclone.ui.common.color
import com.test.mtclone.ui.common.dp
import com.test.mtclone.ui.common.newIconBadge
import com.test.mtclone.ui.common.rippleDrawable
import java.io.File

/** Menelusuri isi satu APK (zip) sebagai folder. */
class ApkOpenActivity : AppCompatActivity() {

    private lateinit var bar: AppBar
    private lateinit var list: RecyclerView
    private lateinit var empty: TextView

    private var apkFile: File? = null
    private var root: ZipNode? = null
    private var current: ZipNode? = null
    private val stack = ArrayDeque<ZipNode>()

    private val pickApk = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) openApk(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        // Dipanggil dari MainActivity dengan path langsung -> lewati file picker.
        val directPath = intent.getStringExtra(EXTRA_APK_PATH)
        if (directPath != null) openApkFile(File(directPath)) else openPicker()
    }

    private fun openPicker() {
        pickApk.launch(arrayOf("application/vnd.android.package-archive", "*/*"))
    }

    private fun openApk(uri: Uri) {
        val tmp = File(cacheDir, "opened.apk")
        contentResolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { out -> input.copyTo(out) }
        }
        openApkFile(tmp)
    }

    private fun openApkFile(f: File) {
        apkFile = f
        root = ZipTree.build(f)
        stack.clear()
        current = root
        render()
    }

    private fun render() {
        val node = current ?: return
        bar.title.text = getString(R.string.apk_path_root, stack.joinToString("/") { it.name })
        val items = node.children.values.sortedWith(compareByDescending<ZipNode> { it.isDir }.thenBy { it.name })
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        list.adapter = EntryAdapter(items) { entry -> onEntryClick(entry) }
    }

    private fun onEntryClick(entry: ZipNode) {
        if (entry.isDir) {
            stack.addLast(entry)
            current = entry
            render()
        } else {
            val apk = apkFile ?: return
            startActivity(
                Intent(this, ApkEntryViewerActivity::class.java)
                    .putExtra(ApkEntryViewerActivity.EXTRA_APK_PATH, apk.absolutePath)
                    .putExtra(ApkEntryViewerActivity.EXTRA_ZIP_NAME, entry.zipName)
            )
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (stack.isNotEmpty()) {
            stack.removeLast()
            current = if (stack.isEmpty()) root else stack.last()
            render()
        } else {
            super.onBackPressed()
        }
    }

    // --------------------------------------------------------------------- UI

    private fun buildUi(): View {
        val rootView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.pane_inactive))
        }
        bar = AppBar(this, R.drawable.ic_arrow_back, R.string.cd_back) { finish() }
        bar.addTextAction(R.string.apk_open_button) { openPicker() }

        list = RecyclerView(this).apply { layoutManager = LinearLayoutManager(context) }
        empty = TextView(this).apply {
            setText(R.string.apk_empty)
            gravity = Gravity.CENTER
            setTextColor(color(R.color.text_secondary))
            visibility = View.VISIBLE
        }
        val container = FrameLayout(this)
        container.addView(list, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        container.addView(empty, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        rootView.addView(bar.view)
        rootView.addView(container, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return rootView
    }

    private inner class EntryAdapter(
        val items: List<ZipNode>,
        val onClick: (ZipNode) -> Unit
    ) : RecyclerView.Adapter<EntryAdapter.VH>() {

        inner class VH(val row: LinearLayout, val badge: ImageView, val name: TextView, val meta: TextView) :
            RecyclerView.ViewHolder(row)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(12), dp(8), dp(12), dp(8))
                gravity = Gravity.CENTER_VERTICAL
                background = rippleDrawable()
            }
            val badge = newIconBadge(36)
            val col = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL }
            val name = TextView(parent.context).apply {
                textSize = 14f
                setTextColor(color(R.color.text_primary))
            }
            val meta = TextView(parent.context).apply {
                textSize = 10.5f
                setTextColor(color(R.color.text_secondary))
            }
            col.addView(name)
            col.addView(meta)
            row.addView(badge, LinearLayout.LayoutParams(dp(36), dp(36)).apply { rightMargin = dp(12) })
            row.addView(col)
            return VH(row, badge, name, meta)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val e = items[position]
            holder.badge.bindKind(if (e.isDir) IconKind.FOLDER else IconKind.FILE)
            holder.name.text = e.name
            holder.meta.text =
                if (e.isDir) getString(R.string.apk_n_items, e.children.size) else FileOps.formatSize(e.size)
            holder.row.setOnClickListener { onClick(e) }
        }

        override fun getItemCount() = items.size
    }

    companion object {
        const val EXTRA_APK_PATH = "apkPath"
    }
}
