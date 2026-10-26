package com.example.flags_core.clientbuilder

import com.example.flags_core.model.ConfigState
import com.example.flags_core.model.EvaluatedFlag
import com.example.flags_core.model.RefreshCallback
import com.example.flags_core.clientconfiguration.FlagSource
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for client to operate on flag configurations.
 */
interface FlagClient : AutoCloseable {
    val configStateFlow: StateFlow<ConfigState>

    // Whether single flag on specific user is enabled or not
    fun isEnabled(
        flagName: String,
        userKey: String,
        defaultValue: Boolean = false
    ): Boolean

    // Evaluates whether single flag is enabled for specific user
    fun evaluate(
        flagName: String,
        userKey: String,
        defaultValue: Boolean = false
    ): EvaluatedFlag

    // Refresh flag configurations from remote source
    fun refresh(callBack: RefreshCallback)

    companion object {
        @JvmStatic
        fun builder(source: FlagSource): FlagClientBuilder = FlagClientBuilder(source)
    }
}
