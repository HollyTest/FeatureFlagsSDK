package com.example.flags_core.clientconfiguration

enum class ConfigSource {
    DEFAULT,
    CACHE,
    REMOTE
}

fun interface FlagSource {
    suspend fun fetchFlagConfig(): String
}
