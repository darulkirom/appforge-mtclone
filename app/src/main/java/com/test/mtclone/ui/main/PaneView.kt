package com.test.mtclone.ui.main

import android.content.Context
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.test.mtclone.R
import com.test.mtclone.core.file.FileOps
import com.test.mtclone.core.prefs.AppPrefs
import com.test.mtclone.ui.common.color
import java.io.File

/** Satu jendela file manager (kiri/kanan). Tiap jendela punya folder, riwayat, dan seleksi sendiri. */
class PaneView(
    context: Context,
    val index: Int,
    private val prefs: AppPrefs,
    startDir: File,
    private val callbacks: Callbacks
) : FrameLayout(context) {

    interface Callbacks {
        fun onActivate(index: Int)
        fun onOpen(index: Int, row: Row)
        fun onParent(index: Int)
        fun onLongPress(index: Int, row: Row)
        fun onToggleSelect(index: Int, row: Row)
    }

    var state = PaneState(startDir)

    var folderCount: Int = 0
        private set
    var fileCount: Int = 0
        private set

    private val rowAdapter = FileRowAdapter(
        context, prefs,
        isSelected = { r -> val f = r.file; f != null && state.selected.contains(f) },
        onClick = { r ->
            callbacks.onActivate(index)
            if (r.isParent) callbacks.onParent(index) else callbacks.onOpen(index, r)
        },
        onLongClick = { r ->
            callbacks.onActivate(index)
            callbacks.onLongPress(index, r)
        },
        onIconClick = { r ->
            callbacks.onActivate(index)
            callbacks.onToggleSelect(index, r)
        }
    )

    private val list = RecyclerView(context).apply {
        layoutManager = LinearLayoutManager(context)
        adapter = rowAdapter
        setOnTouchListener { _, ev ->
            if (ev.action == MotionEvent.ACTION_DOWN) callbacks.onActivate(index)
            false
        }
        addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) callbacks.onActivate(index)
            }
        })
    }

    init {
        addView(list, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setActive(false)
    }

    fun setActive(active: Boolean) {
        setBackgroundColor(context.color(if (active) R.color.pane_active else R.color.pane_inactive))
    }

    fun redraw() = rowAdapter.redraw()

    /** Muat ulang isi jendela. [allowed] = false kalau izin akses file belum diberikan. */
    fun refresh(allowed: Boolean, resetScroll: Boolean = false) {
        if (!allowed) {
            folderCount = 0
            fileCount = 0
            rowAdapter.submit(emptyList())
            return
        }
        val rows = ArrayList<Row>()
        val apkNode = state.apkNode()
        if (state.inApk && apkNode != null) {
            // Isi APK sebagai folder
            val kids = apkNode.children.values.toList()
            val folders = kids.filter { it.isDir }
            val files = kids.filter { !it.isDir }
            folderCount = folders.size
            fileCount = files.size
            rows.add(Row(null, null, true))
            folders.forEach { rows.add(Row(null, it, false)) }
            files.forEach { rows.add(Row(null, it, false)) }
        } else {
            val dir = state.dir
            val spec = prefs.getSort(index, dir.absolutePath)
            val listing = FileOps.listDir(dir, spec, prefs.showHidden, state.filter)
            state.selected.retainAll { it.exists() }
            folderCount = listing.folders.size
            fileCount = listing.files.size
            if (dir.parentFile != null) rows.add(Row(dir, null, true))
            listing.folders.forEach { rows.add(Row(it, null, false)) }
            listing.files.forEach { rows.add(Row(it, null, false)) }
        }
        rowAdapter.submit(rows)
        if (resetScroll) list.scrollToPosition(0)
    }
}
