package com.test.mtclone

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decoder Android Binary XML (dipakai untuk AndroidManifest.xml terkompilasi di dalam APK)
 * jadi teks XML yang bisa dibaca. Best-effort: mendukung struktur umum (string pool,
 * start/end namespace, start/end element, atribut dengan tipe data umum). Tidak semua
 * variasi chunk (mis. styled string / CDATA jarang) dijamin tertangani. Read-only.
 */
object AxmlDecoder {

    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_XML_START_NAMESPACE_TYPE = 0x0100
    private const val RES_XML_END_NAMESPACE_TYPE = 0x0101
    private const val RES_XML_START_ELEMENT_TYPE = 0x0102
    private const val RES_XML_END_ELEMENT_TYPE = 0x0103

    fun decode(bytes: ByteArray): String {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.short; buf.short; buf.int // header chunk XML (type, headerSize, totalSize)

        var strings: List<String> = emptyList()
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        var depth = 0
        val nsPrefixByUri = HashMap<String, String>()
        var pendingNsDecls = StringBuilder()

        fun indent() = "    ".repeat(depth)
        fun str(idx: Int): String = if (idx in strings.indices) strings[idx] else ""

        while (buf.remaining() >= 8) {
            val chunkStart = buf.position()
            val type = buf.short.toInt() and 0xFFFF
            val headerSize = buf.short.toInt() and 0xFFFF
            val chunkSize = buf.int
            if (chunkSize <= 0 || chunkStart + chunkSize > bytes.size) break

            when (type) {
                RES_STRING_POOL_TYPE -> {
                    strings = readStringPool(buf, chunkStart, chunkSize, headerSize)
                }
                RES_XML_START_NAMESPACE_TYPE -> {
                    buf.int; buf.int // line, comment
                    val prefixIdx = buf.int
                    val uriIdx = buf.int
                    val prefix = if (prefixIdx >= 0) str(prefixIdx) else "ns"
                    val uri = if (uriIdx >= 0) str(uriIdx) else ""
                    nsPrefixByUri[uri] = prefix
                    pendingNsDecls.append(" xmlns:$prefix=\"${escape(uri)}\"")
                    buf.position(chunkStart + chunkSize)
                }
                RES_XML_END_NAMESPACE_TYPE -> {
                    buf.position(chunkStart + chunkSize)
                }
                RES_XML_START_ELEMENT_TYPE -> {
                    buf.int; buf.int // line, comment
                    val nsIdx = buf.int
                    val nameIdx = buf.int
                    buf.short // attributeStart
                    val attrSize = buf.short.toInt() and 0xFFFF
                    val attrCount = buf.short.toInt() and 0xFFFF
                    buf.short; buf.short; buf.short // idIdx, classIdx, styleIdx

                    val tagNs = if (nsIdx >= 0) str(nsIdx) else ""
                    val tagName = str(nameIdx)
                    val tagPrefix = nsPrefixByUri[tagNs]
                    val tag = if (tagPrefix != null) "$tagPrefix:$tagName" else tagName

                    sb.append(indent()).append("<").append(tag)
                    if (pendingNsDecls.isNotEmpty()) { sb.append(pendingNsDecls); pendingNsDecls = StringBuilder() }

                    repeat(attrCount) {
                        val aNsIdx = buf.int
                        val aNameIdx = buf.int
                        val aRawValIdx = buf.int
                        buf.short // size
                        buf.get()  // res0
                        val dataType = buf.get().toInt() and 0xFF
                        val data = buf.int

                        val aNs = if (aNsIdx >= 0) str(aNsIdx) else ""
                        val aPrefix = nsPrefixByUri[aNs]
                        val aName = str(aNameIdx)
                        val fullName = if (aPrefix != null) "$aPrefix:$aName" else aName
                        val value = formatAttrValue(dataType, data, aRawValIdx, ::str)
                        sb.append(" ").append(fullName).append("=\"").append(escape(value)).append("\"")
                    }
                    if (attrSize != 20) buf.position(chunkStart + headerSize + attrCount * attrSize)
                    sb.append(">\n")
                    depth++
                }
                RES_XML_END_ELEMENT_TYPE -> {
                    buf.int; buf.int
                    val nsIdx = buf.int
                    val nameIdx = buf.int
                    depth = maxOf(0, depth - 1)
                    val tagNs = if (nsIdx >= 0) str(nsIdx) else ""
                    val tagPrefix = nsPrefixByUri[tagNs]
                    val tagName = str(nameIdx)
                    val tag = if (tagPrefix != null) "$tagPrefix:$tagName" else tagName
                    sb.append(indent()).append("</").append(tag).append(">\n")
                    buf.position(chunkStart + chunkSize)
                }
                else -> buf.position(chunkStart + chunkSize)
            }
        }
        return sb.toString()
    }

    private fun formatAttrValue(dataType: Int, data: Int, rawValIdx: Int, str: (Int) -> String): String =
        when (dataType) {
            0x03 -> if (rawValIdx >= 0) str(rawValIdx) else "" // TYPE_STRING
            0x10 -> data.toString()                             // TYPE_INT_DEC
            0x11 -> "0x" + Integer.toHexString(data)            // TYPE_INT_HEX
            0x12 -> if (data != 0) "true" else "false"          // TYPE_INT_BOOLEAN
            0x01 -> "@" + Integer.toHexString(data)             // TYPE_REFERENCE
            else -> "0x" + Integer.toHexString(data)
        }

    private fun escape(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun readStringPool(buf: ByteBuffer, chunkStart: Int, chunkSize: Int, headerSize: Int): List<String> {
        val stringCount = buf.int
        val styleCount = buf.int
        val flags = buf.int
        val stringsStart = buf.int
        buf.int // stylesStart
        val isUtf8 = (flags and 0x100) != 0

        val offsets = IntArray(stringCount) { buf.int }
        repeat(styleCount) { buf.int }

        val dataBase = chunkStart + stringsStart
        val result = ArrayList<String>(stringCount)
        for (off in offsets) {
            val pos = dataBase + off
            val p = buf.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            p.position(pos)
            if (isUtf8) {
                readUtf8Len(p) // utf16 len (ignored, pakai utf8 len)
                val len = readUtf8Len(p)
                val arr = ByteArray(len)
                p.get(arr)
                result.add(String(arr, Charsets.UTF_8))
            } else {
                val len = readUtf16Len(p)
                val arr = ByteArray(len * 2)
                p.get(arr)
                result.add(String(arr, Charsets.UTF_16LE))
            }
        }
        buf.position(chunkStart + chunkSize)
        return result
    }

    private fun readUtf8Len(p: ByteBuffer): Int {
        var v = p.get().toInt() and 0xFF
        if (v and 0x80 != 0) v = ((v and 0x7F) shl 8) or (p.get().toInt() and 0xFF)
        return v
    }

    private fun readUtf16Len(p: ByteBuffer): Int {
        var v = p.short.toInt() and 0xFFFF
        if (v and 0x8000 != 0) v = ((v and 0x7FFF) shl 16) or (p.short.toInt() and 0xFFFF)
        return v
    }
}
