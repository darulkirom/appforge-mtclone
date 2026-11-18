package com.test.mtclone.ui.common

import android.content.Context
import android.widget.ImageView
import com.test.mtclone.R

enum class IconKind { FOLDER, FILE, APK }

/** Kotak ikon bersudut membulat (ala MT Manager) berukuran [sizeDp]. */
fun Context.newIconBadge(sizeDp: Int): ImageView = ImageView(this).apply {
    scaleType = ImageView.ScaleType.FIT_CENTER
    val p = dp((sizeDp * 0.2f).toInt())
    setPadding(p, p, p, p)
}

fun ImageView.bindKind(kind: IconKind) {
    when (kind) {
        IconKind.FOLDER -> { setBackgroundResource(R.drawable.bg_icon_folder); setImageResource(R.drawable.ic_folder) }
        IconKind.FILE -> { setBackgroundResource(R.drawable.bg_icon_file); setImageResource(R.drawable.ic_file) }
        IconKind.APK -> { setBackgroundResource(R.drawable.bg_icon_apk); setImageResource(R.drawable.ic_apk) }
    }
}
