package com.clashfit.cloud

import java.security.MessageDigest

object FriendCodes {
    private const val CROCKFORD_BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    /** Every stored code is exactly this long, so a lookup of any other length cannot match one. */
    const val LENGTH = 6

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

        return code.toString().take(LENGTH)
    }

    /**
     * A code as somebody typed it, reduced to the form that is actually stored.
     *
     * Crockford base32 leaves I, L, O and U out of its alphabet precisely because people confuse
     * them with 1 and 0, and it says to fold them back when reading a code in. Separators go too,
     * so "abc-123", "ABC 123" and "abcl23" all arrive at the same six characters.
     *
     * Anything else is kept rather than dropped. Silently discarding a stray character would turn a
     * seven-character typo into a valid six-character code belonging to somebody else, and add a
     * stranger instead of a friend.
     */
    fun normalize(input: String): String = buildString {
        for (c in input.uppercase()) {
            when (c) {
                ' ', '-', '_' -> {}
                'O' -> append('0')
                'I', 'L' -> append('1')
                else -> append(c)
            }
        }
    }

    /** True when a typed code could name somebody: the right length, all from the alphabet. */
    fun isValid(input: String): Boolean = normalize(input).let { normalized ->
        normalized.length == LENGTH && normalized.all { it in CROCKFORD_BASE32 }
    }
}
