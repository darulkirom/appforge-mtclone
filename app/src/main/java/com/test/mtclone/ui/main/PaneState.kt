package com.test.mtclone.ui.main

import java.io.File

/** State satu jendela (kiri/kanan): folder aktif + riwayat maju/mundur + filter nama. */
class PaneState(var dir: File) {
    private val back = ArrayDeque<File>()
    private val forward = ArrayDeque<File>()

    var filter: String = ""

    val canBack: Boolean get() = back.isNotEmpty()
    val canForward: Boolean get() = forward.isNotEmpty()

    fun navigate(target: File) {
        if (target != dir) {
            back.addLast(dir)
            forward.clear()
        }
        dir = target
        filter = ""
    }

    fun goBack(): Boolean {
        if (back.isEmpty()) return false
        forward.addLast(dir)
        dir = back.removeLast()
        filter = ""
        return true
    }

    fun goForward(): Boolean {
        if (forward.isEmpty()) return false
        back.addLast(dir)
        dir = forward.removeLast()
        filter = ""
        return true
    }

    fun goUp(): Boolean {
        val parent = dir.parentFile ?: return false
        navigate(parent)
        return true
    }
}
