package com.test.mtclone.ui.common

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import com.test.mtclone.R
import java.io.File
import java.util.concurrent.Executors

/** Jenis ikon file: menentukan warna kotak dan glyph (mengikuti pembagian warna MT Manager). */
enum class IconKind {
    FOLDER, DOC, ARCHIVE, DEX, APK, WEB, MEDIA, FILE;

    companion object {
        private val DOC_EXT = setOf(
            "yaml", "yml", "kt", "kts", "txt", "json", "xml", "md", "java", "gradle", "properties",
            "smali", "js", "css", "cfg", "ini", "pro", "log", "csv", "sh", "py", "toml"
        )
        private val ARCHIVE_EXT = setOf("zip", "jar", "rar", "7z", "tar", "gz", "apks", "xapk", "aab")
        private val MEDIA_EXT = setOf("mp3", "mp4", "mkv", "m4a", "wav", "ogg", "flac", "png", "jpg", "jpeg", "webp", "gif", "webm")

        fun forName(name: String): IconKind {
            val ext = name.substringAfterLast('.', "").lowercase()
            return when {
                ext == "apk" -> APK
                ext == "dex" -> DEX
                ext == "html" || ext == "htm" -> WEB
                ext in DOC_EXT -> DOC
                ext in ARCHIVE_EXT -> ARCHIVE
                ext in MEDIA_EXT -> MEDIA
                else -> FILE
            }
        }
    }
}

/** Kotak ikon bersudut membulat (ala MT Manager). */
fun Context.newIconBadge(sizeDp: Int): ImageView = ImageView(this).apply {
    scaleType = ImageView.ScaleType.FIT_CENTER
    val p = dp((sizeDp * 0.2f).toInt())
    setPadding(p, p, p, p)
}

fun ImageView.bindKind(kind: IconKind) {
    val bg = when (kind) {
        IconKind.FOLDER -> R.drawable.bg_icon_folder
        IconKind.DOC -> R.drawable.bg_icon_doc
        IconKind.ARCHIVE -> R.drawable.bg_icon_archive
        IconKind.DEX -> R.drawable.bg_icon_dex
        IconKind.APK -> R.drawable.bg_icon_apk
        IconKind.WEB -> R.drawable.bg_icon_web
        IconKind.MEDIA -> R.drawable.bg_icon_media
        IconKind.FILE -> R.drawable.bg_icon_file
    }
    val glyph = when (kind) {
        IconKind.FOLDER -> R.drawable.ic_folder
        IconKind.APK -> R.drawable.ic_apk
        else -> R.drawable.ic_file
    }
    setBackgroundResource(bg)
    setImageResource(glyph)
}

/** Memuat ikon asli aplikasi dari file .apk di background (cache), lalu menimpa ikon generik. */
object ApkIconLoader {
    private val cache = LruCache<String, Drawable>(80)
    private val missing = HashSet<String>()
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun load(ctx: Context, file: File, into: ImageView) {
        val key = file.absolutePath + "|" + file.lastModified()
        into.tag = key
        val hit = cache.get(key)
        if (hit != null) {
            show(into, hit)
            return
        }
        if (missing.contains(key)) return
        val appContext = ctx.applicationContext
        executor.execute {
            val icon: Drawable? = try {
                val pm = appContext.packageManager
                @Suppress("DEPRECATION")
                val pi = pm.getPackageArchiveInfo(file.absolutePath, 0)
                val ai = pi?.applicationInfo
                if (ai != null) {
                    ai.sourceDir = file.absolutePath
                    ai.publicSourceDir = file.absolutePath
                    pm.getApplicationIcon(ai)
                } else null
            } catch (e: Exception) {
                null
            }
            main.post {
                if (icon != null) cache.put(key, icon) else missing.add(key)
                if (icon != null && into.tag == key) show(into, icon)
            }
        }
    }

    private fun show(into: ImageView, icon: Drawable) {
        into.background = null
        into.setPadding(0, 0, 0, 0)
        into.setImageDrawable(icon)
    }
}
