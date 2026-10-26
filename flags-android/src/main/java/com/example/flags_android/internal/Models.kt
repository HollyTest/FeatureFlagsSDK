package com.example.flags_android.internal

internal enum class ConfigError(val errorMsg: String) {
    EXCEEDED_LIMIT("Configuration exceeds limit size"),
    REQUEST_FAILURE("Request failed"),
    MISSING("Config missing")
}
