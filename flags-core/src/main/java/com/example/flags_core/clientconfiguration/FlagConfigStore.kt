package com.example.flags_core.clientconfiguration

data class CachedConfig(
    val rawConfigJson: String,
    val savedAtMillis: Long
)

interface FlagConfigStore {
    fun read(): CachedConfig?
    fun write(cachedConfig: CachedConfig)
}

class MemoryFlagConfigStore: FlagConfigStore {
    @Volatile private var cache: CachedConfig? = null

    override fun read(): CachedConfig? = cache
    override fun write(cachedConfig: CachedConfig) { this.cache = cachedConfig }
}
