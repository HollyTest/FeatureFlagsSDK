package com.example.flags_core

import java.security.MessageDigest

internal object Bucketing {

    private const val HASH_PREFIX_BYTE_COUNT = 4
    private const val BITS_PER_BYTE = 8
    private const val BYTE_MASK = 0xFFL

    fun bucket(flagName: String, userKey: String): Int {
        val hash = MessageDigest.getInstance(SHA_256_HASH_TYPE)
            .digest("$flagName:$userKey".toByteArray(Charsets.UTF_8))

        var prefix = 0L
        for (i in 0 until HASH_PREFIX_BYTE_COUNT) {
            prefix = (prefix shl BITS_PER_BYTE) or (hash[i].toLong() and BYTE_MASK)
        }

        return (prefix % ROLLOUT_MAX_VALUE).toInt()
    }
}
