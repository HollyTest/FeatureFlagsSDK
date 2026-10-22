package com.example.featureflagssdk.server

import com.example.featureflagssdk.models.MockScenes
import com.example.flags_core.FlagSource
import kotlinx.coroutines.delay
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

class SampleFlagSource: FlagSource {

    @Volatile var scene = MockScenes.NORMAL
    @Volatile var bucketPercentage = 50
    @Volatile var globalEnabled = true
    private val version = AtomicLong(1L)

    override suspend fun fetchFlagConfig(): String {
        val scenario = scene
        val percentage = bucketPercentage
        val enabled = globalEnabled
        
        delay((if (scenario == MockScenes.NETWORK_SLOW) 5_000L else 500L).milliseconds)

        if (scenario == MockScenes.NETWORK_OFFLINE) throw IOException("Network is offline")
        if (scenario == MockScenes.CONFIG_BROKEN) return "Config is broken"

        val newVersion = if(scenario == MockScenes.CONFIG_OLD_VERSION) 1L else version.incrementAndGet()

        return """{
            "version": $newVersion,
            "flags": {
                "new_feature": {
                    "enabled": $enabled,
                    "rollout": $percentage
                }
            }
        }
        """.trimIndent()
    }
}
