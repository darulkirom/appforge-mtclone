package com.test.mtclone.core.prefs

import android.content.Context
import com.test.mtclone.core.file.SortBy
import com.test.mtclone.core.file.SortSpec

/** Semua pengaturan & state ringan aplikasi (SharedPreferences "mtclone"). Index jendela: 0 = kiri, 1 = kanan. */
class AppPrefs(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var homepage: String?
        get() = sp.getString(K_HOMEPAGE, null)
        set(value) { sp.edit().putString(K_HOMEPAGE, value).apply() }

    var showHidden: Boolean
        get() = sp.getBoolean(K_HIDDEN, false)
        set(value) { sp.edit().putBoolean(K_HIDDEN, value).apply() }

    /** Saat menyimpan file teks, simpan dulu salinan lama sebagai nama.bak. */
    var backupOnSave: Boolean
        get() = sp.getBoolean(K_BACKUP, true)
        set(value) { sp.edit().putBoolean(K_BACKUP, value).apply() }

    var listSize: Int
        get() = sp.getInt(K_LIST_SIZE, SIZE_SMALL)
        set(value) { sp.edit().putInt(K_LIST_SIZE, value).apply() }

    var nameLines: Int
        get() = sp.getInt(K_NAME_LINES, 2)
        set(value) { sp.edit().putInt(K_NAME_LINES, value).apply() }

    var dateFormat: String
        get() = sp.getString(K_DATE_FORMAT, DEFAULT_DATE_FORMAT) ?: DEFAULT_DATE_FORMAT
        set(value) { sp.edit().putString(K_DATE_FORMAT, value).apply() }

    // ---------------------------------------------------------- jalur startup

    fun startupMode(pane: Int): Int = sp.getInt("startup_$pane", STARTUP_HOME)
    fun setStartupMode(pane: Int, mode: Int) { sp.edit().putInt("startup_$pane", mode).apply() }

    fun lastPath(pane: Int): String? = sp.getString("last_$pane", null)
    fun setLastPath(pane: Int, path: String) { sp.edit().putString("last_$pane", path).apply() }

    // --------------------------------------------------------------- bookmark

    fun bookmarks(): List<String> = (sp.getStringSet(K_BOOKMARKS, emptySet()) ?: emptySet()).toList().sorted()

    fun addBookmark(path: String) {
        val set = (sp.getStringSet(K_BOOKMARKS, emptySet()) ?: emptySet()).toMutableSet()
        set.add(path)
        sp.edit().putStringSet(K_BOOKMARKS, set).apply()
    }

    // ------------------------------------------------------------------ sortir

    /** Pengaturan per-folder (kalau ada) menang atas pengaturan umum jendela. */
    fun getSort(pane: Int, dir: String): SortSpec {
        parseSort(sp.getString("sort_dir:$dir", null))?.let { return it }
        return parseSort(sp.getString("sort_pane:$pane", null)) ?: SortSpec()
    }

    fun setSort(pane: Int, dir: String, spec: SortSpec, onlyThisFolder: Boolean) {
        val value = "${spec.by.name}|${if (spec.ascending) 1 else 0}"
        val ed = sp.edit()
        if (onlyThisFolder) {
            ed.putString("sort_dir:$dir", value)
        } else {
            ed.remove("sort_dir:$dir")
            ed.putString("sort_pane:$pane", value)
        }
        ed.apply()
    }

    private fun parseSort(raw: String?): SortSpec? {
        if (raw == null) return null
        val parts = raw.split("|")
        if (parts.size != 2) return null
        val by = try { SortBy.valueOf(parts[0]) } catch (e: IllegalArgumentException) { return null }
        return SortSpec(by, parts[1] == "1")
    }

    companion object {
        private const val NAME = "mtclone"
        private const val K_HOMEPAGE = "homepage"
        private const val K_HIDDEN = "show_hidden"
        private const val K_BACKUP = "backup_on_save"
        private const val K_LIST_SIZE = "list_size"
        private const val K_NAME_LINES = "name_lines"
        private const val K_DATE_FORMAT = "date_format"
        private const val K_BOOKMARKS = "bookmarks"

        const val DEFAULT_DATE_FORMAT = "yy-MM-dd HH:mm"
        val DATE_FORMATS = listOf("yy-MM-dd HH:mm", "yyyy-MM-dd HH:mm:ss", "dd/MM/yy HH:mm", "dd MMM yyyy HH:mm")

        const val SIZE_SMALL = 0
        const val SIZE_MEDIUM = 1
        const val SIZE_LARGE = 2

        const val STARTUP_HOME = 0
        const val STARTUP_LAST = 1
    }
}
