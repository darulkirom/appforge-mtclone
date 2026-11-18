package com.test.mtclone.ui.main

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.test.mtclone.R
import com.test.mtclone.core.file.FileOps
import com.test.mtclone.core.prefs.AppPrefs
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

/** Satu baris daftar. [isParent] = baris ".." (naik ke folder induk dari [file]). */
class Row(val file: File, val isParent: Boolean)

class FileRowAdapter(
    private val ctx: Context,
    private val prefs: AppPrefs,
    private val onClick: (Row) -> Unit
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

    /** Mengganti isi daftar sekaligus membaca ulang pengaturan tampilan (ukuran, baris, format tanggal). */
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ctx.rippleDrawable()
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
        val f = r.file

        holder.row.setPadding(ctx.dp(10), ctx.dp(padV), ctx.dp(10), ctx.dp(padV))
        holder.badge.layoutParams = LinearLayout.LayoutParams(ctx.dp(badgeDp), ctx.dp(badgeDp)).apply {
            rightMargin = ctx.dp(10)
        }
        holder.badge.bindKind(
            when {
                r.isParent || f.isDirectory -> IconKind.FOLDER
                FileOps.isApk(f) -> IconKind.APK
                else -> IconKind.FILE
            }
        )

        holder.name.textSize = nameSp
        if (lines == 1) {
            holder.name.setSingleLine(true)
            holder.name.ellipsize = TextUtils.TruncateAt.MIDDLE
        } else {
            holder.name.setSingleLine(false)
            holder.name.maxLines = lines
            holder.name.ellipsize = TextUtils.TruncateAt.END
        }
        holder.name.text = if (r.isParent) ".." else f.name

        holder.meta.textSize = metaSp
        if (r.isParent) {
            holder.meta.visibility = View.GONE
        } else {
            val date = dateFmt.format(Date(f.lastModified()))
            holder.meta.text = if (f.isDirectory) date else "$date  ${FileOps.formatSize(f.length())}"
            holder.meta.visibility = View.VISIBLE
        }

        holder.row.setOnClickListener { onClick(r) }
    }

    override fun getItemCount() = rows.size
}
