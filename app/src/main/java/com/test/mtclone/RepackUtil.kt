package com.test.mtclone

import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Tulis ulang APK (zip) dengan satu entry diganti isinya, entry lain disalin apa adanya.
 * Hasilnya BELUM ditandatangani -- perlu di-sign ulang (pakai proyek ApkForge / apksigner)
 * sebelum di-install di perangkat yang mewajibkan signature valid.
 */
object RepackUtil {
    fun rebuildWithReplacedEntry(src: File, entryToReplace: String, newContent: ByteArray, out: OutputStream) {
        ZipFile(src).use { zf ->
            ZipOutputStream(out).use { zos ->
                for (e: ZipEntry in zf.entries()) {
                    val ne = ZipEntry(e.name)
                    zos.putNextEntry(ne)
                    if (e.name == entryToReplace) {
                        zos.write(newContent)
                    } else {
                        zf.getInputStream(e).use { it.copyTo(zos) }
                    }
                    zos.closeEntry()
                }
            }
        }
    }
}
