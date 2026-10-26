package com.example.flags_core.model

enum class EvaluationResult {
    BUCKET_INCLUDED,
    BUCKET_NOT_INCLUDED,
    FLAG_DISABLED,
    FLAG_MISSING,
    CONFIG_MISSING,
    CONFIG_EXPIRED,
    INVALID_USER,
    CLIENT_CLOSED
}

data class EvaluatedFlag(
    val enabled: Boolean,
    val evaluationResult: EvaluationResult,
    val bucketIndex: Int? = null
)
