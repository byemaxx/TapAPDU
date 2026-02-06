package com.byemaxx.tappapdu.utils

object ApduUtils {
    fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    fun toHexString(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = "0123456789ABCDEF"[v ushr 4]
            hexChars[j * 2 + 1] = "0123456789ABCDEF"[v and 0x0F]
        }
        return String(hexChars)
    }

    fun getStatusWord(bytes: ByteArray): String {
        if (bytes.size < 2) return ""
        return toHexString(byteArrayOf(bytes[bytes.size - 2], bytes[bytes.size - 1]))
    }

    fun getSWDescription(sw: String): String {
        return when (sw) {
            "9000" -> "Success (OK)"
            "6A82" -> "File Not Found"
            "6700" -> "Wrong Length"
            "6E00" -> "CLA Not Supported"
            "6D00" -> "INS Not Supported"
            "6985" -> "Conditions Not Satisfied"
            "6A81" -> "Function not supported"
            "6A83" -> "Record not found"
            else -> {
                if (sw.startsWith("61")) "More Data Available (${sw.substring(2)} bytes)"
                else if (sw.startsWith("6C")) "Wrong Length (Try Le=${sw.substring(2)})"
                else "Unknown"
            }
        }
    }

    fun toAsciiString(bytes: ByteArray): String {
        val sb = StringBuilder()
        val limit = if(bytes.size >= 2) bytes.size - 2 else bytes.size
        
        for (i in 0 until limit) {
            val b = bytes[i].toInt() and 0xFF
            if (b in 32..126) {
                sb.append(b.toChar())
            } else {
                sb.append('.')
            }
        }
        return sb.toString()
    }
}
