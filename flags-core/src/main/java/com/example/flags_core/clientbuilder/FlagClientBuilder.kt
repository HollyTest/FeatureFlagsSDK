package com.example.flags_core.clientbuilder

import com.example.flags_core.internal.CONFIG_VALIDITY_DURATION_DEFAULT_MILLIS
import com.example.flags_core.internal.CONFIG_VALIDITY_DURATION_MAX_MILLIS
import com.example.flags_core.internal.CONFIG_VALIDITY_DURATION_MIN_MILLIS
import com.example.flags_core.internal.FlagClientImpl
import com.example.flags_core.internal.REMOTE_CONFIG_FETCH_TIMEOUT_DEFAULT_MILLIS
import com.example.flags_core.internal.REMOTE_CONFIG_FETCH_TIMEOUT_MAX_MILLIS
import com.example.flags_core.internal.REMOTE_CONFIG_FETCH_TIMEOUT_MIN_MILLIS
import com.example.flags_core.clientconfiguration.FlagSource
import com.example.flags_core.clientconfiguration.FlagConfigStore
import com.example.flags_core.clientconfiguration.MemoryFlagConfigStore

class FlagClientBuilder internal constructor(
    private val source: FlagSource
) {

    private var fetchTimeoutMillis: Long = REMOTE_CONFIG_FETCH_TIMEOUT_DEFAULT_MILLIS

    private var validityDurationMillis: Long = CONFIG_VALIDITY_DURATION_DEFAULT_MILLIS

    private var myStore: FlagConfigStore? = null

    fun fetchTimeoutMillis(value: Long) = apply { 
        require(value in REMOTE_CONFIG_FETCH_TIMEOUT_MIN_MILLIS..REMOTE_CONFIG_FETCH_TIMEOUT_MAX_MILLIS)
        fetchTimeoutMillis = value 
    }

    fun validityDurationMillis(value: Long) = apply { 
        require(value in CONFIG_VALIDITY_DURATION_MIN_MILLIS..CONFIG_VALIDITY_DURATION_MAX_MILLIS)
        validityDurationMillis = value 
    }

    fun store(value: FlagConfigStore) = apply { myStore = value }
    
    fun build(): FlagClient {

        return FlagClientImpl(
            source = source,
            fetchTimeoutMillis = fetchTimeoutMillis,
            validityDurationMillis = validityDurationMillis,
            store = myStore ?: MemoryFlagConfigStore()
        )
    }
}
