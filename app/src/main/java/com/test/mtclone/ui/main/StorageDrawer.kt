package com.test.mtclone.ui.main

import android.content.Context
import android.content.res.ColorStateList
import android.os.Environment
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.StringRes
import com.test.mtclone.R
import com.test.mtclone.core.file.FileOps
import com.test.mtclone.core.prefs.AppPrefs
import com.test.mtclone.ui.common.color
import com.test.mtclone.ui.common.dp
import com.test.mtclone.ui.common.rippleDrawable
import java.io.File

/**
 * Panel pintasan yang meluncur dari kiri (tombol ☰), mengikuti drawer MT Manager:
 * bagian "Lokal" (penyimpanan + pemakaiannya) dan "Bookmark".
 */
class StorageDrawer(
    private val ctx: Context,
    private val prefs: AppPrefs,
    private val onPick: (File) -> Unit
) {
    private val panelWidth = (ctx.resources.displayMetrics.widthPixels * 0.78f).toInt()
    private val collapsed = HashSet<String>()

    private val content = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    private val scrim = View(ctx).apply {
        setBackgroundColor(0x99000000.toInt())
        visibility = View.GONE
        setOnClickListener { close() }
    }

    private val panel = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(ctx.color(R.color.pane_inactive))
        isClickable = true // telan ketukan supaya tidak tembus ke scrim
        visibility = View.INVISIBLE
        translationX = -panelWidth.toFloat()
    }

    var isOpen: Boolean = false
        private set

    init {
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(ctx.color(R.color.mt_toolbar))
            setPadding(ctx.dp(16), ctx.dp(14), ctx.dp(16), ctx.dp(14))
        }
        header.addView(
            ImageView(ctx).apply { setImageResource(R.mipmap.ic_launcher) },
            LinearLayout.LayoutParams(ctx.dp(40), ctx.dp(40)).apply { rightMargin = ctx.dp(12) }
        )
        header.addView(TextView(ctx).apply {
            setText(R.string.app_name)
            textSize = 18f
            setTextColor(ctx.color(R.color.text_primary))
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        val scroll = ScrollView(ctx)
        scroll.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        panel.addView(header)
        panel.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun attach(frame: FrameLayout) {
        frame.addView(scrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        frame.addView(panel, FrameLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun open() {
        rebuild()
        scrim.visibility = View.VISIBLE
        panel.visibility = View.VISIBLE
        panel.animate().translationX(0f).setDuration(180).start()
        isOpen = true
    }

    fun close() {
        isOpen = false
        panel.animate().translationX(-panelWidth.toFloat()).setDuration(160).withEndAction {
            scrim.visibility = View.GONE
            panel.visibility = View.INVISIBLE
        }.start()
    }

    // ------------------------------------------------------------------ isi

    private fun rebuild() {
        content.removeAllViews()

        section(R.string.drawer_local, "local") {
            storage(R.string.drawer_root, File("/"))
            storage(R.string.drawer_storage, Environment.getExternalStorageDirectory())
            sdCardRoot()?.let { storage(R.string.drawer_sdcard, it) }
        }

        val places = ArrayList<Pair<String, File>>()
        prefs.homepage?.let { home ->
            val f = File(home)
            if (f.isDirectory) places.add(ctx.getString(R.string.drawer_home) to f)
        }
        for (p in prefs.bookmarks()) {
            val f = File(p)
            places.add((f.name.ifEmpty { p }) to f)
        }
        if (places.isNotEmpty()) {
            section(R.string.drawer_bookmarks, "bookmarks") {
                for ((name, f) in places) place(name, f.absolutePath, f)
            }
        }
    }

    private fun section(@StringRes title: Int, key: String, body: () -> Unit) {
        val open = !collapsed.contains(key)
        val head = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(16), ctx.dp(14), ctx.dp(16), ctx.dp(6))
            background = ctx.rippleDrawable()
            setOnClickListener {
                if (open) collapsed.add(key) else collapsed.remove(key)
                rebuild()
            }
        }
        head.addView(TextView(ctx).apply {
            setText(title)
            textSize = 12.5f
            setTextColor(ctx.color(R.color.mt_accent))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(TextView(ctx).apply {
            text = if (open) "▴" else "▾"
            textSize = 14f
            setTextColor(ctx.color(R.color.text_secondary))
        })
        content.addView(head)
        if (open) body()
    }

    /** Baris penyimpanan dengan bar pemakaian. */
    private fun storage(@StringRes name: Int, root: File) {
        val total = root.totalSpace
        val free = root.freeSpace
        val used = total - free
        val pct = if (total > 0) ((used * 100) / total).toInt().coerceIn(0, 100) else 0

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.dp(16), ctx.dp(10), ctx.dp(16), ctx.dp(10))
            background = ctx.rippleDrawable()
            setOnClickListener { pick(root) }
        }
        row.addView(TextView(ctx).apply {
            setText(name)
            textSize = 14f
            setTextColor(ctx.color(R.color.text_primary))
        })
        row.addView(
            ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = pct
                progressTintList = ColorStateList.valueOf(ctx.color(R.color.mt_accent))
                progressBackgroundTintList = ColorStateList.valueOf(ctx.color(R.color.divider))
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(6)).apply { topMargin = ctx.dp(4) }
        )
        row.addView(TextView(ctx).apply {
            text = ctx.getString(R.string.drawer_usage, FileOps.formatSize(used), FileOps.formatSize(free)) + "  ($pct%)"
            textSize = 10.5f
            setTextColor(ctx.color(R.color.text_secondary))
        })
        content.addView(row)
    }

    private fun place(name: String, path: String, dir: File) {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.dp(16), ctx.dp(10), ctx.dp(16), ctx.dp(10))
            background = ctx.rippleDrawable()
            setOnClickListener { pick(dir) }
        }
        row.addView(TextView(ctx).apply {
            text = name
            textSize = 14f
            setTextColor(ctx.color(R.color.text_primary))
        })
        row.addView(TextView(ctx).apply {
            text = path
            textSize = 10.5f
            setTextColor(ctx.color(R.color.text_secondary))
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.MIDDLE
        })
        content.addView(row)
    }

    private fun pick(dir: File) {
        close()
        onPick(dir)
    }

    /** Akar kartu SD, diturunkan dari folder app di volume eksternal kedua (kalau ada). */
    private fun sdCardRoot(): File? {
        val dirs = ctx.getExternalFilesDirs(null) ?: return null
        for (i in 1 until dirs.size) {
            val path = dirs[i]?.absolutePath ?: continue
            val idx = path.indexOf("/Android/data")
            if (idx > 0) return File(path.substring(0, idx))
        }
        return null
    }
}
