package com.example.flags_android.internal

import java.io.IOException
import java.io.Reader

private const val CONFIG_READER_BUFFER_SIZE = 1024 * 2

internal fun Reader.readBoundedText(limit: Int): String {
    val builder = StringBuilder()
    val buffer = CharArray(CONFIG_READER_BUFFER_SIZE)

    while (true) {
        val count = read(buffer)
        if (count < 0) return builder.toString()
        if (builder.length + count > limit) throw IOException(ConfigError.EXCEEDED_LIMIT.errorMsg)
        builder.appendRange(buffer, 0, count)
    }
}
