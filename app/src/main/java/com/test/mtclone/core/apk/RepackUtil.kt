package com.test.mtclone.core.apk

import java.io.File
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Tulis ulang APK (zip) dengan satu entry diganti isinya, entry lain disalin apa adanya.
 *
 * Metode kompresi tiap entry dipertahankan (STORED tetap STORED) dan entry STORED
 * di-align (4 byte; 4096 byte untuk .so) seperti zipalign. Ini penting: Android 11+
 * menolak instal APK (targetSdk >= 30) kalau resources.arsc dikompres / tidak ter-align.
 *
 * Hasilnya BELUM bertanda tangan -- tanda tangani lewat [com.test.mtclone.core.sign.ApkSigning].
 */
object RepackUtil {

    fun rebuildWithReplacedEntry(src: File, entryToReplace: String, newContent: ByteArray, out: OutputStream) {
        val counter = CountingOutputStream(out)
        ZipFile(src).use { zf ->
            ZipOutputStream(counter).use { zos ->
                for (e: ZipEntry in zf.entries()) {
                    val replaced = e.name == entryToReplace
                    val ne = ZipEntry(e.name)
                    if (e.time != -1L) ne.time = e.time

                    if (e.method == ZipEntry.STORED) {
                        ne.method = ZipEntry.STORED
                        if (replaced) {
                            val crc = CRC32().apply { update(newContent) }
                            ne.size = newContent.size.toLong()
                            ne.compressedSize = newContent.size.toLong()
                            ne.crc = crc.value
                        } else {
                            ne.size = e.size
                            ne.compressedSize = e.size
                            ne.crc = e.crc
                        }
                        if (!e.isDirectory) {
                            val extra = alignmentExtra(counter.count, e.name)
                            if (extra != null) ne.extra = extra
                        }
                    } else {
                        ne.method = ZipEntry.DEFLATED
                    }

                    zos.putNextEntry(ne)
                    if (replaced) zos.write(newContent) else zf.getInputStream(e).use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }

    /**
     * Extra field pengisi supaya data entry STORED mulai di kelipatan 4 (atau 4096 untuk .so).
     * Header lokal ZIP = 30 byte + panjang nama + panjang extra.
     */
    private fun alignmentExtra(entryStart: Long, name: String): ByteArray? {
        val align = if (name.endsWith(".so")) 4096 else 4
        val dataStart = entryStart + 30 + name.toByteArray(Charsets.UTF_8).size
        var pad = ((align - (dataStart % align)) % align).toInt()
        if (pad == 0) return null
        if (pad < 4) pad += align // extra field minimal 4 byte (id + panjang)
        val extra = ByteArray(pad)
        extra[0] = 0x35.toByte() // id 0xD935 = pengisi align milik zipalign
        extra[1] = 0xD9.toByte()
        val payload = pad - 4
        extra[2] = (payload and 0xFF).toByte()
        extra[3] = ((payload shr 8) and 0xFF).toByte()
        return extra
    }

    private class CountingOutputStream(private val inner: OutputStream) : OutputStream() {
        var count: Long = 0
            private set

        override fun write(b: Int) { inner.write(b); count++ }
        override fun write(b: ByteArray, off: Int, len: Int) { inner.write(b, off, len); count += len }
        override fun flush() = inner.flush()
        override fun close() = inner.close()
    }
}
