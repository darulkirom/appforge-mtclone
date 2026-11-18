package com.test.mtclone

import org.jf.baksmali.Baksmali
import org.jf.baksmali.BaksmaliOptions
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.iface.DexFile
import java.io.File

/**
 * Daftar class & smali per class dari classes*.dex, pakai dexlib2 + baksmali (read-only di v1).
 *
 * Sengaja pakai API publik tingkat atas Baksmali.disassembleDexFile(...) (menulis .smali ke
 * folder), bukan memanggil kelas internal org.jf.baksmali.Adaptors.ClassDefinition langsung --
 * API internal itu ganti tanda tangan antar versi baksmali (2.4.0 vs 2.5.2 beda tipe writer),
 * sedangkan disassembleDexFile adalah API yang dipakai CLI baksmali sendiri, jauh lebih stabil.
 */
object DexBrowser {

    fun open(apk: File, dexEntryName: String): DexFile {
        val bytes = ZipTree.readBytes(apk, dexEntryName)
        val tmp = File.createTempFile("dex", ".dex")
        tmp.writeBytes(bytes)
        return DexFileFactory.loadDexFile(tmp, Opcodes.getDefault())
    }

    fun classNames(dex: DexFile): List<String> =
        dex.classes.map { it.type }.sorted()

    /** classType contoh: "Lcom/test/mtclone/MainActivity;" (format descriptor dex, sama seperti classNames()). */
    fun smaliOf(dex: DexFile, classType: String): String {
        val outDir = File.createTempFile("baksmali", "").apply { delete(); mkdirs() }
        val options = BaksmaliOptions()
        val ok = Baksmali.disassembleDexFile(dex, outDir, 1, options, listOf(classType))
        if (!ok) return "// gagal disassemble $classType"

        val smaliFile = outDir.walkTopDown().firstOrNull { it.isFile && it.extension == "smali" }
            ?: return "// tidak ketemu hasil .smali untuk $classType"
        val text = smaliFile.readText()
        outDir.deleteRecursively()
        return text
    }
}
