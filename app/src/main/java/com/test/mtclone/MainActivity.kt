package com.test.mtclone

import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var list: RecyclerView
    private lateinit var empty: TextView
    private lateinit var pathLabel: TextView

    private var apkFile: File? = null
    private var root: ZipNode? = null
    private var current: ZipNode? = null
    private val stack = ArrayDeque<ZipNode>()

    private val pickApk = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) openApk(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        openPicker()
    }

    private fun openPicker() {
        pickApk.launch(arrayOf("application/vnd.android.package-archive", "*/*"))
    }

    private fun openApk(uri: Uri) {
        val tmp = File(cacheDir, "opened.apk")
        contentResolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { out -> input.copyTo(out) }
        }
        apkFile = tmp
        root = ZipTree.build(tmp)
        stack.clear()
        current = root
        render()
    }

    private fun render() {
        val node = current ?: return
        pathLabel.text = "/" + stack.joinToString("/") { it.name }
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
            val intent = Intent(this, ViewerActivity::class.java)
            intent.putExtra("apkPath", apk.absolutePath)
            intent.putExtra("zipName", entry.zipName)
            startActivity(intent)
        }
    }

    override fun onBackPressed() {
        if (stack.isNotEmpty()) {
            stack.removeLast()
            current = if (stack.isEmpty()) root else stack.last()
            render()
        } else {
            super.onBackPressed()
        }
    }

    // ------------------------------------------------------------------ UI

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B1220"))
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(28), dp(16), dp(12))
            setBackgroundColor(Color.parseColor("#111A2E"))
        }
        val title = TextView(this).apply {
            text = "MT Clone"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        val openBtn = TextView(this).apply {
            text = "Buka APK"
            textSize = 14f
            setTextColor(Color.parseColor("#60A5FA"))
            setOnClickListener { openPicker() }
        }
        toolbar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        toolbar.addView(openBtn)

        pathLabel = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(dp(16), dp(6), dp(16), dp(6))
            setBackgroundColor(Color.parseColor("#0F172A"))
        }

        val container = FrameLayout(this)
        list = RecyclerView(this).apply { layoutManager = LinearLayoutManager(context) }
        empty = TextView(this).apply {
            text = "Belum ada APK dibuka.\nKetuk \"Buka APK\" di pojok kanan atas."
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#475569"))
            visibility = View.VISIBLE
        }
        container.addView(list, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        container.addView(empty, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        root.addView(toolbar)
        root.addView(pathLabel)
        root.addView(container, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private inner class EntryAdapter(
        val items: List<ZipNode>,
        val onClick: (ZipNode) -> Unit
    ) : RecyclerView.Adapter<EntryAdapter.VH>() {

        inner class VH(val row: LinearLayout, val icon: TextView, val name: TextView, val meta: TextView) :
            RecyclerView.ViewHolder(row)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                gravity = Gravity.CENTER_VERTICAL
            }
            val icon = TextView(parent.context).apply { textSize = 18f; setTextColor(Color.WHITE) }
            val col = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL }
            val name = TextView(parent.context).apply { textSize = 15f; setTextColor(Color.WHITE) }
            val meta = TextView(parent.context).apply { textSize = 11f; setTextColor(Color.parseColor("#64748B")) }
            col.addView(name)
            col.addView(meta)
            row.addView(icon, LinearLayout.LayoutParams(dp(32), ViewGroup.LayoutParams.WRAP_CONTENT))
            row.addView(col)
            return VH(row, icon, name, meta)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val e = items[position]
            holder.icon.text = if (e.isDir) "📁" else iconFor(e.name)
            holder.name.text = e.name
            holder.meta.text = if (e.isDir) "${e.children.size} item" else "${e.size} byte"
            holder.row.setOnClickListener { onClick(e) }
        }

        override fun getItemCount() = items.size

        private fun iconFor(name: String): String = when {
            FileKind.isDex(name) -> "🧩"
            FileKind.isManifest(name) -> "📋"
            FileKind.isLikelyText(name) -> "📄"
            else -> "🗎"
        }
    }
}
