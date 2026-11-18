package com.test.mtclone.ui.main

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import com.test.mtclone.R
import com.test.mtclone.core.file.FileOps
import com.test.mtclone.core.prefs.AppPrefs
import com.test.mtclone.ui.common.AppBar
import com.test.mtclone.ui.common.color
import com.test.mtclone.ui.common.dp
import com.test.mtclone.ui.common.iconButton
import com.test.mtclone.ui.settings.PreferencesActivity
import com.test.mtclone.ui.viewer.LocalFileViewer
import java.io.File

/**
 * File manager dua jendela ala MT Manager: jendela kiri & kanan menjelajah folder
 * masing-masing (independen). Jendela yang terakhir disentuh jadi jendela aktif --
 * tombol bawah (mundur / maju / buat baru / tukar / naik) bekerja pada jendela aktif.
 */
class MainActivity : AppCompatActivity() {

    private val prefs by lazy { AppPrefs(this) }
    private val dialogs by lazy { MainDialogs(this, prefs, host) }
    private val drawer by lazy { StorageDrawer(this, prefs) { dir -> navigateActive(dir) } }

    private lateinit var bar: AppBar
    private lateinit var panes: Array<PaneView>
    private lateinit var btnBack: ImageView
    private lateinit var btnForward: ImageView
    private var active = 0

    private val paneCallbacks = object : PaneView.Callbacks {
        override fun onActivate(index: Int) = setActive(index)

        override fun onOpen(index: Int, file: File) {
            setActive(index)
            openEntry(file)
        }

        override fun onParent(index: Int) {
            setActive(index)
            panes[index].state.goUp()
            refreshAll(resetScroll = true)
        }
    }

    private val host = object : MainDialogs.Host {
        override fun activeIndex() = active
        override fun activeDir(): File = panes[active].state.dir
        override fun activeFilter(): String = panes[active].state.filter
        override fun setActiveFilter(filter: String) {
            panes[active].state.filter = filter
            refreshAll(resetScroll = true)
        }
        override fun navigate(dir: File) = navigateActive(dir)
        override fun refreshAll() = this@MainActivity.refreshAll()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        panes = arrayOf(
            PaneView(this, 0, prefs, startDir(0), paneCallbacks),
            PaneView(this, 1, prefs, startDir(1), paneCallbacks)
        )
        setContentView(buildUi())
        setActive(0)
        ensurePermission()
        refreshAll()
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    override fun onPause() {
        super.onPause()
        panes.forEach { prefs.setLastPath(it.index, it.state.dir.absolutePath) }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawer.isOpen) {
            drawer.close()
            return
        }
        val pane = panes[active]
        val storageRoot = Environment.getExternalStorageDirectory().absolutePath
        if (pane.state.dir.absolutePath != storageRoot && pane.state.goUp()) {
            refreshAll(resetScroll = true)
        } else {
            super.onBackPressed()
        }
    }

    // ------------------------------------------------------------ folder awal

    private fun startDir(index: Int): File {
        val candidate = if (prefs.startupMode(index) == AppPrefs.STARTUP_LAST) prefs.lastPath(index) else prefs.homepage
        val f = candidate?.let { File(it) }
        return if (f != null && f.isDirectory) f else Environment.getExternalStorageDirectory()
    }

    // ------------------------------------------------------------------- izin

