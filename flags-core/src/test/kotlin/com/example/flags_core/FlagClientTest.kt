package com.example.flags_core

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlagClientTest {

    @Test
    fun loadsConfigAndEvaluatesFlag() = runTest {
        val client = FlagClient({ """
            {
                "version":1,
                "flags":{
                    "feature_a":{
                        "enabled":true,
                        "rollout":100
                    }
                }
            }
        """ })
        client.refreshConfig()
        assertTrue(client.isEnabled("feature_a", "user_123"))
    }

    @Test
    fun evaluateRefreshResultWhenNoConfig() = runTest {
        val client = FlagClient({ """{"version":1,"flags":{}}""" })
        val evaluated = client.evaluate("missing_flag", "user-1", defaultValue = true)
        assertTrue(evaluated.enabled)
        assertEquals(EvaluationResult.CONFIG_MISSING, evaluated.evaluationResult)
    }

    @Test
    fun respectRolloutPercentage() = runTest {
        // user-42 gets bucket 57 for feature_x
        val client = FlagClient(FlagSource { """{"version":1,"flags":{"feature_x":{"enabled":true,"rollout":80}}}""" })
        client.refreshConfig()
        assertTrue(client.isEnabled("feature_x", "demo-user-42"))

        val client2 = FlagClient(FlagSource { """{"version":1,"flags":{"feature_x":{"enabled":true,"rollout":30}}}""" })
        client2.refreshConfig()
        assertFalse(client2.isEnabled("feature_x", "demo-user-42"))
    }

    @Test
    fun clientClosedReturnsClientClosed() = runTest {
        val client = FlagClient(FlagSource { """{"version":1,"flags":{}}""" })
        client.close()
        val evaluated = client.evaluate("feature_x", "user-1")
        assertEquals(EvaluationResult.CLIENT_CLOSED, evaluated.evaluationResult)
    }

    @Test
    fun optionsValidation() {
        val options = FlagConfigOptions(fetchTimeoutMillis = 2000L, validityDurationMillis = 60_000L)
        assertEquals(2000L, options.fetchTimeoutMillis)
        assertEquals(60_000L, options.validityDurationMillis)
    }
}
