package com.example.flags_core.internal

import junit.framework.TestCase.assertEquals
import org.junit.Test

class FlagConfigParserTest {

    @Test
    fun parsesValidConfig() {
        val json = """
            {
                "version": 10,
                "flags": {
                    "feature_a": {
                        "enabled": true,
                        "rollout": 50
                    }
                }
            }
        """.trimIndent()
        val result = FlagConfigParser.parse(json)
        assertEquals(10L, result.version)
        assertEquals(Flag(true, 50), result.flags["feature_a"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun throwsOnMissingFlags() {
        val json = """
            {
                "version": 10
            }
        """.trimIndent()
        FlagConfigParser.parse(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun throwsOnMissingVersion() {
        val json = """
            {
                "flags": {
                    "feature_a": {
                        "enabled": true,
                        "rollout": 50
                    }
                }
            }
        """.trimIndent()
        FlagConfigParser.parse(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun throwsOnStringVersion() {
        val json = """
            {
                "version": "10",
                "flags": {}
            }
        """.trimIndent()
        FlagConfigParser.parse(json)
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun throwsOnNegativeVersion() {
        val json = """
            {
                "version": -5,
                "flags": {}
            }
        """.trimIndent()
        FlagConfigParser.parse(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun throwsOnInvalidFlagNameHeader() {
        val json = """
            {
                "version": 10,
                "flags": {
                    "1_invalid_name": {
                        "enabled": true,
                        "rollout": 50
                    }
                }
            }
        """.trimIndent()
        FlagConfigParser.parse(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun throwsOnInvalidFlagNameCharacter() {
        val json = """
            {
                "version": 10,
                "flags": {
                    "invalid-name": {
                        "enabled": true,
                        "rollout": 50
                    }
                }
            }
        """.trimIndent()
        FlagConfigParser.parse(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun throwsOnMissingEnabledInFlag() {
        val json = """
            {
                "version": 1,
                "flags": {
                    "feature_a": {
                        "rollout": 50
                    }
                }
            }
        """.trimIndent()
        FlagConfigParser.parse(json)
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun throwsOnMissingRolloutInFlag() {
        val json = """
            {
                "version": 1,
                "flags": {
                    "feature_a": {
                        "enabled": true
                    }
                }
            }
        """.trimIndent()
        FlagConfigParser.parse(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun throwsOnStringEnabled() {
        val json = """
            {
                "version": 1,
                "flags": {
                    "feature_a": {
                        "enabled": "true",
                        "rollout": 50
                    }
                }
            }
        """.trimIndent()
        FlagConfigParser.parse(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun throwsOnInvalidRollout() {
        val json = """
            {
                "version": 1,
                "flags": {
                    "feature_a": {
                        "enabled": true,
                        "rollout": 150
                    }
                }
            }
        """.trimIndent()
        FlagConfigParser.parse(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun throwsOnStringRollout() {
        val json = """
            {
                "version": 1,
                "flags": {
                    "feature_a": {
                        "enabled": true,
                        "rollout": "50"
                    }
                }
            }
        """.trimIndent()
        FlagConfigParser.parse(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun throwsOnInvalidJsonStructure() {
        val json = """
            {
                "invalid": "structure"
            }
        """.trimIndent()
        FlagConfigParser.parse(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun throwsOnConfigExceedingMaxLength() {
        val largeJson = " ".repeat(CONFIG_JSON_MAX_LENGTH + 1)
        FlagConfigParser.parse(largeJson)
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun throwsOnExceedingMaxFlags() {
        val flags = (1..FLAGS_MAX_SIZE + 1).joinToString(", ") { i ->
            """"feature_$i": { "enabled": true, "rollout": 50 }"""
        }
        val json = """
            {
                "version": 10,
                "flags": { $flags }
            }
        """.trimIndent()
        FlagConfigParser.parse(json)
    }
}
