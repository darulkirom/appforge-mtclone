package com.test.mtclone.ui.main

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.test.mtclone.R
import com.test.mtclone.core.apk.ZipNode
import com.test.mtclone.core.file.FileOps
import com.test.mtclone.core.prefs.AppPrefs
import com.test.mtclone.ui.common.ApkIconLoader
import com.test.mtclone.ui.common.IconKind
import com.test.mtclone.ui.common.bindKind
import com.test.mtclone.ui.common.color
import com.test.mtclone.ui.common.dp
import com.test.mtclone.ui.common.newIconBadge
import com.test.mtclone.ui.common.rippleDrawable
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Satu baris daftar. Salah satu dari: baris ".." ([isParent]), file/folder di disk ([file]),
 * atau entry di dalam APK ([node]).
 */
class Row(val file: File?, val node: ZipNode?, val isParent: Boolean) {
    val name: String
        get() = when {
            isParent -> ".."
            node != null -> node.name
            else -> file?.name ?: ""
        }

    val isDir: Boolean
        get() = isParent || (node?.isDir ?: (file?.isDirectory ?: false))
}

class FileRowAdapter(
    private val ctx: Context,
    private val prefs: AppPrefs,
    private val isSelected: (Row) -> Boolean,
    private val onClick: (Row) -> Unit,
    private val onLongClick: (Row) -> Unit,
    private val onIconClick: (Row) -> Unit
) : RecyclerView.Adapter<FileRowAdapter.VH>() {

    class VH(val row: LinearLayout, val badge: ImageView, val name: TextView, val meta: TextView) :
        RecyclerView.ViewHolder(row)

    private var rows: List<Row> = emptyList()
    private var dateFmt = SimpleDateFormat(AppPrefs.DEFAULT_DATE_FORMAT, Locale.getDefault())
    private var nameSp = 13f
    private var metaSp = 10f
    private var badgeDp = 34
    private var padV = 6
    private var lines = 2

    /** Mengganti isi daftar sekaligus membaca ulang pengaturan tampilan. */
    fun submit(newRows: List<Row>) {
        rows = newRows
        dateFmt = SimpleDateFormat(prefs.dateFormat, Locale.getDefault())
        when (prefs.listSize) {
            AppPrefs.SIZE_LARGE -> { nameSp = 16f; metaSp = 12f; badgeDp = 44; padV = 10 }
            AppPrefs.SIZE_MEDIUM -> { nameSp = 14.5f; metaSp = 11f; badgeDp = 38; padV = 8 }
            else -> { nameSp = 13f; metaSp = 10f; badgeDp = 34; padV = 6 }
        }
        lines = prefs.nameLines.coerceIn(1, 3)
        notifyDataSetChanged()
    }

    /** Gambar ulang tanpa memuat ulang isi folder (mis. setelah seleksi berubah). */
    fun redraw() {
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // Wajib: tanpa ini RecyclerView memberi lebar WRAP_CONTENT -> area sentuh hanya selebar teks.
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            minimumHeight = ctx.dp(44)
        }
        val badge = ctx.newIconBadge(badgeDp)
        val column = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val name = TextView(ctx).apply { setTextColor(ctx.color(R.color.text_primary)) }
        val meta = TextView(ctx).apply { setTextColor(ctx.color(R.color.text_secondary)) }
        column.addView(name)
        column.addView(meta)
        row.addView(badge)
        row.addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return VH(row, badge, name, meta)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = rows[position]

        holder.row.background =
            if (isSelected(r)) ColorDrawable(ctx.color(R.color.row_selected)) else ctx.rippleDrawable()
        holder.row.setPadding(ctx.dp(10), ctx.dp(padV), ctx.dp(10), ctx.dp(padV))

        val pad = ctx.dp((badgeDp * 0.2f).toInt())
        holder.badge.setPadding(pad, pad, pad, pad)
        holder.badge.layoutParams = LinearLayout.LayoutParams(ctx.dp(badgeDp), ctx.dp(badgeDp)).apply {
            rightMargin = ctx.dp(10)
        }
        holder.badge.tag = null
        val kind = if (r.isDir) IconKind.FOLDER else IconKind.forName(r.name)
        holder.badge.bindKind(kind)
        val f = r.file
        if (kind == IconKind.APK && f != null) ApkIconLoader.load(ctx, f, holder.badge)

        holder.name.textSize = nameSp
        if (lines == 1) {
            holder.name.setSingleLine(true)
            holder.name.ellipsize = TextUtils.TruncateAt.MIDDLE
        } else {
            holder.name.setSingleLine(false)
            holder.name.maxLines = lines
            holder.name.ellipsize = TextUtils.TruncateAt.END
        }
        holder.name.text = r.name

        holder.meta.textSize = metaSp
        val node = r.node
        when {
            r.isParent -> holder.meta.visibility = View.GONE
            node != null -> {
                holder.meta.text =
                    if (node.isDir) ctx.getString(R.string.apk_n_items, node.children.size) else FileOps.formatSize(node.size)
                holder.meta.visibility = View.VISIBLE
            }
            f != null -> {
                val date = dateFmt.format(Date(f.lastModified()))
                holder.meta.text = if (f.isDirectory) date else "$date  ${FileOps.formatSize(f.length())}"
                holder.meta.visibility = View.VISIBLE
            }
            else -> holder.meta.visibility = View.GONE
        }

        holder.row.setOnClickListener { onClick(r) }
        holder.row.setOnLongClickListener {
            if (!r.isParent) onLongClick(r)
            true
        }
        holder.badge.setOnClickListener { if (r.isParent) onClick(r) else onIconClick(r) }
    }

    override fun getItemCount() = rows.size
}
