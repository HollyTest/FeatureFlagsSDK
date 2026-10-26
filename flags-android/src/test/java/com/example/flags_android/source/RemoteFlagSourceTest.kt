package com.example.flags_android.source

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteFlagSourceTest {

    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun getInstanceWillCreateNonNullFlagSource() {
        val source = RemoteFlagSource.getInstance(
            timeoutMillis = 5000L,
            endpoint = "https://example.com/test"
        )
        assertNotNull(source)
    }

    @Test(expected = IllegalArgumentException::class)
    fun getInstanceThrowErrorOnHttpUrl() {
        RemoteFlagSource.getInstance(5000L, "http://example.com/test")
    }

    @Test(expected = IllegalArgumentException::class)
    fun getInstanceThrowErrorOnUrlWithFragment() {
        RemoteFlagSource.getInstance(5000L, "https://example.com/test#test")
    }

    @Test
    fun fetchFlagConfigReturnsExpectedConfigOnSuccess() = runTest {
        val expectedConfig = """{"flags": {"feature_a": {"enabled": true, "rollout": 100}}, "version": 1}"""
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(expectedConfig))

        val okHttpClient = OkHttpClient.Builder().build()
        val source = RemoteFlagSource(mockWebServer.url("/test"), okHttpClient)

        val config = source.fetchFlagConfig()
        assertEquals(expectedConfig, config)
    }

    @Test
    fun fetchFlagConfigThrowsIOExceptionOnFailureResponse() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val okHttpClient = OkHttpClient.Builder().build()
        val source = RemoteFlagSource(mockWebServer.url("/test"), okHttpClient)

        var thrownException: Exception? = null
        try {
            source.fetchFlagConfig()
        } catch (e: Exception) {
            thrownException = e
        }

        assertNotNull(thrownException)
        assertTrue(thrownException is IOException)
    }
}
