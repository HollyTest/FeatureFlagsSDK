package com.example.flags_android

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Trace.beginSection
import android.os.Trace.endSection
import com.example.flags_core.EvaluatedFlag
import com.example.flags_core.FlagClient
import com.example.flags_core.FlagConfigOptions
import com.example.flags_core.FlagSource
import com.example.flags_core.RefreshCallback
import com.example.flags_core.RefreshResult
import com.example.flags_core.RefreshStatus
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

class AndroidFlagClient private constructor(private val flagClient: FlagClient): AutoCloseable {
    private val mainLooperHandler = Handler(Looper.getMainLooper())
    private val isClientClosed = AtomicBoolean(false)
    val flagState = flagClient.flagStateFlow

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
            options: FlagConfigOptions = FlagConfigOptions()
        ): AndroidFlagClient {
            val source = RemoteFlagSource.getInstance(options.fetchTimeoutMillis, url)
            val hashCacheKey = MessageDigest
                .getInstance(SHA_256_HASH_TYPE)
                .digest(url.toByteArray(Charsets.UTF_8))
                .take(HASH_PREFIX_BYTE_COUNT)
                .joinToString("") {HASH_FORMATTER.format(it)}

            return fromSource(
                context = context,
                flagSource = source,
                cacheKey = hashCacheKey,
                options = options
            )
        }

        // Common flag client
        @JvmStatic @JvmOverloads
        fun fromSource(
            context: Context,
            flagSource: FlagSource,
            cacheKey: String,
            options: FlagConfigOptions = FlagConfigOptions()
        ): AndroidFlagClient {
            require(cacheKey.matches(cacheKeyPattern))

            val file = File(
                context.applicationContext.filesDir,
                "$CONFIG_CACHE_FILE_KEY_PREFIX-$cacheKey.$CONFIG_CACHE_FILE_KEY_SUFFIX"
            )

            return AndroidFlagClient(
                FlagClient(
                    source = flagSource,
                    options = options,
                    store = LocalFlagCacheStore(file)
                )
            )
        }
    }
}
