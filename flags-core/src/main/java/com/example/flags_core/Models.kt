package com.example.flags_core


/* ------------------- Evaluation ------------------- */
enum class EvaluationResult {
    BUCKET_INCLUDED,
    BUCKET_NOT_INCLUDED,
    FLAG_DISABLED,
    FLAG_MISSING,
    CONFIG_MISSING,
    CONFIG_EXPIRED,
    INVALID_USER,
    CLIENT_CLOSED
}

data class EvaluatedFlag(
    val enabled: Boolean,
    val evaluationResult: EvaluationResult,
    val bucketIndex: Int? = null
)



/* ------------------- Refresh ------------------- */
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



/* ------------------- Flag Source ------------------- */
enum class ConfigSource {
    DEFAULT,
    CACHE,
    REMOTE
}

fun interface FlagSource {
    suspend fun fetchFlagConfig(): String
}



/* ------------------- Flag State ------------------- */

data class FlagState(
    val version: Long = 0L,
    val configSource: ConfigSource = ConfigSource.DEFAULT,
    val isRefreshing: Boolean = false,
    val lastError: RefreshError? = null
)



/* ------------------- Store in Memory ------------------- */
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



/* ------------------- Client Config Options ------------------- */
data class FlagConfigOptions @JvmOverloads constructor(
    val fetchTimeoutMillis: Long = TIMEOUT_DEFAULT_MILLIS,
    val validityDurationMillis: Long = DURATION_DEFAULT_MILLIS,
) {
    init {
        require(fetchTimeoutMillis in TIMEOUT_MIN_MILLIS..TIMEOUT_MAX_MILLIS)
        require(validityDurationMillis in DURATION_MIN_MILLIS..DURATION_MAX_MILLIS)
    }
}

