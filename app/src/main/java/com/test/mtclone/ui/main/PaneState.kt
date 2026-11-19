package com.test.mtclone.ui.main

import com.test.mtclone.core.apk.ZipNode
import com.test.mtclone.core.apk.ZipTree
import java.io.File

/**
 * State satu jendela (kiri/kanan): folder aktif, riwayat maju/mundur, filter nama,
 * seleksi file, dan mode "APK sebagai folder" (isi APK ditampilkan langsung di jendela).
 */
class PaneState(var dir: File) {
    private val back = ArrayDeque<File>()
    private val forward = ArrayDeque<File>()

    var filter: String = ""
    val selected = LinkedHashSet<File>()

    // ---- mode APK ----
    var apk: File? = null
        private set
    private var apkRoot: ZipNode? = null
    private val apkStack = ArrayList<ZipNode>()

    val inApk: Boolean get() = apk != null
    val canBack: Boolean get() = inApk || back.isNotEmpty()
    val canForward: Boolean get() = !inApk && forward.isNotEmpty()

    /** Melempar exception kalau file bukan zip yang valid. */
    fun openApk(file: File) {
        apkRoot = ZipTree.build(file)
        apk = file
        apkStack.clear()
        selected.clear()
        filter = ""
    }

    private fun closeApk() {
        apk = null
        apkRoot = null
        apkStack.clear()
    }

    fun apkNode(): ZipNode? = if (apkStack.isEmpty()) apkRoot else apkStack[apkStack.lastIndex]

    fun apkEnter(node: ZipNode) {
        apkStack.add(node)
    }

    /** Jalur tampilan, mis. "Brankas_2_0_mod.apk/kotlin/". */
    fun apkPath(): String = (apk?.name ?: "") + "/" + apkStack.joinToString("") { it.name + "/" }

    fun navigate(target: File) {
        closeApk()
        if (target != dir) {
            back.addLast(dir)
            forward.clear()
        }
        dir = target
        filter = ""
        selected.clear()
    }

    fun goBack(): Boolean {
        if (inApk) return goUp()
        if (back.isEmpty()) return false
        forward.addLast(dir)
        dir = back.removeLast()
        filter = ""
        selected.clear()
        return true
    }

    fun goForward(): Boolean {
        if (inApk || forward.isEmpty()) return false
        back.addLast(dir)
        dir = forward.removeLast()
        filter = ""
        selected.clear()
        return true
    }

    /** Di dalam APK: naik satu folder, di akar APK keluar ke folder tempat APK berada. */
    fun goUp(): Boolean {
        if (inApk) {
            if (apkStack.isNotEmpty()) apkStack.removeAt(apkStack.lastIndex) else closeApk()
            selected.clear()
            return true
        }
        val parent = dir.parentFile ?: return false
        navigate(parent)
        return true
    }
}