    private fun hasPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun ensurePermission() {
        if (hasPermission()) return
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
                )
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
            Toast.makeText(this, R.string.permission_hint, Toast.LENGTH_LONG).show()
        } else {
            requestPermissions(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                1
            )
        }
    }

    // ---------------------------------------------------------------- navigasi

    private fun setActive(index: Int) {
        active = index
        panes.forEachIndexed { i, p -> p.setActive(i == index) }
        updateHeader()
    }

    private fun navigateActive(dir: File) {
        panes[active].state.navigate(dir)
        refreshAll(resetScroll = true)
    }

    private fun openEntry(f: File) {
        when {
            f.isDirectory -> navigateActive(f)
            FileOps.isApk(f) -> dialogs.showApk(f)
            else -> startActivity(
                Intent(this, LocalFileViewer::class.java).putExtra(LocalFileViewer.EXTRA_PATH, f.absolutePath)
            )
        }
    }

    private fun swapPanes() {
        val a = panes[0]
        val b = panes[1]
        val t = a.state
        a.state = b.state
        b.state = t
        refreshAll(resetScroll = true)
    }

    private fun refreshAll(resetScroll: Boolean = false) {
        val ok = hasPermission()
        panes.forEach { it.refresh(ok, resetScroll) }
        updateHeader()
    }

    private fun updateHeader() {
        if (!::bar.isInitialized) return
        val pane = panes[active]
        val dir = pane.state.dir
        bar.title.text = dir.absolutePath
        if (hasPermission()) {
            val total = dir.totalSpace
            val used = total - dir.freeSpace
            bar.setSubtitle(
                getString(
                    R.string.stat_format,
                    pane.folderCount, pane.fileCount, FileOps.formatSize(used), FileOps.formatSize(total)
                )
            )
        } else {
            bar.setSubtitle(getString(R.string.waiting_permission))
        }
        btnBack.alpha = if (pane.state.canBack) 1f else 0.35f
        btnForward.alpha = if (pane.state.canForward) 1f else 0.35f
    }

    // -------------------------------------------------------------- menu (⋮)

    private fun showOverflow(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_main, popup.menu)
        popup.menu.findItem(R.id.action_toggle_hidden)?.isChecked = prefs.showHidden
        popup.setOnMenuItemClickListener { item -> onMenuAction(item) }
        popup.show()
    }

    private fun onMenuAction(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_refresh -> refreshAll()
            R.id.action_search -> dialogs.showSearch()
            R.id.action_filter -> dialogs.showFilter()
            R.id.action_sort -> dialogs.showSort()
            R.id.action_toggle_hidden -> {
                prefs.showHidden = !prefs.showHidden
                refreshAll()
            }
            R.id.action_bookmark -> {
                prefs.addBookmark(panes[active].state.dir.absolutePath)
                Toast.makeText(this, R.string.bookmark_added, Toast.LENGTH_SHORT).show()
            }
            R.id.action_homepage -> {
                prefs.homepage = panes[active].state.dir.absolutePath
                Toast.makeText(this, R.string.homepage_set, Toast.LENGTH_SHORT).show()
            }
            R.id.action_swap -> swapPanes()
            R.id.action_prefs -> startActivity(Intent(this, PreferencesActivity::class.java))
            R.id.action_exit -> finishAffinity()
            else -> return false
        }
        return true
    }

    // ---------------------------------------------------------------------- UI

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.mt_toolbar))
        }

        bar = AppBar(this, R.drawable.ic_menu, R.string.cd_menu) { drawer.open() }
        bar.title.setOnClickListener { dialogs.showGoTo() }
        bar.subtitle.setOnClickListener { dialogs.showGoTo() }
        bar.addAction(R.drawable.ic_more_vert, R.string.cd_more) { showOverflow(it) }

        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        body.addView(panes[0], LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        body.addView(
            View(this).apply { setBackgroundColor(color(R.color.divider)) },
            LinearLayout.LayoutParams(dp(1), ViewGroup.LayoutParams.MATCH_PARENT)
        )
        body.addView(panes[1], LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        btnBack = navButton(R.drawable.ic_arrow_back, R.string.cd_back) {
            if (panes[active].state.goBack()) refreshAll(resetScroll = true)
        }
        btnForward = navButton(R.drawable.ic_arrow_forward, R.string.cd_forward) {
            if (panes[active].state.goForward()) refreshAll(resetScroll = true)
        }
        val btnAdd = navButton(R.drawable.ic_add, R.string.cd_create) { dialogs.showCreate() }
        val btnSwap = navButton(R.drawable.ic_swap, R.string.cd_swap) { swapPanes() }
        val btnUp = navButton(R.drawable.ic_arrow_up, R.string.cd_up) {
            if (panes[active].state.goUp()) refreshAll(resetScroll = true)
        }

        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(color(R.color.pane_inactive))
        }
        for (b in listOf(btnBack, btnForward, btnAdd, btnSwap, btnUp)) {
            bottomBar.addView(b, LinearLayout.LayoutParams(0, dp(48), 1f))
        }

        root.addView(bar.view)
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomBar)

        val frame = FrameLayout(this)
        frame.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        drawer.attach(frame)
        return frame
    }

    private fun navButton(icon: Int, desc: Int, onClick: () -> Unit): ImageView =
        iconButton(icon, desc) { onClick() }
}
