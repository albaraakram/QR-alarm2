package com.example.alarmqr

import java.security.MessageDigest

object HashUtil {
    fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val normalized = input.trim()
        val bytes = md.digest(normalized.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

