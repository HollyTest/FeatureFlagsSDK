package com.example.flags_core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

class FlagClient internal constructor(
    private val source: FlagSource,
    private val options: FlagConfigOptions,
    private val store: FlagConfigStore,
    dispatcher: CoroutineDispatcher,
    private val currentMillisFetch: () -> Long
): AutoCloseable {
    @JvmOverloads constructor(
        source: FlagSource,
        options: FlagConfigOptions = FlagConfigOptions(),
        store: FlagConfigStore = MemoryFlagConfigStore()
    ): this(
        source = source,
        options = options,
        store = store,
        dispatcher = Dispatchers.IO,
        currentMillisFetch = System::currentTimeMillis
    )

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutex = Mutex()
    private val isClientClosed = AtomicBoolean(false)
    private val savedFlagConfig = AtomicReference<SavedFlagConfig?>(null)
    private val mutableFlagStateFlow = MutableStateFlow(FlagState())
    val flagStateFlow: StateFlow<FlagState> = mutableFlagStateFlow.asStateFlow()

    fun isEnabled(flagName: String, userKey: String, defaultValue: Boolean = false) = evaluate(flagName, userKey, defaultValue).enabled

    fun evaluate(flagName: String, userKey: String, defaultValue: Boolean = false): EvaluatedFlag {
        // EvaluationResult.CLIENT_CLOSED
        if (isClientClosed.get()) return EvaluatedFlag(defaultValue, EvaluationResult.CLIENT_CLOSED)

        // EvaluationResult.INVALID_USER
        if (userKey.length > USER_KEY_MAX_LENGTH || userKey.isBlank()) return EvaluatedFlag(defaultValue,
            EvaluationResult.INVALID_USER)

        // EvaluationResult.CONFIG_MISSING
        val config = savedFlagConfig.get() ?: return EvaluatedFlag(defaultValue, EvaluationResult.CONFIG_MISSING)

        // EvaluationResult.CONFIG_EXPIRED
        if ((currentMillisFetch() - config.savedAtMillis) !in 0..options.validityDurationMillis ) return EvaluatedFlag(defaultValue, EvaluationResult.CONFIG_EXPIRED)

        // EvaluationResult.FLAG_MISSING
        val flag = config.flagConfig.flags[flagName] ?: return EvaluatedFlag(defaultValue, EvaluationResult.FLAG_MISSING)

        // EvaluationResult.FLAG_DISABLED
        if (!flag.enabled) return EvaluatedFlag(false, EvaluationResult.FLAG_DISABLED)

        // EvaluationResult.BUCKET_INCLUDED
        // EvaluationResult.BUCKET_NOT_INCLUDED
        val bucket = Bucketing.bucket(flagName, userKey)
        val included = bucket < flag.rollout
        return EvaluatedFlag(included, if (included) EvaluationResult.BUCKET_INCLUDED else EvaluationResult.BUCKET_NOT_INCLUDED, bucket)
    }

    fun refresh(callBack: RefreshCallback) {
        if (isClientClosed.get()) {
            callBack.onComplete(RefreshResult(RefreshStatus.CLOSED))
            return
        }

        scope.launch {
            if (!isClientClosed.get()) callBack.onComplete(refreshConfig())
        }
    }

    internal suspend fun refreshConfig(): RefreshResult {
         // Client closed
         // -- RefreshStatus.CLOSED
         if (isClientClosed.get()) return RefreshResult(RefreshStatus.CLOSED)

         // Fetch ongoing
         // -- RefreshStatus.IN_PROGRESS
         if (!mutex.tryLock()) return RefreshResult(RefreshStatus.IN_PROGRESS)

         mutableFlagStateFlow.update{ it.copy(isRefreshing = true, lastError = null)}

         // Fetch flag config
         // -- FAILED: RefreshError.TIMEOUT
         // -- FAILED: RefreshError.NETWORK
         try {

             // Read Cache
             // -- RefreshError.CACHE_READ
             fetchConfigCache()

             // Fetch from remote
             // -- FAILED: RefreshError.INVALID_CONFIG
             val rawJson = withTimeout(options.fetchTimeoutMillis.milliseconds) {
                 source.fetchFlagConfig()
             }
             val newConfig = try {
                 FlagConfigParser.parse(rawJson)
             } catch (_: Exception) {
                 return setError(RefreshError.INVALID_CONFIG)
             }

             val oldConfig = savedFlagConfig.get()?.flagConfig

             // Remote config evaluation
             // -- RefreshStatus.OLDER_VERSION_IGNORED
             if (oldConfig != null && oldConfig.version > newConfig.version)
                 return RefreshResult(RefreshStatus.OLDER_VERSION_IGNORED)

             // Remote config evaluation
             // -- FAILED: RefreshError.INVALID_CONFIG
             if (oldConfig != null && oldConfig.version == newConfig.version && oldConfig != newConfig)
                 return setError(RefreshError.INVALID_CONFIG)

             // Write to cache
             // -- RefreshError.CACHE_WRITE
             currentCoroutineContext().ensureActive()
             val currentMillis = currentMillisFetch()
             savedFlagConfig.set(SavedFlagConfig(newConfig, currentMillis))
             val cacheError = try {
                 store.write(CachedConfig(rawJson, currentMillis))
                 null
             } catch (cancelled: CancellationException) {
                 throw cancelled
             } catch (_: Exception) {
                 RefreshError.CACHE_WRITE
             }

             // Set final state
             // -- RefreshStatus.UPDATE
             // -- RefreshStatus.NO_CHANGE
             mutableFlagStateFlow.update { it.copy(
                 version = newConfig.version,
                 configSource = ConfigSource.REMOTE,
                 lastError = cacheError
             ) }
             return RefreshResult(
                 status = if (oldConfig == newConfig) RefreshStatus.NO_CHANGE else RefreshStatus.UPDATE,
                 error = cacheError
             )
         } catch (_: TimeoutCancellationException) {
             return setError(RefreshError.TIMEOUT)
         } catch (cancelled: CancellationException) {
             throw cancelled
         } catch (_: Exception) {
             return setError(RefreshError.NETWORK)
         } finally {
             mutableFlagStateFlow.update { it.copy(isRefreshing = false) }
             mutex.unlock()
         }
    }

    private fun fetchConfigCache() {
        if (savedFlagConfig.get() != null) return
        try {
            val cache = store.read() ?: return
            val cachedConfig = FlagConfigParser.parse(cache.rawConfigJson)
            savedFlagConfig.set(SavedFlagConfig(cachedConfig, cache.savedAtMillis))
            mutableFlagStateFlow.value = FlagState(
                version = cachedConfig.version,
                configSource = ConfigSource.CACHE,
                isRefreshing = false
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableFlagStateFlow.update { it.copy(lastError = RefreshError.CACHE_READ) }
        }
    }

    private fun setError(error: RefreshError): RefreshResult {
        mutableFlagStateFlow.update { it.copy(lastError = error) }
        return RefreshResult(RefreshStatus.FAILED, error)
    }

    override fun close() {
        if (!isClientClosed.compareAndSet(false, true)) return
        scope.cancel()
        savedFlagConfig.set(null)
    }
}
