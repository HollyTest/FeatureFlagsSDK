package com.example.flags_android.client

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Trace.beginSection
import android.os.Trace.endSection
import com.example.flags_core.model.EvaluatedFlag
import com.example.flags_core.clientbuilder.FlagClient
import com.example.flags_core.clientconfiguration.FlagSource
import com.example.flags_core.model.RefreshCallback
import com.example.flags_core.model.RefreshResult
import com.example.flags_core.model.RefreshStatus
import com.example.flags_android.source.RemoteFlagSource
import com.example.flags_android.store.LocalFlagCacheStore
import com.example.flags_android.internal.CONFIG_CACHE_FILE_KEY_PREFIX
import com.example.flags_android.internal.CONFIG_CACHE_FILE_KEY_SUFFIX
import com.example.flags_android.internal.CONFIG_CACHE_KEY_MAX_LENGTH
import com.example.flags_android.internal.CONFIG_CACHE_DIRECTORY_HASH_KEY_SHA_256
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

class AndroidFlagClient private constructor(private val flagClient: FlagClient): AutoCloseable {
    private val mainLooperHandler = Handler(Looper.getMainLooper())
    private val isClientClosed = AtomicBoolean(false)
    val flagState = flagClient.configStateFlow

    @JvmOverloads fun isEnabled(flagName: String, userKey: String, defaultValue: Boolean = false) = evaluate(flagName, userKey, defaultValue).enabled

    @JvmOverloads fun evaluate(flagName: String, userKey: String, defaultValue: Boolean = false): EvaluatedFlag {
        beginSection(FLAG_EVALUATION_TRACE)
        return try {
            flagClient.evaluate(flagName, userKey, defaultValue)
        } finally {
            endSection()
        }
    }

    fun refresh(callBack: RefreshCallback) {
        if (isClientClosed.get()) {
            mainLooperHandler.post { callBack.onComplete(RefreshResult(RefreshStatus.CLOSED)) }
            return
        }

        flagClient.refresh { result ->
            mainLooperHandler.post {
                if(!isClientClosed.get()) callBack.onComplete(result)
            }
        }
    }

    override fun close() {
        if (isClientClosed.compareAndSet(false, true)) flagClient.close()
    }

    companion object {
        private const val FLAG_EVALUATION_TRACE = "FeatureFlags Evaluation"

        private val cacheKeyPattern = Regex("[a-zA-Z0-9_-]{1,$CONFIG_CACHE_KEY_MAX_LENGTH}")

        private const val HASH_PREFIX_BYTE_COUNT = 8
        private const val HASH_FORMATTER = "%02x"

        // Flag client with source from remote
        @JvmStatic @JvmOverloads
        fun fromUrl(
            context: Context,
            url: String,
            fetchTimeoutMillis: Long = 3000L,
            validityDurationMillis: Long = 24 * 60 * 60 * 1000L
        ): AndroidFlagClient {
            val source = RemoteFlagSource.getInstance(fetchTimeoutMillis, url)
            val hashCacheKey = MessageDigest
                .getInstance(CONFIG_CACHE_DIRECTORY_HASH_KEY_SHA_256)
                .digest(url.toByteArray(Charsets.UTF_8))
                .take(HASH_PREFIX_BYTE_COUNT)
                .joinToString("") {HASH_FORMATTER.format(it)}

            return fromSource(
                context = context,
                flagSource = source,
                cacheKey = hashCacheKey,
                fetchTimeoutMillis = fetchTimeoutMillis,
                validityDurationMillis = validityDurationMillis
            )
        }

        // Common flag client
        @JvmOverloads
        @JvmStatic
        fun fromSource(
            context: Context,
            flagSource: FlagSource,
            cacheKey: String,
            fetchTimeoutMillis: Long = 3000L,
            validityDurationMillis: Long = 24 * 60 * 60 * 1000L
        ): AndroidFlagClient {
            require(cacheKey.matches(cacheKeyPattern))

            val file = File(
                context.applicationContext.filesDir,
                "$CONFIG_CACHE_FILE_KEY_PREFIX-$cacheKey.$CONFIG_CACHE_FILE_KEY_SUFFIX"
            )

            return AndroidFlagClient(
                FlagClient.builder(flagSource)
                    .fetchTimeoutMillis(fetchTimeoutMillis)
                    .validityDurationMillis(validityDurationMillis)
                    .store(LocalFlagCacheStore(file))
                    .build()
            )
        }
    }
}
