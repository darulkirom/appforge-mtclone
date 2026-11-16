package com.test.mtclone

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Model file/folder di dalam APK (zip), dibentuk dari daftar ZipEntry.
 * Satu node bisa berupa folder (anak-anaknya diisi dari entry yang namanya diawali path ini)
 * atau file (nama ZipEntry aslinya disimpan di zipName).
 */
class ZipNode(val name: String, val isDir: Boolean, val zipName: String? = null, val size: Long = 0) {
    val children = sortedMapOf<String, ZipNode>()
}

object ZipTree {
    fun build(apk: File): ZipNode {
        val root = ZipNode("", true)
        ZipFile(apk).use { zf ->
            for (e: ZipEntry in zf.entries()) {
                val parts = e.name.trim('/').split("/")
                var cur = root
                for ((i, p) in parts.withIndex()) {
                    val last = i == parts.size - 1
                    val isDir = !last || e.isDirectory
                    cur = cur.children.getOrPut(p) {
                        ZipNode(p, isDir, if (!isDir) e.name else null, if (!isDir) e.size else 0)
                    }
                }
            }
        }
        return root
    }

    fun readBytes(apk: File, zipName: String): ByteArray =
        ZipFile(apk).use { zf -> zf.getInputStream(zf.getEntry(zipName)).use { it.readBytes() } }
}
