package com.example.flags_core.model

import com.example.flags_core.clientconfiguration.ConfigSource

data class ConfigState(
    val version: Long = 0L,
    val configSource: ConfigSource = ConfigSource.DEFAULT,
    val isRefreshing: Boolean = false,
    val lastError: RefreshError? = null
)
