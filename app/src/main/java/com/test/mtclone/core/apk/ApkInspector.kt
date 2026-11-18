package com.test.mtclone.core.apk

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import com.android.apksig.ApkVerifier
import java.io.File
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.zip.CRC32

/** Hasil verifikasi tanda tangan APK (apksig). [cert] = sertifikat penanda tangan pertama. */
class SigInfo(
    val schemes: List<String>,
    val verified: Boolean,
    val cert: X509Certificate?
)

class ApkInfo(
    val file: File,
    val label: String,
    val icon: Drawable?,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    /** Waktu pasang pertama / pembaruan terakhir kalau paket yang sama sudah terpasang. */
    val firstInstall: Long?,
    val lastUpdate: Long?
)

class ApkReport(val info: ApkInfo, val sig: SigInfo)

/** Membaca info paket dan tanda tangan dari file .apk (untuk dialog info ala MT Manager). */
object ApkInspector {

    /** null kalau file bukan APK yang bisa dibaca sistem (mis. .apks / rusak). Jalankan di thread background. */
    fun inspect(context: Context, file: File): ApkReport? {
        val info = readInfo(context, file) ?: return null
        return ApkReport(info, readSignature(file))
    }

    @Suppress("DEPRECATION")
    private fun readInfo(context: Context, file: File): ApkInfo? {
        val pm = context.packageManager
        val pi = pm.getPackageArchiveInfo(file.absolutePath, 0) ?: return null
        var label: String = pi.packageName
        var icon: Drawable? = null
        var minSdk = 0
        var targetSdk = 0
        val ai = pi.applicationInfo
        if (ai != null) {
            ai.sourceDir = file.absolutePath
            ai.publicSourceDir = file.absolutePath
            label = pm.getApplicationLabel(ai).toString()
            icon = try { pm.getApplicationIcon(ai) } catch (e: Exception) { null }
            minSdk = ai.minSdkVersion
            targetSdk = ai.targetSdkVersion
        }
        val code: Long = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
        val installed = try { pm.getPackageInfo(pi.packageName, 0) } catch (e: Exception) { null }
        return ApkInfo(
            file, label, icon, pi.packageName, pi.versionName ?: "", code, minSdk, targetSdk,
            installed?.firstInstallTime, installed?.lastUpdateTime
        )
    }

    private fun readSignature(file: File): SigInfo = try {
        val r = ApkVerifier.Builder(file).build().verify()
        val schemes = ArrayList<String>()
        if (r.isVerifiedUsingV1Scheme) schemes.add("V1")
        if (r.isVerifiedUsingV2Scheme) schemes.add("V2")
        if (r.isVerifiedUsingV3Scheme) schemes.add("V3")
        SigInfo(schemes, r.isVerified, r.signerCertificates.firstOrNull())
    } catch (e: Exception) {
        SigInfo(emptyList(), false, null)
    }

    /** Nama rilis Android untuk level API, null kalau tidak dikenal. */
    fun androidName(api: Int): String? = when (api) {
        21 -> "5.0"; 22 -> "5.1"; 23 -> "6"; 24 -> "7"; 25 -> "7.1"; 26 -> "8"; 27 -> "8.1"
        28 -> "9"; 29 -> "10"; 30 -> "11"; 31 -> "12"; 32 -> "12L"; 33 -> "13"; 34 -> "14"
        35 -> "15"; 36 -> "16"
        else -> null
    }

    fun digest(cert: X509Certificate, algorithm: String): ByteArray =
        MessageDigest.getInstance(algorithm).digest(cert.encoded)

    fun crc32(cert: X509Certificate): Int = CRC32().apply { update(cert.encoded) }.value.toInt()
}
