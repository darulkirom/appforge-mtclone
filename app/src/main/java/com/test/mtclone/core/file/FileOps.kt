package com.test.mtclone.core.file

import java.io.File
import java.util.Locale

enum class SortBy { NAME, SIZE, DATE, TYPE }

data class SortSpec(val by: SortBy = SortBy.NAME, val ascending: Boolean = true)

class Listing(val folders: List<File>, val files: List<File>)

object FileOps {

    /**
     * Folder dulu lalu file, masing-masing disortir sesuai [spec].
     * File tersembunyi (diawali titik) disaring kalau [showHidden] = false;
     * [filter] (kalau tidak kosong) menyaring berdasarkan potongan nama.
     */
    fun listDir(dir: File, spec: SortSpec, showHidden: Boolean, filter: String = ""): Listing {
        val all = dir.listFiles()?.toList() ?: emptyList()
        var visible = if (showHidden) all else all.filterNot { it.name.startsWith(".") }
        if (filter.isNotEmpty()) visible = visible.filter { it.name.contains(filter, ignoreCase = true) }

        val byName: Comparator<File> = compareBy<File> { it.name.lowercase() }
        val fileCmp: Comparator<File> = when (spec.by) {
            SortBy.NAME -> byName
            SortBy.SIZE -> compareBy<File> { it.length() }.then(byName)
            SortBy.DATE -> compareBy<File> { it.lastModified() }.then(byName)
            SortBy.TYPE -> compareBy<File> { it.extension.lowercase() }.then(byName)
        }
        // Ukuran/jenis tidak bermakna untuk folder -> urut nama; tanggal ikut dipakai.
        val dirCmp: Comparator<File> = if (spec.by == SortBy.DATE) fileCmp else byName

        val folders = visible.filter { it.isDirectory }.sortedWith(dirCmp)
        val files = visible.filter { !it.isDirectory }.sortedWith(fileCmp)
        return if (spec.ascending) Listing(folders, files) else Listing(folders.reversed(), files.reversed())
    }

    fun createFolder(parent: File, name: String): Boolean = File(parent, name).mkdir()

    fun createFile(parent: File, name: String): Boolean =
        try { File(parent, name).createNewFile() } catch (e: Exception) { false }

    /** Format ringkas ala MT Manager: 0B, 37.23K, 11.59M, 19.86G. */
    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "${bytes}B"
        val units = charArrayOf('K', 'M', 'G', 'T')
        var v = bytes.toDouble()
        var i = -1
        while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
        return String.format(Locale.US, "%.2f%c", v, units[i])
    }

    fun isApk(f: File) = f.extension.equals("apk", true) || f.extension.equals("apks", true)
}
