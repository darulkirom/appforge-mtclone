package com.test.mtclone.core.file

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Operasi file untuk menu tekan-lama (salin, pindah, hapus, kompres, pisah/gabung, statistik). */
object FileActions {

    class Stats(var folders: Int = 0, var files: Int = 0, var bytes: Long = 0)

    /** true kalau [child] adalah [parent] sendiri atau berada di dalamnya. */
    fun isInside(child: File, parent: File): Boolean {
        val c = child.canonicalPath
        val p = parent.canonicalPath
        return c == p || c.startsWith(p + File.separator)
    }

    /** Nama bebas tabrakan: "a.txt" -> "a (1).txt" -> "a (2).txt" ... */
    fun uniqueTarget(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.', name)
        val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
        var i = 1
        while (f.exists()) {
            f = File(dir, "$base ($i)$ext")
            i++
        }
        return f
    }

    fun copy(src: File, dstDir: File): File {
        require(!(src.isDirectory && isInside(dstDir, src))) { "folder tidak bisa disalin ke dalam dirinya sendiri" }
        val target = uniqueTarget(dstDir, src.name)
        copyInto(src, target)
        return target
    }

    private fun copyInto(src: File, target: File) {
        if (src.isDirectory) {
            target.mkdirs()
            src.listFiles()?.forEach { copyInto(it, File(target, it.name)) }
        } else {
            src.copyTo(target)
            target.setLastModified(src.lastModified())
        }
    }

    fun move(src: File, dstDir: File) {
        if (src.parentFile?.canonicalPath == dstDir.canonicalPath) return // sudah di folder itu
        require(!(src.isDirectory && isInside(dstDir, src))) { "folder tidak bisa dipindah ke dalam dirinya sendiri" }
        val target = uniqueTarget(dstDir, src.name)
        if (!src.renameTo(target)) {          // beda volume -> salin lalu hapus
            copyInto(src, target)
            check(delete(src)) { "gagal menghapus sumber setelah menyalin" }
        }
    }

    fun delete(f: File): Boolean = f.deleteRecursively()

    fun rename(f: File, newName: String): Boolean {
        if (newName.isEmpty() || newName.contains('/')) return false
        val target = File(f.parentFile, newName)
        return !target.exists() && f.renameTo(target)
    }

    fun zip(files: List<File>, out: File) {
        ZipOutputStream(out.outputStream().buffered()).use { z ->
            for (f in files) addToZip(z, f, f.name)
        }
    }

    private fun addToZip(z: ZipOutputStream, f: File, path: String) {
        if (f.isDirectory) {
            val kids = f.listFiles()
            if (kids.isNullOrEmpty()) {
                z.putNextEntry(ZipEntry("$path/"))
                z.closeEntry()
            } else {
                for (k in kids) addToZip(z, k, "$path/${k.name}")
            }
        } else {
            val e = ZipEntry(path)
            e.time = f.lastModified()
            z.putNextEntry(e)
            f.inputStream().use { it.copyTo(z) }
            z.closeEntry()
        }
    }

    fun stats(f: File, s: Stats = Stats()): Stats {
        if (f.isDirectory) {
            s.folders++
            f.listFiles()?.forEach { stats(it, s) }
        } else {
            s.files++
            s.bytes += f.length()
        }
        return s
    }

    /** Pisah jadi bagian [partSize] byte: nama.001, nama.002, ... di folder yang sama. */
    fun split(file: File, partSize: Long) {
        require(partSize > 0)
        file.inputStream().buffered().use { input ->
            val buf = ByteArray(64 * 1024)
            var index = 1
            var written = 0L
            var out: java.io.OutputStream? = null
            try {
                while (true) {
                    if (out == null) {
                        out = File(file.parentFile, String.format("%s.%03d", file.name, index)).outputStream().buffered()
                        written = 0
                    }
                    val want = minOf(buf.size.toLong(), partSize - written).toInt()
                    val n = input.read(buf, 0, want)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    written += n
                    if (written >= partSize) {
                        out.close()
                        out = null
                        index++
                    }
                }
            } finally {
                out?.close()
            }
        }
    }

    /** Gabung nama.001, nama.002, ... (mulai dari bagian pertama [first]) menjadi nama. Mengembalikan file hasil. */
    fun merge(first: File): File {
        require(first.name.endsWith(".001")) { "pilih bagian pertama (.001)" }
        val baseName = first.name.removeSuffix(".001")
        val out = uniqueTarget(first.parentFile ?: File("."), baseName)
        out.outputStream().buffered().use { o ->
            var i = 1
            while (true) {
                val part = File(first.parentFile, String.format("%s.%03d", baseName, i))
                if (!part.exists()) break
                part.inputStream().buffered().use { it.copyTo(o) }
                i++
            }
        }
        return out
    }

    /** Tulis daftar jalur (folder dijabarkan rekursif) ke [out], satu baris per file. */
    fun exportList(files: List<File>, out: File) {
        out.bufferedWriter().use { w ->
            for (f in files) {
                if (f.isDirectory) f.walkTopDown().filter { it.isFile }.forEach { w.write(it.absolutePath + "\n") }
                else w.write(f.absolutePath + "\n")
            }
        }
    }
}
