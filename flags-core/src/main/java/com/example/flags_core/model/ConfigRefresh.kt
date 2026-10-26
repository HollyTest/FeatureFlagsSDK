package com.example.flags_core.model

enum class RefreshStatus {
    UPDATE,
    NO_CHANGE,
    OLDER_VERSION_IGNORED,
    IN_PROGRESS,
    FAILED,
    CLOSED
}

enum class RefreshError {
    TIMEOUT,
    NETWORK,
    INVALID_CONFIG,
    CACHE_READ,
    CACHE_WRITE
}

data class RefreshResult(
    val status: RefreshStatus,
    val error: RefreshError? = null
)

fun interface RefreshCallback {
    fun onComplete(result: RefreshResult)
}
