package com.test.mtclone.core.dex

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Membaca "kolam tali" (string_ids) langsung dari header file .dex, tanpa dexlib2. */
object DexStrings {
    private const val HEADER_SIZE = 112
    private const val STRING_IDS_SIZE_OFFSET = 56
    private const val STRING_IDS_OFF_OFFSET = 60

    fun read(bytes: ByteArray): List<String> {
        if (bytes.size < HEADER_SIZE) return emptyList()
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val count = bb.getInt(STRING_IDS_SIZE_OFFSET)
        val idsOff = bb.getInt(STRING_IDS_OFF_OFFSET)
        if (count < 0 || idsOff < 0) return emptyList()
        val out = ArrayList<String>(minOf(count, 1_000_000))
        for (i in 0 until count) {
            val idPos = idsOff.toLong() + i * 4L
            if (idPos + 4 > bytes.size) break
            var p = bb.getInt(idPos.toInt())
            if (p < 0 || p >= bytes.size) break
            while (p < bytes.size && (bytes[p].toInt() and 0x80) != 0) p++ // lewati uleb128 panjang utf16
            p++
            var end = p
            while (end < bytes.size && bytes[end].toInt() != 0) end++
            if (p > end) break
            out.add(String(bytes, p, end - p, Charsets.UTF_8))
        }
        return out
    }
}
