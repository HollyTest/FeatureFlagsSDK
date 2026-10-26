package com.example.flags_core.clientconfiguration

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Test

class MemoryFlagConfigStoreTest {

    @Test
    fun defaultCacheNull() {
        val store = MemoryFlagConfigStore()
        assertNull(store.read())
    }

    @Test
    fun writeAndReadCache() {
        val store = MemoryFlagConfigStore()
        val config = CachedConfig("""{"damn_market": "so tough to find a job"}""", 1000L)

        store.write(config)
        
        val savedConfig = store.read()
        assertEquals(config, savedConfig)
    }
}
