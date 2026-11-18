package com.test.mtclone

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

/**
 * Explorer dua-kolom (folder di satu sisi, file di sisi lain -- satu direktori yang sama,
 * cuma ditampilkan terpisah supaya lebih ringkas & cepat dibaca, meniru tampilan MT Manager).
 * Ini BUKAN dual-pane navigasi independen (kiri & kanan jelajah path berbeda) -- "Tukar sisi"
 * di sini cuma menukar sisi mana yang menampilkan folder vs file.
 */
class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("mtclone", Context.MODE_PRIVATE) }

    private var currentDir: File = Environment.getExternalStorageDirectory()
    private val back = ArrayDeque<File>()
    private val fwd = ArrayDeque<File>()

    private var showHidden = false
    private var sortBy = SortBy.NAME
    private var ascending = true
    private var swapped = false // true = folder tampil di kanan, file di kiri

    private lateinit var pathLabel: TextView
    private lateinit var statLabel: TextView
    private lateinit var leftList: RecyclerView
    private lateinit var rightList: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val home = prefs.getString("homepage", null)
        if (home != null) currentDir = File(home)
        setContentView(buildUi())
        ensurePermission()
        render()
    }

    override fun onResume() {
        super.onResume()
        if (hasPermission()) render()
    }

    // ------------------------------------------------------------ izin

    private fun hasPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun ensurePermission() {
        if (hasPermission()) return
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
            Toast.makeText(this, "Izinkan \"Akses semua file\" lalu kembali ke app.", Toast.LENGTH_LONG).show()
        } else {
            requestPermissions(
                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                1
            )
        }
    }

    // --------------------------------------------------------- navigasi

    private fun navigateTo(dir: File, pushHistory: Boolean = true) {
        if (pushHistory && currentDir != dir) { back.addLast(currentDir); fwd.clear() }
        currentDir = dir
        render()
    }

    private fun goBack() {
        if (back.isEmpty()) return
        fwd.addLast(currentDir)
        currentDir = back.removeLast()
        render()
    }

    private fun goForward() {
        if (fwd.isEmpty()) return
        back.addLast(currentDir)
        currentDir = fwd.removeLast()
        render()
    }

    private fun goUp() {
        currentDir.parentFile?.let { navigateTo(it) }
    }

    // ------------------------------------------------------------ render

    private fun render() {
        if (!hasPermission()) {
            pathLabel.text = currentDir.absolutePath
            statLabel.text = "Menunggu izin akses file…"
            leftList.adapter = EntryAdapter(emptyList())
            rightList.adapter = EntryAdapter(emptyList())
            return
        }
        val (folders, files) = FileOps.listDir(currentDir, sortBy, ascending, showHidden)
        pathLabel.text = currentDir.absolutePath
        val free = currentDir.freeSpace
        val total = currentDir.totalSpace
        statLabel.text = "Folder: ${folders.size}  File: ${files.size}  Penyimpanan: ${FileOps.humanSize(total - free)}/${FileOps.humanSize(total)}"

        val folderAdapter = EntryAdapter(folders)
        val fileAdapter = EntryAdapter(files)
        if (!swapped) {
            leftList.adapter = folderAdapter
            rightList.adapter = fileAdapter
        } else {
            leftList.adapter = fileAdapter
            rightList.adapter = folderAdapter
        }
    }

    private fun onEntryClick(f: File) {
        if (f.isDirectory) {
            navigateTo(f)
        } else if (FileOps.isApk(f)) {
            showOpenWithDialog(f)
        } else {
            startActivity(Intent(this, LocalFileViewer::class.java).putExtra("path", f.absolutePath))
        }
    }

    // ------------------------------------------------------- buka dgn...

    private fun showOpenWithDialog(f: File) {
        val options = listOf("Install", "Melihat", "Tanda tangani file", "Konversikan ke apk")
        AlertDialog.Builder(this)
            .setTitle("Buka dengan…")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Install" -> installApk(f)
                    "Melihat" -> startActivity(Intent(this, ApkOpenActivity::class.java).putExtra("apkPath", f.absolutePath))
                    "Tanda tangani file" -> Toast.makeText(this, "Fitur tanda tangan ada di proyek ApkForge, belum ada di MTClone.", Toast.LENGTH_LONG).show()
                    "Konversikan ke apk" -> Toast.makeText(this, "Belum diimplementasikan.", Toast.LENGTH_SHORT).show()
                }
            }.show()
    }

    private fun installApk(f: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    // --------------------------------------------------------- buat baru

    private fun showCreateDialog() {
        val input = EditText(this)
        val dlg = AlertDialog.Builder(this)
            .setTitle("Membuat")
            .setView(input)
            .setNegativeButton("MEMBATALKAN", null)
            .setNeutralButton("FILE") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty() && FileOps.createFile(currentDir, name)) render()
                else if (name.isNotEmpty()) Toast.makeText(this, "Gagal membuat file.", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("FOLDER") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty() && FileOps.createFolder(currentDir, name)) render()
                else if (name.isNotEmpty()) Toast.makeText(this, "Gagal membuat folder.", Toast.LENGTH_SHORT).show()
            }
            .create()
        dlg.show()
    }

    // ------------------------------------------------------- menu (⋮)

    private fun showOverflowMenu(anchor: View) {
        val items = listOf(
            "Menyegarkan", "Menyortir", "File tersembunyi: ${if (showHidden) "ON" else "OFF"}",
            "Tambahkan bookmark", "Setel sebagai homepage", "Buka terminal", "keluar"
        )
        AlertDialog.Builder(this).setItems(items.toTypedArray()) { _, which ->
            when (items[which].substringBefore(":")) {
                "Menyegarkan" -> render()
                "Menyortir" -> showSortDialog()
                "File tersembunyi" -> { showHidden = !showHidden; render() }
                "Tambahkan bookmark" -> {
                    val set = (prefs.getStringSet("bookmarks", emptySet()) ?: emptySet()).toMutableSet()
                    set.add(currentDir.absolutePath)
                    prefs.edit().putStringSet("bookmarks", set).apply()
                    Toast.makeText(this, "Bookmark ditambahkan.", Toast.LENGTH_SHORT).show()
                }
                "Setel sebagai homepage" -> {
                    prefs.edit().putString("homepage", currentDir.absolutePath).apply()
                    Toast.makeText(this, "Homepage diset ke sini.", Toast.LENGTH_SHORT).show()
                }
                "Buka terminal" -> Toast.makeText(this, "Belum tersedia di MTClone.", Toast.LENGTH_SHORT).show()
                "keluar" -> finishAffinity()
            }
        }.show()
    }

    private fun showSortDialog() {
        val items = arrayOf("Nama", "Ukuran", "Tanggal")
        val current = when (sortBy) { SortBy.NAME -> 0; SortBy.SIZE -> 1; SortBy.DATE -> 2 }
        AlertDialog.Builder(this).setTitle("Urutkan berdasarkan").setSingleChoiceItems(items, current) { d, i ->
            sortBy = when (i) { 0 -> SortBy.NAME; 1 -> SortBy.SIZE; else -> SortBy.DATE }
            d.dismiss(); render()
        }.show()
    }

    // ------------------------------------------------------------ UI

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B1220"))
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(28), dp(16), dp(10))
            setBackgroundColor(Color.parseColor("#111A2E"))
        }
        toolbar.addView(TextView(this).apply {
            text = "MT Clone"; textSize = 17f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        toolbar.addView(TextView(this).apply {
            text = "⋮"; textSize = 20f; setTextColor(Color.WHITE)
            setPadding(dp(12), 0, dp(4), 0)
            setOnClickListener { showOverflowMenu(it) }
        })

        pathLabel = TextView(this).apply {
            textSize = 12f; setTextColor(Color.parseColor("#93C5FD"))
            setPadding(dp(16), dp(8), dp(16), dp(0))
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            setSingleLine(true)
        }
        statLabel = TextView(this).apply {
            textSize = 11f; setTextColor(Color.parseColor("#64748B"))
            setPadding(dp(16), dp(2), dp(16), dp(8))
        }

        leftList = RecyclerView(this).apply { layoutManager = LinearLayoutManager(context) }
        rightList = RecyclerView(this).apply { layoutManager = LinearLayoutManager(context) }
        val divider = View(this).apply { setBackgroundColor(Color.parseColor("#1E293B")) }
        val panes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        panes.addView(leftList, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        panes.addView(divider, LinearLayout.LayoutParams(dp(1), ViewGroup.LayoutParams.MATCH_PARENT))
        panes.addView(rightList, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#111A2E"))
            setPadding(0, dp(10), 0, dp(10))
        }
        fun navBtn(label: String, onClick: () -> Unit) = TextView(this).apply {
            text = label; textSize = 18f; setTextColor(Color.parseColor("#CBD5E1"))
            gravity = Gravity.CENTER
            setOnClickListener { onClick() }
        }
        bottomBar.addView(navBtn("‹") { goBack() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bottomBar.addView(navBtn("›") { goForward() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bottomBar.addView(navBtn("+") { showCreateDialog() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bottomBar.addView(navBtn("⇄") { swapped = !swapped; render() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bottomBar.addView(navBtn("↑") { goUp() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        root.addView(toolbar)
        root.addView(pathLabel)
        root.addView(statLabel)
        root.addView(panes, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomBar)
        return root
    }

    private inner class EntryAdapter(val items: List<File>) : RecyclerView.Adapter<EntryAdapter.VH>() {
        inner class VH(val row: LinearLayout, val icon: TextView, val name: TextView, val meta: TextView) : RecyclerView.ViewHolder(row)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                gravity = Gravity.CENTER_VERTICAL
            }
            val icon = TextView(parent.context).apply { textSize = 16f; setTextColor(Color.WHITE) }
            val col = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL }
            val name = TextView(parent.context).apply {
                textSize = 13f; setTextColor(Color.WHITE)
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE; setSingleLine(true)
            }
            val meta = TextView(parent.context).apply { textSize = 10f; setTextColor(Color.parseColor("#64748B")) }
            col.addView(name); col.addView(meta)
            row.addView(icon, LinearLayout.LayoutParams(dp(26), ViewGroup.LayoutParams.WRAP_CONTENT))
            row.addView(col)
            return VH(row, icon, name, meta)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val f = items[position]
            holder.icon.text = when {
                f.isDirectory -> "📁"
                FileOps.isApk(f) -> "📦"
                FileKind.isLikelyText(f.name) -> "📄"
                else -> "🗎"
            }
            holder.name.text = f.name
            holder.meta.text = if (f.isDirectory) "" else FileOps.humanSize(f.length())
            holder.row.setOnClickListener { onEntryClick(f) }
        }

        override fun getItemCount() = items.size
    }
}
