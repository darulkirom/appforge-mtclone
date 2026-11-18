package com.test.mtclone.ui.settings

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.test.mtclone.R
import com.test.mtclone.core.prefs.AppPrefs
import com.test.mtclone.ui.common.AppBar
import com.test.mtclone.ui.common.color
import com.test.mtclone.ui.common.dp
import com.test.mtclone.ui.common.rippleDrawable

/** Layar Preferensi (mengikuti bagian Awal / Penampilan / Umum di MT Manager). */
class PreferencesActivity : AppCompatActivity() {

    private lateinit var prefs: AppPrefs
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPrefs(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.pane_inactive))
        }
        val bar = AppBar(this, R.drawable.ic_arrow_back, R.string.cd_back) { finish() }
        bar.title.setText(R.string.prefs_title)
        container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this)
        scroll.addView(
            container,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        root.addView(bar.view)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        render()
    }

    private fun render() {
        container.removeAllViews()

        section(R.string.prefs_section_start)
        choiceRow(
            R.string.pref_startup_left,
            listOf(R.string.startup_home, R.string.startup_last),
            prefs.startupMode(0)
        ) { prefs.setStartupMode(0, it) }
        choiceRow(
            R.string.pref_startup_right,
            listOf(R.string.startup_home, R.string.startup_last),
            prefs.startupMode(1)
        ) { prefs.setStartupMode(1, it) }

        section(R.string.prefs_section_look)
        choiceRow(
            R.string.pref_list_size,
            listOf(R.string.size_small, R.string.size_medium, R.string.size_large),
            prefs.listSize
        ) { prefs.listSize = it }
        textChoiceRow(
            R.string.pref_name_lines,
            listOf("1", "2", "3"),
            (prefs.nameLines - 1).coerceIn(0, 2)
        ) { prefs.nameLines = it + 1 }
        textChoiceRow(
            R.string.pref_date_format,
            AppPrefs.DATE_FORMATS,
            AppPrefs.DATE_FORMATS.indexOf(prefs.dateFormat).coerceAtLeast(0)
        ) { prefs.dateFormat = AppPrefs.DATE_FORMATS[it] }
        switchRow(R.string.pref_show_hidden, R.string.pref_show_hidden_sum, prefs.showHidden) { prefs.showHidden = it }

        section(R.string.prefs_section_general)
        switchRow(R.string.pref_backup, R.string.pref_backup_sum, prefs.backupOnSave) { prefs.backupOnSave = it }
    }

    // ---------------------------------------------------------------- baris

    private fun section(@StringRes title: Int) {
        container.addView(TextView(this).apply {
            setText(title)
            textSize = 12.5f
            setTextColor(color(R.color.mt_accent))
            setPadding(dp(16), dp(20), dp(16), dp(6))
        })
    }

    private fun rowBase(@StringRes title: Int, summary: CharSequence): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = rippleDrawable()
        }
        row.addView(TextView(this).apply {
            setText(title)
            textSize = 15f
            setTextColor(color(R.color.text_primary))
        })
        row.addView(TextView(this).apply {
            text = summary
            textSize = 11.5f
            setTextColor(color(R.color.text_secondary))
        })
        container.addView(row)
        return row
    }

    /** Pilihan dengan label dari resources. */
    private fun choiceRow(@StringRes title: Int, labels: List<Int>, selected: Int, onPick: (Int) -> Unit) {
        textChoiceRow(title, labels.map { getString(it) }, selected, onPick)
    }

    /** Pilihan dengan label teks langsung (angka / pola tanggal). */
    private fun textChoiceRow(@StringRes title: Int, labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
        val row = rowBase(title, labels[selected.coerceIn(0, labels.lastIndex)])
        row.setOnClickListener {
            val items: List<CharSequence> = labels
            AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(items.toTypedArray(), selected) { dialog, which ->
                    onPick(which)
                    dialog.dismiss()
                    render()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun switchRow(@StringRes title: Int, @StringRes summary: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = rippleDrawable()
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            setText(title)
            textSize = 15f
            setTextColor(color(R.color.text_primary))
        })
        texts.addView(TextView(this).apply {
            setText(summary)
            textSize = 11.5f
            setTextColor(color(R.color.text_secondary))
        })
        val sw = SwitchCompat(this).apply { isChecked = checked }
        sw.setOnCheckedChangeListener { _, isOn -> onChange(isOn) }
        line.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        line.addView(sw)
        line.setOnClickListener { sw.toggle() }
        container.addView(line)
    }
}
