package com.example.flags_core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

// Json Keys
private const val FLAG_CONFIG_KEY_VERSION = "version"
private const val FLAG_CONFIG_KEY_FLAGS = "flags"
private const val FLAG_CONFIG_KEY_ENABLED = "enabled"
private const val FLAG_CONFIG_KEY_ROLLOUT = "rollout"

// Single flag configuration
internal data class Flag(
    val enabled: Boolean,
    val rollout: Int
)

// Full configuration
internal data class FlagConfig(
    val version: Long,
    val flags: Map<String, Flag>
)

// Configuration saved in memory
internal data class SavedFlagConfig(
    val flagConfig: FlagConfig,
    val savedAtMillis: Long
)

// Parse received JSON with flags
internal object FlagConfigParser {

    val flagNamePattern = Regex("[a-z][a-z0-9_]{0,${FLAG_NAME_MAX_LENGTH -1}}")

    fun parse(rawJSON: String): FlagConfig {
        require(rawJSON.length <= CONFIG_JSON_MAX_LENGTH)

        val json = Json.parseToJsonElement(rawJSON).jsonObject
        require(json.containsKey(FLAG_CONFIG_KEY_VERSION) && json.containsKey(FLAG_CONFIG_KEY_FLAGS))

        val version = json.getValue(FLAG_CONFIG_KEY_VERSION).jsonPrimitive
        require(!version.isString && version.long > 0L)

        val flagsObject = json.getValue(FLAG_CONFIG_KEY_FLAGS).jsonObject
        require(flagsObject.size <= FLAGS_MAX_SIZE)

        val flags = flagsObject.mapValues { (name, value) ->
            require(name.matches(flagNamePattern))

            val rule = value.jsonObject
            require(rule.keys == setOf(FLAG_CONFIG_KEY_ENABLED, FLAG_CONFIG_KEY_ROLLOUT))

            val enabled = rule.getValue(FLAG_CONFIG_KEY_ENABLED).jsonPrimitive
            require(!enabled.isString)

            val rollout = rule.getValue(FLAG_CONFIG_KEY_ROLLOUT).jsonPrimitive
            require(!rollout.isString && rollout.int in 0..ROLLOUT_MAX_VALUE)

            Flag(enabled.boolean, rollout.int)
        }

        return FlagConfig(version.long, flags)
    }
}
