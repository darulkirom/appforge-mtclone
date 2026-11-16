package com.test.mtclone

object FileKind {
    private val TEXT_EXT = setOf(
        "xml", "txt", "json", "properties", "pro", "smali", "gradle", "kts",
        "java", "kt", "cfg", "ini", "md", "html", "js", "css", "yml", "yaml"
    )

    fun isDex(name: String) = name == "classes.dex" || Regex("^classes\\d+\\.dex$").matches(name)
    fun isManifest(name: String) = name == "AndroidManifest.xml"
    fun isLikelyText(name: String) = TEXT_EXT.contains(name.substringAfterLast('.', "").lowercase())

    fun hexDump(bytes: ByteArray, maxBytes: Int = 4096): String {
        val n = minOf(bytes.size, maxBytes)
        val sb = StringBuilder()
        var i = 0
        while (i < n) {
            sb.append(String.format("%06X  ", i))
            val end = minOf(i + 16, n)
            for (j in i until end) sb.append(String.format("%02X ", bytes[j]))
            for (j in end until i + 16) sb.append("   ")
            sb.append(" ")
            for (j in i until end) {
                val c = bytes[j].toInt().toChar()
                sb.append(if (c.code in 32..126) c else '.')
            }
            sb.append("\n")
            i += 16
        }
        if (bytes.size > maxBytes) sb.append("\n… dipotong, total ${bytes.size} byte")
        return sb.toString()
    }
}
