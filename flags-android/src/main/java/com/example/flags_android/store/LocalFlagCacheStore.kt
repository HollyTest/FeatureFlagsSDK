package com.example.flags_android.store

import androidx.core.util.AtomicFile
import com.example.flags_android.internal.LOCAL_CACHED_CONFIG_READER_BOUNDED_LIMIT
import com.example.flags_android.internal.readBoundedText
import com.example.flags_core.clientconfiguration.CachedConfig
import com.example.flags_core.clientconfiguration.FlagConfigStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.File
import java.io.FileNotFoundException

internal class LocalFlagCacheStore(originalFile: File): FlagConfigStore {

    private val file = AtomicFile(originalFile)

    // Read cache from Disk file
    override fun read(): CachedConfig? {
        val raw = try {
            file.openRead()
        } catch (_: FileNotFoundException) {
            return null
        }

        val obj = raw.bufferedReader(Charsets.UTF_8).use {
            Json.parseToJsonElement(it.readBoundedText(LOCAL_CACHED_CONFIG_READER_BOUNDED_LIMIT)).jsonObject
        }

        return CachedConfig(
            rawConfigJson = obj.getValue(CACHED_CONFIG_JSON).jsonPrimitive.content,
            savedAtMillis = obj.getValue(CACHED_CONFIG_SAVED).jsonPrimitive.long
        )
    }

    // Write cache in Disk file
    override fun write(cachedConfig: CachedConfig) {
        val json = buildJsonObject {
            put(CACHED_CONFIG_JSON, cachedConfig.rawConfigJson)
            put(CACHED_CONFIG_SAVED, cachedConfig.savedAtMillis)
        }.toString()

        val stream = file.startWrite()
        try {
            stream.write(json.toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }

    companion object {

        private const val CACHED_CONFIG_JSON = "config_json"
        private const val CACHED_CONFIG_SAVED = "config_saved_millis"
    }
}
