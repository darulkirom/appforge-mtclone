package com.test.mtclone.core.sign

import android.content.Context
import com.android.apksig.ApkSigner
import java.io.File
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Tanda tangan APK (v1 + v2 + v3) memakai testkey AOSP publik
 * (assets/keys/testkey.pk8 + testkey.x509.pem), seperti fitur "Tanda tangani file" di MT Manager.
 *
 * testkey bersifat publik -> hanya untuk APK uji / mod pribadi.
 */
object ApkSigning {
    private const val KEY_ASSET = "keys/testkey.pk8"
    private const val CERT_ASSET = "keys/testkey.x509.pem"

    /** Menulis [input] yang sudah ditandatangani ke [output]. Melempar exception kalau gagal. */
    fun sign(context: Context, input: File, output: File) {
        require(input.absoluteFile != output.absoluteFile) { "input dan output tidak boleh sama" }
        val signer = ApkSigner.SignerConfig.Builder("testkey", loadKey(context), listOf(loadCert(context))).build()
        if (output.exists()) output.delete()
        ApkSigner.Builder(listOf(signer))
            .setInputApk(input)
            .setOutputApk(output)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .build()
            .sign()
    }

    /** Nama keluaran: foo.apk -> foo_sign.apk */
    fun signedName(input: File): String = input.nameWithoutExtension + "_sign.apk"

    private fun loadKey(context: Context): PrivateKey {
        val der = context.assets.open(KEY_ASSET).use { it.readBytes() }
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der))
    }

    private fun loadCert(context: Context): X509Certificate =
        context.assets.open(CERT_ASSET).use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
        }
}
