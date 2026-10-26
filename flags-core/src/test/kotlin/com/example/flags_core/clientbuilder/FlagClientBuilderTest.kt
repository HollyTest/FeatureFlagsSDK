package com.example.flags_core.clientbuilder

import com.example.flags_core.clientconfiguration.FlagSource
import com.example.flags_core.clientconfiguration.MemoryFlagConfigStore
import com.example.flags_core.internal.CONFIG_VALIDITY_DURATION_MAX_MILLIS
import com.example.flags_core.internal.CONFIG_VALIDITY_DURATION_MIN_MILLIS
import com.example.flags_core.internal.FlagClientImpl
import com.example.flags_core.internal.REMOTE_CONFIG_FETCH_TIMEOUT_MAX_MILLIS
import com.example.flags_core.internal.REMOTE_CONFIG_FETCH_TIMEOUT_MIN_MILLIS
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.lang.IllegalArgumentException

class FlagClientBuilderTest {

    @Test
    fun builderReturnFlagClientImpl() {
        val builder = FlagClient.builder(testFlagSource)
        
        val client = builder.build()
        assertNotNull(client)
        assert(client is FlagClientImpl)
    }

    @Test
    fun builderWithOptionsAndStore() {
        val client = FlagClient.builder(testFlagSource)
            .fetchTimeoutMillis(5000L)
            .validityDurationMillis(10000L)
            .store(MemoryFlagConfigStore())
            .build()
            
        assertNotNull(client)
    }

    @Test(expected = IllegalArgumentException::class)
    fun throwsOnFetchTimeoutSmallerThanMin() {
        FlagClient.builder(testFlagSource).fetchTimeoutMillis(REMOTE_CONFIG_FETCH_TIMEOUT_MIN_MILLIS - 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun throwsOnFetchTimeoutLargerThanMax() {
        FlagClient.builder(testFlagSource).fetchTimeoutMillis(REMOTE_CONFIG_FETCH_TIMEOUT_MAX_MILLIS + 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun throwsOnValidityDurationSmallerThanMin() {
        FlagClient.builder(testFlagSource).validityDurationMillis(CONFIG_VALIDITY_DURATION_MIN_MILLIS - 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun throwsOnValidityDurationLargerThanMax() {
        FlagClient.builder(testFlagSource).validityDurationMillis(CONFIG_VALIDITY_DURATION_MAX_MILLIS + 1)
    }

    companion object {
        private val testFlagSource = FlagSource { """{"test_json": "test_value"}""" }
    }
}
