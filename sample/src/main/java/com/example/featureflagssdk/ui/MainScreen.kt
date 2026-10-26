package com.example.featureflagssdk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.featureflagssdk.client.SampleViewModel
import com.example.featureflagssdk.models.MockScenes

@Composable
fun MainScreen(viewModel: SampleViewModel) {

    val flagState by viewModel.flagClient.flagState.collectAsState()
    val refreshResult by viewModel.refreshResult.collectAsState()

    var user by rememberSaveable { mutableStateOf("test_user") }

    var bucketPercentage by rememberSaveable { mutableIntStateOf(50) }
    var globalEnabled by rememberSaveable { mutableStateOf(true) }
    var scene by rememberSaveable { mutableStateOf(MockScenes.NORMAL.name) }


    val evaluationResult = remember(flagState, user) {
        viewModel.flagClient.evaluate(
            flagName = "new_feature",
            userKey = user,
            defaultValue = false
        )
    }

    val isFlagEnabled = remember(flagState, user) {
        viewModel.flagClient.isEnabled(
            flagName = "new_feature",
            userKey = user,
            defaultValue = false
        )
    }

    Scaffold(modifier = Modifier.fillMaxSize().padding((24.dp))) {
        paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Feature Flags SDK",
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = Color.Black
                )
            }

            // Server Section
            Text(
                text = "Server",
                fontSize = 20.sp,
                color = Color.DarkGray,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Global Switch",
                    modifier = Modifier.weight(1f),
                    color = Color.DarkGray
                )
                Switch(
                    checked = globalEnabled,
                    onCheckedChange = {
                        globalEnabled = it
                        viewModel.flagSource.globalEnabled = it
                        viewModel.refresh()
                    }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 25,50, 75, 100).forEach { percentage ->
                    FilterChip(
                        selected = bucketPercentage == percentage,
                        onClick = {
                            bucketPercentage = percentage
                            viewModel.flagSource.bucketPercentage = percentage
                            viewModel.refresh()
                        },
                        label = { Text("$percentage%") }
                    )
                }
            }

            Text(
                text = "Mock Scenario",
                fontSize = 16.sp,
                color = Color.DarkGray
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MockScenes.entries.toList().forEach { currentScene ->
                    FilterChip(
                        selected = scene == currentScene.name,
                        onClick = {
                            scene = currentScene.name
                            viewModel.flagSource.scene = currentScene
                            viewModel.refresh()
                        },
                        label = {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(currentScene.name)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            HorizontalDivider()

            // Client Section
            Text(
                text = "Client",
                fontSize = 20.sp,
                color = Color.DarkGray,
                fontWeight = FontWeight.Bold
            )
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                label = { Text("User") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isFlagEnabled) Color.Green else Color.Red
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "test feature",
                        fontSize = 20.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.padding(8.dp))
                    Text(
                        text = "Feature Enabled: $isFlagEnabled",
                        fontSize = 16.sp,
                        color = Color.DarkGray
                    )
                    Text(
                        text = "Evaluation: ${evaluationResult.evaluationResult}",
                        fontSize = 16.sp,
                        color = Color.DarkGray
                    )
                    Text(
                        text = "BucketIndex: ${evaluationResult.bucketIndex}",
                        fontSize = 16.sp,
                        color = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.padding(8.dp))
                    Text(
                        text = "source: ${flagState.configSource}; version: ${flagState.version}",
                        fontSize = 12.sp,
                        color = Color.White
                    )
                    if (flagState.lastError != null) {
                        Text(
                            text = "Error: ${flagState.lastError}",
                            fontSize = 14.sp,
                            color = Color.Yellow
                        )
                    }
                    Text(
                        text = "Result: $refreshResult",
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }
            }

        }
    }

}
