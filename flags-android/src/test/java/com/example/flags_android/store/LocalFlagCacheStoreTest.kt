package com.example.flags_android.store

import com.example.flags_core.clientconfiguration.CachedConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalFlagCacheStoreTest {

    @get:Rule
    val mockDirectory = TemporaryFolder()

    @Test
    fun readNonExistentFileReturnsNull() {
        val file = File(mockDirectory.root, "test.json")
        val store = LocalFlagCacheStore(file)
        
        assertNull(store.read())
    }

    @Test
    fun writeAndReadReturnsSavedConfig() {
        val file = File(mockDirectory.root, "test.json")
        val store = LocalFlagCacheStore(file)
        
        val config = CachedConfig("""{"test_key":"test_value"}""", 12345L)
        store.write(config)
        
        val readConfig = store.read()
        assertEquals(config.rawConfigJson, readConfig?.rawConfigJson)
        assertEquals(config.savedAtMillis, readConfig?.savedAtMillis)
    }
}
