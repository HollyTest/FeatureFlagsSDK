package com.example.featureflagssdk.client

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.featureflagssdk.server.SampleFlagSource
import com.example.flags_android.client.AndroidFlagClient
import kotlinx.coroutines.flow.MutableStateFlow

class SampleViewModel(application: Application): AndroidViewModel(application) {

    val flagSource = SampleFlagSource()
    val flagClient = AndroidFlagClient.fromSource(
        context = application,
        flagSource = flagSource,
        cacheKey = "test",
    )

    val refreshResult = MutableStateFlow("No refresh")

    init {
        refresh()
    }

    fun refresh() {
        flagClient.refresh {result ->
            refreshResult.value = "Status: ${result.status}; Error: ${result.error ?: "No Error"}"
        }
    }

    override fun onCleared() {
        flagClient.close()
    }
}
