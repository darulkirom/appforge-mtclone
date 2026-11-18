package com.test.mtclone

import java.io.File

enum class SortBy { NAME, SIZE, DATE }

object FileOps {

    /** folders dulu lalu files, masing2 disortir sesuai pilihan. Hidden (diawali titik) disaring kalau showHidden=false. */
    fun listDir(dir: File, sortBy: SortBy, ascending: Boolean, showHidden: Boolean): Pair<List<File>, List<File>> {
        val all = dir.listFiles()?.toList() ?: emptyList()
        val visible = if (showHidden) all else all.filterNot { it.name.startsWith(".") }
        val cmp: Comparator<File> = when (sortBy) {
            SortBy.NAME -> compareBy { it.name.lowercase() }
            SortBy.SIZE -> compareBy { it.length() }
            SortBy.DATE -> compareBy { it.lastModified() }
        }
        val folders = visible.filter { it.isDirectory }.sortedWith(cmp)
        val files = visible.filter { it.isFile }.sortedWith(cmp)
        return if (ascending) folders to files else folders.reversed() to files.reversed()
    }

    fun createFolder(parent: File, name: String): Boolean = File(parent, name).mkdir()
    fun createFile(parent: File, name: String): Boolean = try { File(parent, name).createNewFile() } catch (_: Exception) { false }

    fun humanSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        return "%.1f GB".format(mb / 1024.0)
    }

    fun isApk(f: File) = f.extension.equals("apk", true) || f.extension.equals("apks", true)
}
