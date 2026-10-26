package com.example.flags_android.source

import com.example.flags_android.internal.ConfigError
import com.example.flags_android.internal.REMOTE_CONFIG_READER_BOUNDED_LIMIT
import com.example.flags_android.internal.readBoundedText
import com.example.flags_core.clientconfiguration.FlagSource
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class RemoteFlagSource(
    private val url: HttpUrl,
    private val client: OkHttpClient
): FlagSource {

    override suspend fun fetchFlagConfig(): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url).get().build())
        continuation.invokeOnCancellation { call.cancel() }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val config = response.use {
                        if (!it.isSuccessful) throw IOException(ConfigError.REQUEST_FAILURE.errorMsg)

                        val body = it.body ?: throw IOException(ConfigError.MISSING.errorMsg)
                        body.charStream().use { reader -> reader.readBoundedText(REMOTE_CONFIG_READER_BOUNDED_LIMIT) }
                    }
                    continuation.resume(config)
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }
        })
    }

    companion object {
        fun getInstance(timeoutMillis: Long, endpoint: String): RemoteFlagSource {
            val url = endpoint.toHttpUrl()
            require(url.isHttps && url.username.isEmpty() && url.password.isEmpty() && url.fragment == null)

            val okHttpClient = OkHttpClient.Builder()
                .callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .build()
            return RemoteFlagSource(url, okHttpClient)
        }
    }
}
