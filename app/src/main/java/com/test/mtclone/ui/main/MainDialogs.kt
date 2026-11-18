package com.test.mtclone.ui.main

import android.content.Intent
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.test.mtclone.R
import com.test.mtclone.core.file.FileOps
import com.test.mtclone.core.file.SortBy
import com.test.mtclone.core.file.SortSpec
import com.test.mtclone.core.prefs.AppPrefs
import com.test.mtclone.core.sign.ApkSigning
import com.test.mtclone.ui.apk.ApkOpenActivity
import com.test.mtclone.ui.common.color
import com.test.mtclone.ui.common.dp
import java.io.File

/** Dialog-dialog layar utama (buka dengan, buat baru, sortir, cari, saring, pintasan). */
class MainDialogs(
    private val activity: AppCompatActivity,
    private val prefs: AppPrefs,
    private val host: Host
) {

    /** Hal-hal yang harus disediakan layar utama (jendela aktif, refresh, navigasi). */
    interface Host {
        fun activeIndex(): Int
        fun activeDir(): File
        fun activeFilter(): String
        fun setActiveFilter(filter: String)
        fun navigate(dir: File)
        fun refreshAll()
    }

    private fun toast(res: Int) = Toast.makeText(activity, res, Toast.LENGTH_SHORT).show()
    private fun toast(text: String) = Toast.makeText(activity, text, Toast.LENGTH_LONG).show()

    private fun padded(view: View): View = LinearLayout(activity).apply {
        setPadding(activity.dp(24), activity.dp(12), activity.dp(24), 0)
        addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun windowName(index: Int): String =
        activity.getString(if (index == 0) R.string.window_left else R.string.window_right)

    // ------------------------------------------------------------ buka dengan

    private enum class OpenAction { INSTALL, VIEW, SIGN, CONVERT }

    fun showOpenWith(f: File) {
        val entries = listOf(
            R.string.open_install to OpenAction.INSTALL,
            R.string.open_view to OpenAction.VIEW,
            R.string.open_sign to OpenAction.SIGN,
            R.string.open_convert to OpenAction.CONVERT
        )
        val labels: List<CharSequence> = entries.map { activity.getString(it.first) }
        AlertDialog.Builder(activity)
            .setTitle(R.string.open_with_title)
            .setItems(labels.toTypedArray()) { _, which ->
                when (entries[which].second) {
                    OpenAction.INSTALL -> installApk(f)
                    OpenAction.VIEW -> activity.startActivity(
                        Intent(activity, ApkOpenActivity::class.java)
                            .putExtra(ApkOpenActivity.EXTRA_APK_PATH, f.absolutePath)
                    )
                    OpenAction.SIGN -> signApk(f)
                    OpenAction.CONVERT -> toast(R.string.not_implemented)
                }
            }
            .show()
    }

    private fun installApk(f: File) {
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", f)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
    }

    /** Tanda tangani APK dengan testkey AOSP -> <nama>_sign.apk di folder yang sama. */
    private fun signApk(f: File) {
        if (f.extension.equals("apks", true)) {
            toast(R.string.sign_apks_unsupported)
            return
        }
        val out = File(f.parentFile, ApkSigning.signedName(f))
        toast(R.string.signing_progress)
        val appContext = activity.applicationContext
        Thread {
            val error: String? = try {
                ApkSigning.sign(appContext, f, out)
                null
            } catch (e: Exception) {
                e.message ?: e.javaClass.simpleName
            }
            activity.runOnUiThread {
                if (error == null) {
                    toast(activity.getString(R.string.sign_done, out.name))
                    host.refreshAll()
                } else {
                    toast(activity.getString(R.string.sign_failed, error))
                }
            }
        }.start()
    }

    // -------------------------------------------------------------- buat baru

    fun showCreate() {
        val dir = host.activeDir()
        val input = EditText(activity).apply {
            setHint(R.string.create_hint)
            setSingleLine(true)
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.create_title)
            .setView(padded(input))
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.create_file) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    if (FileOps.createFile(dir, name)) host.refreshAll() else toast(R.string.create_file_failed)
                }
            }
            .setPositiveButton(R.string.create_folder) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    if (FileOps.createFolder(dir, name)) host.refreshAll() else toast(R.string.create_folder_failed)
                }
            }
            .show()
    }

    // ----------------------------------------------------------------- sortir

    fun showSort() {
        val idx = host.activeIndex()
        val dir = host.activeDir().absolutePath
        val cur = prefs.getSort(idx, dir)

        val options = listOf(
            SortBy.NAME to R.string.sort_name,
            SortBy.SIZE to R.string.sort_size,
            SortBy.DATE to R.string.sort_date,
            SortBy.TYPE to R.string.sort_type
        )
        val group = RadioGroup(activity).apply { orientation = RadioGroup.VERTICAL }
        for ((by, label) in options) {
            val rb = RadioButton(activity).apply {
                id = View.generateViewId()
                setText(label)
                setTextColor(activity.color(R.color.text_primary))
                tag = by
                isChecked = by == cur.by
            }
            group.addView(rb)
        }
        val onlyFolder = CheckBox(activity).apply {
            setText(R.string.sort_only_folder)
            setTextColor(activity.color(R.color.text_primary))
        }
        val reverse = CheckBox(activity).apply {
            setText(R.string.sort_reverse)
            setTextColor(activity.color(R.color.text_primary))
            isChecked = !cur.ascending
        }
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(group)
            addView(onlyFolder)
            addView(reverse)
        }

        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.sort_title, windowName(idx)))
            .setView(padded(body))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                val picked = group.findViewById<View>(group.checkedRadioButtonId)?.tag as? SortBy ?: cur.by
                prefs.setSort(idx, dir, SortSpec(picked, !reverse.isChecked), onlyFolder.isChecked)
                host.refreshAll()
            }
            .show()
    }

    // -------------------------------------------------------- saring & mencari

    fun showFilter() {
        val input = EditText(activity).apply {
            setHint(R.string.filter_hint)
            setSingleLine(true)
            setText(host.activeFilter())
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.menu_filter)
            .setView(padded(input))
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.filter_clear) { _, _ -> host.setActiveFilter("") }
            .setPositiveButton(R.string.ok) { _, _ -> host.setActiveFilter(input.text.toString().trim()) }
            .show()
    }

    fun showSearch() {
        val input = EditText(activity).apply {
            setHint(R.string.search_hint)
            setSingleLine(true)
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.menu_search)
            .setView(padded(input))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.search_action) { _, _ ->
                val q = input.text.toString().trim()
                if (q.isNotEmpty()) runSearch(q)
            }
            .show()
    }

    private fun runSearch(query: String) {
        val base = host.activeDir()
        toast(R.string.searching)
        Thread {
            val found: List<File> = try {
                base.walkTopDown()
                    .onEnter { it.canRead() }
                    .filter { it != base && it.name.contains(query, ignoreCase = true) }
                    .take(MAX_SEARCH_RESULTS)
                    .toList()
            } catch (e: Exception) {
                emptyList()
            }
            activity.runOnUiThread { showSearchResults(base, found) }
        }.start()
    }

    private fun showSearchResults(base: File, found: List<File>) {
        if (found.isEmpty()) {
            toast(R.string.search_empty)
            return
        }
        val labels: List<CharSequence> = found.map { it.relativeTo(base).path }
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.search_results, found.size))
            .setItems(labels.toTypedArray()) { _, which ->
                val f = found[which]
                host.navigate(if (f.isDirectory) f else (f.parentFile ?: base))
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    // ---------------------------------------------------------------- pintasan

    fun showShortcuts() {
        val root = Environment.getExternalStorageDirectory()
        val items = ArrayList<Pair<String, File>>()
        items.add(activity.getString(R.string.shortcut_internal) to root)
        prefs.homepage?.let { home ->
            val f = File(home)
            if (f.isDirectory) items.add(activity.getString(R.string.shortcut_home, home) to f)
        }
        for (name in listOf("Download", "DCIM", "Documents", "Pictures", "Music", "Movies")) {
            val f = File(root, name)
            if (f.isDirectory) items.add(name to f)
        }
        for (p in prefs.bookmarks()) items.add(("★ " + p) to File(p))

        val labels: List<CharSequence> = items.map { it.first }
        AlertDialog.Builder(activity)
            .setTitle(R.string.shortcuts_title)
            .setItems(labels.toTypedArray()) { _, which -> host.navigate(items[which].second) }
            .show()
    }

    companion object {
        private const val MAX_SEARCH_RESULTS = 300
    }
}
