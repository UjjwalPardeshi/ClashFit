package com.clashfit.cloud

import java.security.MessageDigest

object FriendCodes {
    private val CROCKFORD_BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    /**
     * Generate a 6-character friend code from a user ID using Crockford base32 encoding
     * of the first 6 bytes of SHA-256(uid).
     * Format is deterministic and case-insensitive (always uppercase).
     */
    fun forUid(uid: String): String {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(uid.toByteArray())

        // Take first 6 bytes and encode as base32
        val code = StringBuilder()
        var bitBuffer = 0L
        var bitsInBuffer = 0

        for (i in 0 until 6) {
            bitBuffer = (bitBuffer shl 8) or (hash[i].toLong() and 0xFFL)
            bitsInBuffer += 8

            while (bitsInBuffer >= 5) {
                bitsInBuffer -= 5
                val index = ((bitBuffer shr bitsInBuffer) and 0x1FL).toInt()
                code.append(CROCKFORD_BASE32[index])
            }
        }

        // Handle remaining bits
        if (bitsInBuffer > 0) {
            val index = ((bitBuffer shl (5 - bitsInBuffer)) and 0x1FL).toInt()
            code.append(CROCKFORD_BASE32[index])
        }

        return code.toString().take(6)
    }
}
