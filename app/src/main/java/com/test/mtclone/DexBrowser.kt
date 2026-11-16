package com.test.mtclone

import org.jf.baksmali.Adaptors.ClassDefinition
import org.jf.baksmali.BaksmaliOptions
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.iface.ClassDef
import org.jf.dexlib2.iface.DexFile
import org.jf.util.IndentingWriter
import java.io.File
import java.io.StringWriter

/** Daftar class & smali per class dari classes*.dex, pakai dexlib2 + baksmali (read-only di v1). */
object DexBrowser {

    fun open(apk: File, dexEntryName: String): DexFile {
        val bytes = ZipTree.readBytes(apk, dexEntryName)
        val tmp = File.createTempFile("dex", ".dex")
        tmp.writeBytes(bytes)
        return DexFileFactory.loadDexFile(tmp, Opcodes.getDefault())
    }

    fun classNames(dex: DexFile): List<String> =
        dex.classes.map { it.type }.sorted()

    fun smaliOf(dex: DexFile, classType: String): String {
        val cls: ClassDef = dex.classes.first { it.type == classType }
        val options = BaksmaliOptions()
        val sw = StringWriter()
        val writer = IndentingWriter(sw)
        ClassDefinition(options, cls).writeTo(writer)
        writer.flush()
        return sw.toString()
    }
}
