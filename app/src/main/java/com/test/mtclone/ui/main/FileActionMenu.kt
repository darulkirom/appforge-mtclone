package com.test.mtclone.ui.main

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.test.mtclone.R
import com.test.mtclone.core.file.FileActions
import com.test.mtclone.core.file.FileOps
import com.test.mtclone.core.prefs.AppPrefs
import com.test.mtclone.ui.common.color
import com.test.mtclone.ui.common.dialogPad
import com.test.mtclone.ui.common.dp
import com.test.mtclone.ui.common.kvRow
import com.test.mtclone.ui.common.rippleDrawable
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Menu tekan-lama pada file/folder (ala MT Manager) beserta semua operasinya. */
class FileActionMenu(
    private val activity: AppCompatActivity,
    private val prefs: AppPrefs,
    private val host: MainDialogs.Host
) {

    private enum class Op(@DrawableRes val icon: Int, @StringRes val label: Int) {
        COPY(R.drawable.ic_copy, R.string.op_copy),
        MOVE(R.drawable.ic_move, R.string.op_move),
        DELETE(R.drawable.ic_delete, R.string.op_delete),
        RENAME(R.drawable.ic_edit, R.string.op_rename),
        TOOLS(R.drawable.ic_build, R.string.op_tools),
        COMPRESS(R.drawable.ic_compress, R.string.op_compress),
        PROPERTIES(R.drawable.ic_info, R.string.op_properties),
        SHARE(R.drawable.ic_share, R.string.op_share),
        OPEN_WITH(R.drawable.ic_check, R.string.op_open_with),
        BOOKMARK(R.drawable.ic_bookmark_add, R.string.op_bookmark)
    }

    private fun toast(res: Int) = Toast.makeText(activity, res, Toast.LENGTH_SHORT).show()
    private fun toast(text: String) = Toast.makeText(activity, text, Toast.LENGTH_LONG).show()

    // ------------------------------------------------------------------- menu

    fun show() {
        val files = host.selection()
        if (files.isEmpty()) return

        val grid = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(6), activity.dp(8), activity.dp(6), activity.dp(8))
        }
        var dialog: AlertDialog? = null
        val ops = Op.values()
        var i = 0
        while (i < ops.size) {
            val line = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            for (j in i until minOf(i + 2, ops.size)) {
                val op = ops[j]
                line.addView(
                    cell(op) {
                        dialog?.dismiss()
                        run(op, files)
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                )
            }
            grid.addView(line)
            i += 2
        }
        dialog = AlertDialog.Builder(activity).setView(grid).show()
    }

    private fun cell(op: Op, onClick: () -> Unit): View {
        val c = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(activity.dp(12), activity.dp(13), activity.dp(8), activity.dp(13))
            background = activity.rippleDrawable()
            setOnClickListener { onClick() }
        }
        c.addView(
            ImageView(activity).apply { setImageResource(op.icon) },
            LinearLayout.LayoutParams(activity.dp(22), activity.dp(22)).apply { rightMargin = activity.dp(12) }
        )
        c.addView(TextView(activity).apply {
            setText(op.label)
            textSize = 14f
            setTextColor(activity.color(R.color.text_primary))
            setSingleLine(true)
        })
        return c
    }

    private fun run(op: Op, files: List<File>) {
        when (op) {
            Op.COPY -> transfer(files, move = false)
            Op.MOVE -> transfer(files, move = true)
            Op.DELETE -> confirmDelete(files)
            Op.RENAME -> rename(files)
            Op.TOOLS -> tools(files)
            Op.COMPRESS -> compress(files)
            Op.PROPERTIES -> properties(files)
            Op.SHARE -> share(files)
            Op.OPEN_WITH -> openWith(files)
            Op.BOOKMARK -> bookmark(files)
        }
    }

    /** Jalankan [work] di background dengan dialog "mohon tunggu"; work mengembalikan pesan error atau null. */
    private fun runTask(@StringRes message: Int, work: () -> String?) {
        val wait = AlertDialog.Builder(activity).setMessage(message).setCancelable(false).show()
        Thread {
            val error: String? = try { work() } catch (e: Exception) { e.message ?: e.javaClass.simpleName }
            activity.runOnUiThread {
                wait.dismiss()
                host.clearSelection()
                host.refreshAll()
                if (error != null) toast(activity.getString(R.string.op_failed, error)) else toast(R.string.op_done)
            }
        }.start()
    }

    // ---------------------------------------------------------- salin / pindah

    private fun transfer(files: List<File>, move: Boolean) {
        val target = host.otherPaneDir()
        if (target == null) {
            toast(R.string.no_other_pane)
            return
        }
        if (files.any { it.isDirectory && FileActions.isInside(target, it) }) {
            toast(R.string.into_self)
            return
        }
        val msg = activity.getString(
            if (move) R.string.confirm_move else R.string.confirm_copy, files.size, target.absolutePath
        )
        AlertDialog.Builder(activity)
            .setMessage(msg)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                runTask(if (move) R.string.working_move else R.string.working_copy) {
                    var failed = 0
                    for (f in files) {
                        try {
                            if (move) FileActions.move(f, target) else FileActions.copy(f, target)
                        } catch (e: Exception) {
                            failed++
                        }
                    }
                    if (failed > 0) activity.getString(R.string.op_failed_n, failed) else null
                }
            }
            .show()
    }

    // ------------------------------------------------------------------- hapus

    private fun confirmDelete(files: List<File>) {
        AlertDialog.Builder(activity)
            .setMessage(activity.getString(R.string.confirm_delete, files.size))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                runTask(R.string.working_delete) {
                    val failed = files.count { !FileActions.delete(it) }
                    if (failed > 0) activity.getString(R.string.op_failed_n, failed) else null
                }
            }
            .show()
    }

    // -------------------------------------------------------------- ganti nama

    private fun rename(files: List<File>) {
        if (files.size != 1) {
            toast(R.string.need_single)
            return
        }
        val f = files[0]
        val input = EditText(activity).apply {
            setText(f.name)
            setSingleLine(true)
            setSelection(0, f.nameWithoutExtension.length)
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.op_rename)
            .setView(activity.dialogPad(input))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty() && n != f.name) {
                    if (FileActions.rename(f, n)) {
                        host.clearSelection()
                        host.refreshAll()
                    } else {
                        toast(R.string.rename_failed)
                    }
                }
            }
            .show()
    }

    // ----------------------------------------------------------------- kompres

    private fun compress(files: List<File>) {
        val input = EditText(activity).apply {
            setHint(R.string.compress_hint)
            setSingleLine(true)
            setText(if (files.size == 1) files[0].nameWithoutExtension + ".zip" else "arsip.zip")
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.compress_title)
            .setView(activity.dialogPad(input))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                var name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    if (!name.endsWith(".zip", true)) name += ".zip"
                    val out = FileActions.uniqueTarget(host.activeDir(), name)
                    runTask(R.string.working_compress) {
                        FileActions.zip(files, out)
                        null
                    }
                }
            }
            .show()
    }

    // --------------------------------------------------------------- peralatan

    private fun tools(files: List<File>) {
        val labels: List<CharSequence> = listOf(
            activity.getString(R.string.tool_split),
            activity.getString(R.string.tool_merge),
            activity.getString(R.string.tool_export)
        )
        AlertDialog.Builder(activity)
            .setTitle(R.string.tools_title)
            .setItems(labels.toTypedArray()) { _, which ->
                when (which) {
                    0 -> split(files)
                    1 -> merge(files)
                    else -> exportList(files)
                }
            }
            .show()
    }

    private fun split(files: List<File>) {
        if (files.size != 1 || !files[0].isFile) {
            toast(R.string.need_file)
            return
        }
        val f = files[0]
        val input = EditText(activity).apply {
            setHint(R.string.split_hint)
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("10")
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.tool_split)
            .setView(activity.dialogPad(input))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                val mb = input.text.toString().trim().toLongOrNull()
                if (mb != null && mb >= 1) {
                    runTask(R.string.working_tool) {
                        FileActions.split(f, mb * 1024 * 1024)
                        null
                    }
                }
            }
            .show()
    }

    private fun merge(files: List<File>) {
        if (files.size != 1 || !files[0].isFile || !files[0].name.endsWith(".001")) {
            toast(R.string.merge_need)
            return
        }
        val first = files[0]
        runTask(R.string.working_tool) {
            FileActions.merge(first)
            null
        }
    }

    private fun exportList(files: List<File>) {
        val out = FileActions.uniqueTarget(host.activeDir(), "daftar_file.txt")
        runTask(R.string.working_tool) {
            FileActions.exportList(files, out)
            null
        }
    }

    // ---------------------------------------------------------------- properti

    private fun properties(files: List<File>) {
        toast(R.string.calculating)
        Thread {
            val stats = FileActions.Stats()
            files.forEach { FileActions.stats(it, stats) }
            activity.runOnUiThread { showProperties(files, stats) }
        }.start()
    }

    private fun showProperties(files: List<File>, stats: FileActions.Stats) {
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(22), activity.dp(14), activity.dp(22), activity.dp(4))
        }
        val sizeText = activity.getString(R.string.size_full, FileOps.formatSize(stats.bytes), stats.bytes)
        if (files.size == 1) {
            val f = files[0]
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            fun yn(b: Boolean) = activity.getString(if (b) R.string.yes else R.string.no)
            body.addView(activity.kvRow(activity.getString(R.string.props_path), f.absolutePath))
            body.addView(activity.kvRow(activity.getString(R.string.props_type), activity.getString(if (f.isDirectory) R.string.type_folder else R.string.type_file)))
            body.addView(activity.kvRow(activity.getString(R.string.props_size), sizeText))
            if (f.isDirectory) {
                // folder itu sendiri tidak dihitung sebagai isi
                body.addView(activity.kvRow(activity.getString(R.string.props_contents), activity.getString(R.string.contents_fmt, stats.folders - 1, stats.files)))
            }
            body.addView(activity.kvRow(activity.getString(R.string.props_modified), fmt.format(Date(f.lastModified()))))
            body.addView(activity.kvRow(activity.getString(R.string.props_perm), activity.getString(R.string.perm_fmt, yn(f.canRead()), yn(f.canWrite()), yn(f.canExecute()))))
        } else {
            body.addView(activity.kvRow(activity.getString(R.string.props_type), activity.getString(R.string.type_multi, files.size)))
            body.addView(activity.kvRow(activity.getString(R.string.props_size), sizeText))
            body.addView(activity.kvRow(activity.getString(R.string.props_contents), activity.getString(R.string.contents_fmt, stats.folders, stats.files)))
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.props_title)
            .setView(body)
            .setNegativeButton(R.string.close, null)
            .show()
    }

    // ------------------------------------------------- bagikan / buka dengan

    private fun mimeOf(f: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"

    private fun uriOf(f: File): Uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", f)

    private fun share(files: List<File>) {
        if (files.any { it.isDirectory }) {
            toast(R.string.share_dirs)
            return
        }
        try {
            val uris = ArrayList<Uri>()
            files.forEach { uris.add(uriOf(it)) }
            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
            intent.type = if (files.size == 1) mimeOf(files[0]) else "*/*"
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.share_title)))
        } catch (e: Exception) {
            toast(activity.getString(R.string.op_failed, e.message ?: e.javaClass.simpleName))
        }
    }

    private fun openWith(files: List<File>) {
        if (files.size != 1 || files[0].isDirectory) {
            toast(R.string.need_file)
            return
        }
        val f = files[0]
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uriOf(f), mimeOf(f))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.open_title)))
        } catch (e: ActivityNotFoundException) {
            toast(R.string.open_none)
        } catch (e: Exception) {
            toast(activity.getString(R.string.op_failed, e.message ?: e.javaClass.simpleName))
        }
    }

    private fun bookmark(files: List<File>) {
        files.forEach { prefs.addBookmark(it.absolutePath) }
        toast(R.string.bookmark_added)
    }
}
