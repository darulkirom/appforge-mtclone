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

/** Satu jendela file manager (kiri/kanan). Tiap jendela punya folder & riwayat sendiri. */
class PaneView(
    context: Context,
    val index: Int,
    private val prefs: AppPrefs,
    startDir: File,
    private val callbacks: Callbacks
) : FrameLayout(context) {

    interface Callbacks {
        fun onActivate(index: Int)
        fun onOpen(index: Int, file: File)
        fun onParent(index: Int)
    }

    var state = PaneState(startDir)

    var folderCount: Int = 0
        private set
    var fileCount: Int = 0
        private set

    private val rowAdapter = FileRowAdapter(context, prefs) { row ->
        callbacks.onActivate(index)
        if (row.isParent) callbacks.onParent(index) else callbacks.onOpen(index, row.file)
    }

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

    /** Muat ulang isi folder. [allowed] = false kalau izin akses file belum diberikan. */
    fun refresh(allowed: Boolean, resetScroll: Boolean = false) {
        if (!allowed) {
            folderCount = 0
            fileCount = 0
            rowAdapter.submit(emptyList())
            return
        }
        val dir = state.dir
        val spec = prefs.getSort(index, dir.absolutePath)
        val listing = FileOps.listDir(dir, spec, prefs.showHidden, state.filter)
        folderCount = listing.folders.size
        fileCount = listing.files.size

        val rows = ArrayList<Row>(listing.folders.size + listing.files.size + 1)
        if (dir.parentFile != null) rows.add(Row(dir, true))
        listing.folders.forEach { rows.add(Row(it, false)) }
        listing.files.forEach { rows.add(Row(it, false)) }
        rowAdapter.submit(rows)
        if (resetScroll) list.scrollToPosition(0)
    }
}
