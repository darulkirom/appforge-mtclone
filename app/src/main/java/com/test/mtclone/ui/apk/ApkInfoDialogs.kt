package com.test.mtclone.ui.apk

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.test.mtclone.R
import com.test.mtclone.core.apk.ApkInspector
import com.test.mtclone.core.apk.ApkReport
import com.test.mtclone.core.file.FileOps
import com.test.mtclone.ui.common.color
import com.test.mtclone.ui.common.dp
import com.test.mtclone.ui.common.rippleDrawable
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.security.auth.x500.X500Principal

/** Dialog info APK (nama paket, versi, SDK, ...) dan info tanda tangan, ala MT Manager. */
object ApkInfoDialogs {

    private class Kv(val row: LinearLayout, val value: TextView)

    private fun kv(ctx: Context, label: CharSequence, value: CharSequence, onClick: (() -> Unit)? = null): Kv {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, ctx.dp(6), 0, ctx.dp(6))
        }
        row.addView(TextView(ctx).apply {
            text = label
            textSize = 13f
            setTextColor(ctx.color(R.color.text_primary))
        }, LinearLayout.LayoutParams(ctx.dp(96), ViewGroup.LayoutParams.WRAP_CONTENT))
        val v = TextView(ctx).apply {
            text = value
            textSize = 12f
            setTextColor(ctx.color(R.color.text_secondary))
        }
        row.addView(v, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (onClick != null) {
            row.background = ctx.rippleDrawable()
            row.setOnClickListener { onClick() }
        }
        return Kv(row, v)
    }

    private fun body(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ctx.dp(22), ctx.dp(18), ctx.dp(22), ctx.dp(4))
    }

    private fun sdkText(ctx: Context, api: Int): String {
        val name = ApkInspector.androidName(api)
        return if (name != null) ctx.getString(R.string.sdk_format, name, api) else ctx.getString(R.string.sdk_unknown, api)
    }

    private fun dateText(ms: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ms))

    // ------------------------------------------------------------ info APK

    fun showInfo(
        activity: AppCompatActivity,
        report: ApkReport,
        onFunctions: () -> Unit,
        onView: () -> Unit,
        onInstall: () -> Unit
    ) {
        val i = report.info
        val b = body(activity)

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, activity.dp(8))
        }
        header.addView(
            ImageView(activity).apply { setImageDrawable(i.icon) },
            LinearLayout.LayoutParams(activity.dp(44), activity.dp(44)).apply { rightMargin = activity.dp(12) }
        )
        header.addView(TextView(activity).apply {
            text = i.label
            textSize = 17f
            setTextColor(activity.color(R.color.text_primary))
        })
        b.addView(header)

        val sigText =
            if (report.sig.schemes.isEmpty()) activity.getString(R.string.sig_unsigned) else report.sig.schemes.joinToString(" + ")

        b.addView(kv(activity, activity.getString(R.string.info_package), i.packageName).row)
        b.addView(kv(activity, activity.getString(R.string.info_version), "${i.versionName} (${i.versionCode})").row)
        b.addView(kv(activity, activity.getString(R.string.info_file_size), FileOps.formatSize(i.file.length())).row)
        b.addView(kv(activity, activity.getString(R.string.info_signature), sigText) { showSignature(activity, report) }.row)
        b.addView(kv(activity, activity.getString(R.string.info_target_sdk), sdkText(activity, i.targetSdk)).row)
        b.addView(kv(activity, activity.getString(R.string.info_min_sdk), sdkText(activity, i.minSdk)).row)
        i.firstInstall?.let { b.addView(kv(activity, activity.getString(R.string.info_installed), dateText(it)).row) }
        i.lastUpdate?.let { b.addView(kv(activity, activity.getString(R.string.info_updated), dateText(it)).row) }

        val scroll = ScrollView(activity)
        scroll.addView(b)
        AlertDialog.Builder(activity)
            .setView(scroll)
            .setNeutralButton(R.string.info_functions) { _, _ -> onFunctions() }
            .setNegativeButton(R.string.info_view) { _, _ -> onView() }
            .setPositiveButton(R.string.info_install) { _, _ -> onInstall() }
            .show()
    }

    // ------------------------------------------------------ info tanda tangan

    fun showSignature(activity: AppCompatActivity, report: ApkReport) {
        val sig = report.sig
        val cert = sig.cert
        if (cert == null) {
            Toast.makeText(activity, R.string.sig_no_cert, Toast.LENGTH_LONG).show()
            return
        }
        val b = body(activity)
        b.addView(TextView(activity).apply {
            setText(R.string.sig_title)
            textSize = 18f
            setTextColor(activity.color(R.color.text_primary))
            setPadding(0, 0, 0, activity.dp(10))
        })

        val schemeText = if (sig.schemes.isEmpty()) activity.getString(R.string.sig_unsigned) else sig.schemes.joinToString(" + ")
        val statusText = activity.getString(if (sig.verified) R.string.sig_verified else R.string.sig_failed_verify)
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        b.addView(kv(activity, activity.getString(R.string.sig_file), report.info.file.name).row)
        b.addView(kv(activity, activity.getString(R.string.sig_scheme), schemeText).row)
        b.addView(kv(activity, activity.getString(R.string.sig_status), statusText).row)
        b.addView(kv(activity, activity.getString(R.string.sig_algorithm), cert.sigAlgName).row)
        b.addView(kv(activity, activity.getString(R.string.sig_pubkey), publicKeyText(cert)).row)
        b.addView(kv(activity, activity.getString(R.string.sig_valid_from), fmt.format(cert.notBefore)).row)
        b.addView(kv(activity, activity.getString(R.string.sig_valid_to), fmt.format(cert.notAfter)).row)
        b.addView(kv(activity, activity.getString(R.string.sig_owner), cert.subjectX500Principal.getName(X500Principal.RFC1779)).row)

        val hash = cert.hashCode()
        b.addView(kv(activity, "HASH", String.format("0x%08X (%d)", hash, hash)).row)
        val crc = ApkInspector.crc32(cert)
        b.addView(kv(activity, "CRC32", String.format("0x%08X (%d)", crc, crc)).row)

        // Hash sertifikat: format bisa diubah (titik dua / huruf besar); ketuk untuk menyalin.
        val digests = listOf(
            "MD5" to ApkInspector.digest(cert, "MD5"),
            "SHA1" to ApkInspector.digest(cert, "SHA-1"),
            "SHA256" to ApkInspector.digest(cert, "SHA-256")
        )
        var colon = false
        var upper = true
        val digestViews = ArrayList<Kv>()
        fun refreshDigests() {
            digests.forEachIndexed { idx, d -> digestViews[idx].value.text = hex(d.second, colon, upper) }
        }
        for ((name, bytes) in digests) {
            lateinit var holder: Kv
            holder = kv(activity, name, hex(bytes, colon, upper)) { copy(activity, holder.value.text.toString()) }
            digestViews.add(holder)
            b.addView(holder.row)
        }

        b.addView(switchRow(activity, R.string.sig_format, R.string.sig_colon, false) { colon = it; refreshDigests() })
        b.addView(switchRow(activity, null, R.string.sig_upper, true) { upper = it; refreshDigests() })

        val scroll = ScrollView(activity)
        scroll.addView(b)
        AlertDialog.Builder(activity)
            .setView(scroll)
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun switchRow(
        ctx: Context, @StringRes label: Int?, @StringRes text: Int, checked: Boolean, onChange: (Boolean) -> Unit
    ): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, ctx.dp(4), 0, ctx.dp(4))
        }
        row.addView(TextView(ctx).apply {
            if (label != null) setText(label)
            textSize = 13f
            setTextColor(ctx.color(R.color.text_primary))
        }, LinearLayout.LayoutParams(ctx.dp(96), ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(TextView(ctx).apply {
            setText(text)
            textSize = 12f
            setTextColor(ctx.color(R.color.text_secondary))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sw = SwitchCompat(ctx).apply { isChecked = checked }
        sw.setOnCheckedChangeListener { _, on -> onChange(on) }
        row.addView(sw)
        return row
    }

    private fun publicKeyText(cert: X509Certificate): String {
        val pk = cert.publicKey
        return if (pk is RSAPublicKey) "RSA - ${pk.modulus.bitLength()} bit" else pk.algorithm
    }

    private fun hex(bytes: ByteArray, colon: Boolean, upper: Boolean): String {
        val s = bytes.joinToString(if (colon) ":" else "") { String.format("%02x", it.toInt() and 0xFF) }
        return if (upper) s.uppercase() else s
    }

    private fun copy(ctx: Context, text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("", text))
        Toast.makeText(ctx, R.string.sig_copied, Toast.LENGTH_SHORT).show()
    }
}
