package com.example.flags_core.internal

import com.example.flags_core.clientconfiguration.CachedConfig
import com.example.flags_core.clientconfiguration.ConfigSource
import com.example.flags_core.clientconfiguration.FlagConfigStore
import com.example.flags_core.clientconfiguration.FlagSource
import com.example.flags_core.model.EvaluationResult
import com.example.flags_core.model.RefreshError
import com.example.flags_core.model.RefreshStatus
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class FlagClientTest {

    private lateinit var mockSource: MockFlagSource
    private lateinit var mockStore: MockFlagConfigStore
    private var currentTime = 1000L

    @Before
    fun setup() {
        mockSource = MockFlagSource()
        mockStore = MockFlagConfigStore()
        currentTime = 1000L
    }

    private fun buildFlagClient(
        fetchTimeoutMillis: Long = 3000L,
        validityDurationMillis: Long = 24 * 60 * 60 * 1000L
    ): FlagClientImpl {
        return FlagClientImpl(
            source = mockSource,
            fetchTimeoutMillis = fetchTimeoutMillis,
            validityDurationMillis = validityDurationMillis,
            store = mockStore,
            dispatcher = StandardTestDispatcher(),
            currentMillisFetch = { currentTime }
        )
    }

    // --- Evaluation Tests ---

    @Test
    fun evaluate_ClientClosedReturnsClientClosed() = runTest {
        val client = buildFlagClient()
        client.close()
        
        val result = client.evaluate("flag", "user")
        assertEquals(EvaluationResult.CLIENT_CLOSED, result.evaluationResult)
        assertFalse(result.enabled)
    }

    @Test
    fun evaluate_InvalidUserKeyReturnsInvalidUser() = runTest {
        val client = buildFlagClient()
        
        val resultBlank = client.evaluate("flag", " ")
        assertEquals(EvaluationResult.INVALID_USER, resultBlank.evaluationResult)
        assertFalse(resultBlank.enabled)
        
        val longUser = "a".repeat(257)
        val resultLong = client.evaluate("flag", longUser)
        assertEquals(EvaluationResult.INVALID_USER, resultLong.evaluationResult)
        assertFalse(resultLong.enabled)
    }

    @Test
    fun evaluate_NoConfigReturnsConfigMissing() = runTest {
        val client = buildFlagClient()
        
        val result = client.evaluate("flag", "user")
        assertEquals(EvaluationResult.CONFIG_MISSING, result.evaluationResult)
        assertFalse(result.enabled)
    }

    @Test
    fun evaluate_ExpiredConfigReturnsConfigExpired() = runTest {
        val client = buildFlagClient(validityDurationMillis = 5000L)
        
        mockSource.responseJson = createConfigJson(1, "flag", true, 100)
        client.refreshConfig()
        
        currentTime += 6000L
        
        val result = client.evaluate("flag", "user")
        assertEquals(EvaluationResult.CONFIG_EXPIRED, result.evaluationResult)
        assertFalse(result.enabled)
    }

    @Test
    fun evaluate_MissingFlagReturnsFlagMissing() = runTest {
        val client = buildFlagClient()
        mockSource.responseJson = createConfigJson(1, "flag1", true, 100)
        client.refreshConfig()
        
        val result = client.evaluate("flag2", "user")
        assertEquals(EvaluationResult.FLAG_MISSING, result.evaluationResult)
        assertFalse(result.enabled)
    }

    @Test
    fun evaluate_DisabledFlagReturnsFlagDisabled() = runTest {
        val client = buildFlagClient()
        mockSource.responseJson = createConfigJson(1, "flag", false, 100)
        client.refreshConfig()
        
        val result = client.evaluate("flag", "user")
        assertEquals(EvaluationResult.FLAG_DISABLED, result.evaluationResult)
        assertFalse(result.enabled)
    }

    @Test
    fun evaluate_BucketIncludedReturnsIncluded() = runTest {
        val client = buildFlagClient()
        mockSource.responseJson = createConfigJson(1, "flag", true, 100)
        client.refreshConfig()
        
        val result = client.evaluate("flag", "user")
        assertEquals(EvaluationResult.BUCKET_INCLUDED, result.evaluationResult)
        assertTrue(result.enabled)
    }

    @Test
    fun evaluate_BucketNotIncludedReturnsNotIncluded() = runTest {
        val client = buildFlagClient()
        // Rollout 0 means never included
        mockSource.responseJson = createConfigJson(1, "flag", true, 0)
        client.refreshConfig()
        
        val result = client.evaluate("flag", "user")
        assertEquals(EvaluationResult.BUCKET_NOT_INCLUDED, result.evaluationResult)
        assertFalse(result.enabled)
    }

    // --- Refresh Tests ---

    @Test
    fun refresh_ClientClosedReturnsClosed() = runTest {
        val client = buildFlagClient()
        client.close()
        
        val result = client.refreshConfig()
        assertEquals(RefreshStatus.CLOSED, result.status)
    }

    @Test
    fun refresh_UpdateStateAndFlow() = runTest {
        val client = buildFlagClient()
        mockSource.responseJson = createConfigJson(10, "flag", true, 50)
        
        val result = client.refreshConfig()
        assertEquals(RefreshStatus.UPDATE, result.status)
        assertNull(result.error)
        
        val state = client.configStateFlow.value
        assertEquals(10L, state.version)
        assertEquals(ConfigSource.REMOTE, state.configSource)
        assertFalse(state.isRefreshing)
        assertNull(state.lastError)
    }

    @Test
    fun refresh_TimeoutReturnsTimeoutError() = runTest {
        val client = buildFlagClient(fetchTimeoutMillis = 100L)
        mockSource.delayMillis = 200L
        
        val result = client.refreshConfig()
        assertEquals(RefreshStatus.FAILED, result.status)
        assertEquals(RefreshError.TIMEOUT, result.error)
        
        val state = client.configStateFlow.value
        assertEquals(RefreshError.TIMEOUT, state.lastError)
    }

    @Test
    fun refresh_NetworkErrorReturnsNetworkError() = runTest {
        val client = buildFlagClient()
        mockSource.throwException = IOException("Network down")
        
        val result = client.refreshConfig()
        assertEquals(RefreshStatus.FAILED, result.status)
        assertEquals(RefreshError.NETWORK, result.error)
    }

    @Test
    fun refresh_InvalidJsonReturnsInvalidConfigError() = runTest {
        val client = buildFlagClient()
        mockSource.responseJson = "{ invalid_json }"
        
        val result = client.refreshConfig()
        assertEquals(RefreshStatus.FAILED, result.status)
        assertEquals(RefreshError.INVALID_CONFIG, result.error)
    }

    @Test
    fun refresh_OlderVersionReturnsOldVersionIgnored() = runTest {
        val client = buildFlagClient()
        
        mockSource.responseJson = createConfigJson(10, "flag", true, 50)
        client.refreshConfig()
        
        mockSource.responseJson = createConfigJson(9, "flag", true, 50)
        val result = client.refreshConfig()
        
        assertEquals(RefreshStatus.OLDER_VERSION_IGNORED, result.status)
        assertEquals(10L, client.configStateFlow.value.version)
    }

    @Test
    fun refresh_SameVersionReturnsNoChange() = runTest {
        val client = buildFlagClient()
        
        mockSource.responseJson = createConfigJson(10, "flag", true, 50)
        client.refreshConfig()
        
        val result = client.refreshConfig()
        
        assertEquals(RefreshStatus.NO_CHANGE, result.status)
    }

    @Test
    fun refresh_ReadCacheSuccessThenUseCache() = runTest {
        val client = buildFlagClient()
        
        val cachedJson = createConfigJson(5, "flag", true, 100)
        mockStore.write(CachedConfig(cachedJson, currentTime))
        
        mockSource.throwException = IOException()
        
        client.refreshConfig()
        
        val eval = client.evaluate("flag", "user")
        assertEquals(EvaluationResult.BUCKET_INCLUDED, eval.evaluationResult)
        
        assertEquals(5L, client.configStateFlow.value.version)
        assertEquals(RefreshError.NETWORK, client.configStateFlow.value.lastError)
    }

    @Test
    fun refresh_CacheWriteErrorThenThrowsErrorButStillReturnsUpdate() = runTest {
        val client = buildFlagClient()
        mockSource.responseJson = createConfigJson(1, "flag", true, 50)
        mockStore.throwOnWrite = RuntimeException("Disk full")
        
        val result = client.refreshConfig()
        
        assertEquals(RefreshStatus.UPDATE, result.status)
        assertEquals(RefreshError.CACHE_WRITE, result.error)
        assertEquals(RefreshError.CACHE_WRITE, client.configStateFlow.value.lastError)
    }

    @Test
    fun refresh_FetchReturnsInProgress() = runTest {
        val client = buildFlagClient()
        mockSource.responseJson = createConfigJson(1, "flag", true, 50)
        mockSource.delayMillis = 1000L
        
        val job1 = async { client.refreshConfig() }
        
        delay(100.milliseconds)
        
        val result2 = client.refreshConfig()
        assertEquals(RefreshStatus.IN_PROGRESS, result2.status)
        
        val result1 = job1.await()
        assertEquals(RefreshStatus.UPDATE, result1.status)
    }

    // --- create json ---

    private fun createConfigJson(version: Long, flagName: String, enabled: Boolean, rollout: Int): String {
        return """
            {
                "version": $version,
                "flags": {
                    "$flagName": {
                        "enabled": $enabled,
                        "rollout": $rollout
                    }
                }
            }
        """.trimIndent()
    }
}

class MockFlagSource : FlagSource {
    var responseJson: String = ""
    var throwException: Exception? = null
    var delayMillis: Long = 0

    override suspend fun fetchFlagConfig(): String {
        if (delayMillis > 0) delay(delayMillis.milliseconds)
        throwException?.let { throw it }
        return responseJson
    }
}

class MockFlagConfigStore : FlagConfigStore {
    var cache: CachedConfig? = null
    var throwOnRead: Exception? = null
    var throwOnWrite: Exception? = null

    override fun read(): CachedConfig? {
        throwOnRead?.let { throw it }
        return cache
    }

    override fun write(cachedConfig: CachedConfig) {
        throwOnWrite?.let { throw it }
        this.cache = cachedConfig
    }
}
