package com.example.flags_core.internal

internal const val CONFIG_JSON_MAX_LENGTH = 1024 * 64

internal const val USER_KEY_MAX_LENGTH = 256
internal const val FLAG_NAME_MAX_LENGTH = 64

internal const val FLAGS_MAX_SIZE = 100

internal const val ROLLOUT_MAX_VALUE = 100

internal const val BUCKET_HASH_TYPE_SHA_256 = "SHA-256"

internal const val REMOTE_CONFIG_FETCH_TIMEOUT_DEFAULT_MILLIS = 3_000L
internal const val REMOTE_CONFIG_FETCH_TIMEOUT_MIN_MILLIS = 100L
internal const val REMOTE_CONFIG_FETCH_TIMEOUT_MAX_MILLIS = 30 * 1_000L

internal const val CONFIG_VALIDITY_DURATION_DEFAULT_MILLIS = 24 * 60 * 60 * 1_000L
internal const val CONFIG_VALIDITY_DURATION_MIN_MILLIS = 1_000L
internal const val CONFIG_VALIDITY_DURATION_MAX_MILLIS = 7 * 24 * 60 * 60 * 1_000L
