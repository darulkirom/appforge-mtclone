package com.test.mtclone.ui.common

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.text.method.ScrollingMovementMethod
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.test.mtclone.R

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.color(@ColorRes id: Int): Int = ContextCompat.getColor(this, id)

/** Latar ripple standar tema (untuk baris / tombol yang bisa diketuk). */
fun Context.rippleDrawable(): Drawable? {
    val tv = TypedValue()
    return if (theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
        ContextCompat.getDrawable(this, tv.resourceId)
    } else null
}

fun Context.iconButton(@DrawableRes icon: Int, @StringRes desc: Int, onClick: (View) -> Unit): ImageView =
    ImageView(this).apply {
        setImageResource(icon)
        contentDescription = getString(desc)
        scaleType = ImageView.ScaleType.CENTER
        background = rippleDrawable()
        setOnClickListener { v -> onClick(v) }
    }

/** App bar gelap ala MT Manager: ikon navigasi, judul + subjudul, lalu aksi di kanan. */
class AppBar(
    private val ctx: Context,
    @DrawableRes navIcon: Int,
    @StringRes navDesc: Int,
    onNav: () -> Unit
) {
    val title = TextView(ctx).apply {
        textSize = 16f
        setTextColor(ctx.color(R.color.text_primary))
        setTypeface(null, Typeface.BOLD)
        setSingleLine(true)
        ellipsize = TextUtils.TruncateAt.MIDDLE
    }

    val subtitle = TextView(ctx).apply {
        textSize = 10.5f
        setTextColor(ctx.color(R.color.text_secondary))
        setSingleLine(true)
        ellipsize = TextUtils.TruncateAt.END
        visibility = View.GONE
    }

    private val actions = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    val view = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(ctx.color(R.color.mt_toolbar))
        minimumHeight = ctx.dp(56)
    }

    init {
        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(subtitle)
        }
        view.addView(ctx.iconButton(navIcon, navDesc) { onNav() }, LinearLayout.LayoutParams(ctx.dp(48), ctx.dp(48)))
        view.addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        view.addView(actions)
    }

    fun setSubtitle(text: CharSequence?) {
        subtitle.text = text ?: ""
        subtitle.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    fun addAction(@DrawableRes icon: Int, @StringRes desc: Int, onClick: (View) -> Unit) {
        actions.addView(ctx.iconButton(icon, desc, onClick), LinearLayout.LayoutParams(ctx.dp(48), ctx.dp(48)))
    }

    fun addTextAction(@StringRes text: Int, onClick: () -> Unit) {
        val btn = TextView(ctx).apply {
            setText(text)
            textSize = 14f
            setTextColor(ctx.color(R.color.mt_accent))
            gravity = Gravity.CENTER
            setPadding(ctx.dp(14), 0, ctx.dp(14), 0)
            background = ctx.rippleDrawable()
            setOnClickListener { onClick() }
        }
        actions.addView(btn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ctx.dp(48)))
    }
}

/** Area teks monospace (kode / smali / hex). Mengembalikan scroll view + EditText di dalamnya. */
fun Context.codeViewer(content: String, editable: Boolean): Pair<ScrollView, EditText> {
    val edit = EditText(this).apply {
        setText(content)
        isEnabled = editable
        isFocusable = editable
        isFocusableInTouchMode = editable
        typeface = Typeface.MONOSPACE
        textSize = 12.5f
        setTextColor(color(R.color.text_primary))
        setBackgroundColor(Color.TRANSPARENT)
        setPadding(dp(14), dp(14), dp(14), dp(14))
        gravity = Gravity.TOP or Gravity.START
        movementMethod = ScrollingMovementMethod()
    }
    val scroll = ScrollView(this)
    scroll.addView(
        edit,
        FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    )
    return scroll to edit
}

/** Pembungkus padding standar untuk view di dalam dialog. */
fun Context.dialogPad(view: View): View = LinearLayout(this).apply {
    setPadding(dp(24), dp(12), dp(24), 0)
    addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
}

/** Baris "label  nilai" untuk dialog info / properti. */
fun Context.kvRow(label: CharSequence, value: CharSequence): LinearLayout {
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(6), 0, dp(6))
    }
    row.addView(TextView(this).apply {
        text = label
        textSize = 13f
        setTextColor(color(R.color.text_primary))
    }, LinearLayout.LayoutParams(dp(84), ViewGroup.LayoutParams.WRAP_CONTENT))
    row.addView(TextView(this).apply {
        text = value
        textSize = 12f
        setTextColor(color(R.color.text_secondary))
    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    return row
}
