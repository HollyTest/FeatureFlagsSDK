package com.example.flags_android.client

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.flags_core.clientconfiguration.FlagSource
import com.example.flags_core.model.RefreshResult
import com.example.flags_core.model.RefreshStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AndroidFlagClientTest {

    @Test
    fun fromUrl_CreateClientSuccess() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val client = AndroidFlagClient.fromUrl(
            context = context,
            url = "https://example.com/test",
            fetchTimeoutMillis = 1000L
        )
        assertNotNull(client)
        assertNotNull(client.flagState)
        client.close()
    }

    @Test
    fun fromSource_CreateClientSuccess() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dummySource = FlagSource { """{"test_key": "test_value}""" }
        val client = AndroidFlagClient.fromSource(
            context = context,
            flagSource = dummySource,
            cacheKey = "test_cache_key"
        )
        assertNotNull(client)
        client.close()
    }

    @Test
    fun refreshAndEvaluate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        val configJson = """
            {
                "version": 10,
                "flags": {
                    "feature_a": {
                        "enabled": true,
                        "rollout": 100
                    }
                }
            }
        """.trimIndent()
        
        val testSource = FlagSource { configJson }
        
        val client = AndroidFlagClient.fromSource(
            context = context,
            flagSource = testSource,
            cacheKey = "test_cache_key"
        )
        
        val latch = CountDownLatch(1)
        var refreshResult: RefreshResult? = null
        
        client.refresh { result ->
            refreshResult = result
            latch.countDown()
        }
        
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertNotNull(refreshResult)
        
        assertTrue(refreshResult!!.status == RefreshStatus.UPDATE || refreshResult.status == RefreshStatus.NO_CHANGE)
        
        val isEnabled = client.isEnabled("feature_a", "user_1")
        assertTrue(isEnabled)
        
        val isNonExistedFlagEnabled = client.isEnabled("feature_b", "user_1")
        assertFalse(isNonExistedFlagEnabled)
        
        client.close()
    }

    @Test
    fun refresh_ClientClosedReturnsClientClosed() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val client = AndroidFlagClient.fromSource(
            context = context,
            flagSource = { """{"test_key": "test_value}""" },
            cacheKey = "test_key"
        )
        
        client.close()
        
        val latch = CountDownLatch(1)
        var refreshResult: RefreshResult? = null
        
        client.refresh { result ->
            refreshResult = result
            latch.countDown()
        }
        
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(RefreshStatus.CLOSED, refreshResult?.status)
    }
}
