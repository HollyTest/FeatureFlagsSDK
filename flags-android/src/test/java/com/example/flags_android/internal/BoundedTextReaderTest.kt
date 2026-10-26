package com.example.flags_android.internal

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringReader
import java.io.IOException

class BoundedTextReaderTest {

    @Test
    fun readBoundedTextReturnsText() {
        val text = "test"
        val reader = StringReader(text)
        
        val result = reader.readBoundedText(100)
        assertEquals(text, result)
    }

    @Test(expected = IOException::class)
    fun readBoundedTextExceedsLimitThenThrowIOException() {
        val text = "test"
        val reader = StringReader(text)
        
        reader.readBoundedText(3)
    }
}
