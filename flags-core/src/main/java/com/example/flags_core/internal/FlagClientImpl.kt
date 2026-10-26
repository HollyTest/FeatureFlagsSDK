package com.example.flags_core.internal

import com.example.flags_core.clientbuilder.FlagClient
import com.example.flags_core.model.ConfigState
import com.example.flags_core.model.EvaluatedFlag
import com.example.flags_core.model.EvaluationResult
import com.example.flags_core.model.RefreshCallback
import com.example.flags_core.model.RefreshError
import com.example.flags_core.model.RefreshResult
import com.example.flags_core.model.RefreshStatus
import com.example.flags_core.clientconfiguration.ConfigSource
import com.example.flags_core.clientconfiguration.FlagSource
import com.example.flags_core.clientconfiguration.CachedConfig
import com.example.flags_core.clientconfiguration.FlagConfigStore
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

internal class FlagClientImpl(
    private val source: FlagSource,
    private val fetchTimeoutMillis: Long,
    private val validityDurationMillis: Long,
    private val store: FlagConfigStore,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val currentMillisFetch: () -> Long = System::currentTimeMillis
): FlagClient {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutex = Mutex()
    private val isClientClosed = AtomicBoolean(false)
    private val savedFlagConfig = AtomicReference<SavedFlagConfig?>(null)
    private val mutableConfigStateFlow = MutableStateFlow(ConfigState())
    
    override val configStateFlow: StateFlow<ConfigState> = mutableConfigStateFlow.asStateFlow()

    override fun isEnabled(flagName: String, userKey: String, defaultValue: Boolean) = evaluate(flagName, userKey, defaultValue).enabled

    override fun evaluate(flagName: String, userKey: String, defaultValue: Boolean): EvaluatedFlag {
        // EvaluationResult.CLIENT_CLOSED
        if (isClientClosed.get()) return EvaluatedFlag(defaultValue, EvaluationResult.CLIENT_CLOSED)

        // EvaluationResult.INVALID_USER
        if (userKey.length > USER_KEY_MAX_LENGTH || userKey.isBlank()) return EvaluatedFlag(defaultValue,
            EvaluationResult.INVALID_USER)

        // EvaluationResult.CONFIG_MISSING
        val config = savedFlagConfig.get() ?: return EvaluatedFlag(defaultValue, EvaluationResult.CONFIG_MISSING)

        // EvaluationResult.CONFIG_EXPIRED
        if ((currentMillisFetch() - config.savedAtMillis) !in 0..validityDurationMillis ) return EvaluatedFlag(defaultValue, EvaluationResult.CONFIG_EXPIRED)

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

    override fun refresh(callBack: RefreshCallback) {
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
         if (isClientClosed.get()) return RefreshResult(RefreshStatus.CLOSED)

         // Fetch ongoing
         if (!mutex.tryLock()) return RefreshResult(RefreshStatus.IN_PROGRESS)

         mutableConfigStateFlow.update{ it.copy(isRefreshing = true, lastError = null)}

         try {
             fetchConfigCache()
             val cachedConfig = savedFlagConfig.get()?.flagConfig

             val rawJson = withTimeout(fetchTimeoutMillis.milliseconds) {
                 source.fetchFlagConfig()
             }
             val fetchedConfig = try {
                 FlagConfigParser.parse(rawJson)
             } catch (_: Exception) {
                 return setError(RefreshError.INVALID_CONFIG)
             }

             if (cachedConfig != null && cachedConfig.version > fetchedConfig.version)
                 return RefreshResult(RefreshStatus.OLDER_VERSION_IGNORED)

             if (cachedConfig != null && cachedConfig.version == fetchedConfig.version && cachedConfig != fetchedConfig)
                 return setError(RefreshError.INVALID_CONFIG)

             currentCoroutineContext().ensureActive()
             val currentMillis = currentMillisFetch()
             savedFlagConfig.set(SavedFlagConfig(fetchedConfig, currentMillis))
             val cacheError = try {
                 store.write(CachedConfig(rawJson, currentMillis))
                 null
             } catch (cancelled: CancellationException) {
                 throw cancelled
             } catch (_: Exception) {
                 RefreshError.CACHE_WRITE
             }

             mutableConfigStateFlow.update { it.copy(
                 version = fetchedConfig.version,
                 configSource = ConfigSource.REMOTE,
                 lastError = cacheError
             ) }
             return RefreshResult(
                 status = if (cachedConfig == fetchedConfig) RefreshStatus.NO_CHANGE else RefreshStatus.UPDATE,
                 error = cacheError
             )
         } catch (_: TimeoutCancellationException) {
             return setError(RefreshError.TIMEOUT)
         } catch (cancelled: CancellationException) {
             throw cancelled
         } catch (_: Exception) {
             return setError(RefreshError.NETWORK)
         } finally {
             mutableConfigStateFlow.update { it.copy(isRefreshing = false) }
             mutex.unlock()
         }
    }

    private fun fetchConfigCache() {
        if (savedFlagConfig.get() != null) return
        try {
            val cache = store.read() ?: return
            val cachedConfig = FlagConfigParser.parse(cache.rawConfigJson)
            savedFlagConfig.set(SavedFlagConfig(cachedConfig, cache.savedAtMillis))
            mutableConfigStateFlow.value = ConfigState(
                version = cachedConfig.version,
                configSource = ConfigSource.CACHE,
                isRefreshing = false
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableConfigStateFlow.update { it.copy(lastError = RefreshError.CACHE_READ) }
        }
    }

    private fun setError(error: RefreshError): RefreshResult {
        mutableConfigStateFlow.update { it.copy(lastError = error) }
        return RefreshResult(RefreshStatus.FAILED, error)
    }

    override fun close() {
        if (!isClientClosed.compareAndSet(false, true)) return
        scope.cancel()
        savedFlagConfig.set(null)
    }
}
