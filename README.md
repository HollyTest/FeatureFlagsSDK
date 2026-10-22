# FeatureFlagsSDK

A lightweight, robust, and zero-external-dependency (core) Feature Flag SDK for Android and Kotlin projects. 

This SDK provides a complete solution for fetching, caching, evaluating, and reacting to remote feature configurations, enabling you to safely roll out new features and run A/B tests using local bucketing.

## Features

* **Multi-Module Architecture**
  * `:flags-core`: The underlying engine written in pure Kotlin. It handles configuration parsing, local bucketing, coroutine-based state management, and memory/local caching. It has no Android dependencies, making it suitable for any JVM/Kotlin environment.
  * `:flags-android`: An Android-specific extension that bridges the core engine with the Android platform, automatically routing callback results to the Main Thread via `Looper.getMainLooper()`.
* **Local Deterministic Bucketing**: Uses `SHA-256` hashing on `flagName:userKey` to consistently determine user allocation (0-100 rollout percentage) entirely on the client side without extra network roundtrips.
* **Concurrency Safe**: Uses Coroutines and `Mutex` to prevent redundant network requests when multiple components trigger a refresh simultaneously.
* **Reactive State**: Exposes a `StateFlow` to easily observe fetching states, version changes, and errors, which integrates seamlessly with modern UI frameworks like Jetpack Compose.
* **Strict Configuration Parsing**: Validates incoming remote JSON strictly (handling missing fields, type safety, max lengths, etc.) using `kotlinx-serialization-json`.

## How It Works

1. **Fetching**: You provide a `FlagSource` (e.g., an OkHttp network call) to fetch a JSON payload containing flag rules (whether it is globally enabled and its rollout percentage).
2. **Caching**: Configurations are cached locally (you provide the `FlagConfigStore` implementation) and in memory. The SDK handles cache expiration and offline fallbacks.
3. **Evaluating**: When you request a flag's status, the SDK checks the memory cache. If the flag exists, it calculates the user's bucket index. If the index falls within the rollout percentage, the flag is enabled.

## Usage Guide

### 1. Setup the Client
You need to instantiate the client with a remote source and a local cache store. For Android projects, wrap it with `AndroidFlagClient` to handle thread dispatching automatically.

```kotlin
val coreClient = FlagClient(
    source = MyNetworkFlagSource(),
    store = MyLocalFlagConfigStore()
)
val androidClient = AndroidFlagClient.getInstance(coreClient)
```

### 2. Evaluate a Flag
You can evaluate a flag safely anywhere in your app. It's fully synchronous and non-blocking since it relies on the local cache. If the configuration is missing or the flag doesn't exist, it falls back to the default value.

```kotlin
val isEnabled = androidClient.isEnabled("new_feature", userKey = "user_123", defaultValue = false)
```
*Note: If you need to know exactly why a flag was disabled (e.g., bucket not included, config expired, invalid user), use the `evaluate()` method instead which returns a detailed `EvaluatedFlag` result.*

### 3. Refresh Configuration
Trigger a background refresh when your app launches or comes to the foreground. The SDK guarantees that multiple simultaneous calls will only trigger a single network request.

```kotlin
androidClient.refresh { result ->
    // result.status will tell you if it's UPDATE, NO_CHANGE, FAILED, etc.
}
```

### 4. Observe Global State (Optional)
If you need to show a loading spinner or react to network failures globally, you can observe the reactive state flow.

```kotlin
// In Jetpack Compose or ViewModel
val flagState by androidClient.flagState.collectAsState()
// flagState.isRefreshing 
// flagState.lastError
```

## Running the Sample
The project includes a `:sample` module (an Android App) built with Jetpack Compose. It includes a mock server (`SampleFlagSource`) that allows you to simulate various scenarios like offline mode, slow network, corrupted configurations, and version downgrades to see how the SDK gracefully handles edge cases.


## Demo
https://github.com/user-attachments/assets/adb091d4-9fc2-4cdf-92b2-a73b1a7183da

Cache:
<img width="1029" height="348" alt="cache-screenshot" src="https://github.com/user-attachments/assets/b5b7a0fd-9f94-4b1f-b52e-481a00fb03e1" />




