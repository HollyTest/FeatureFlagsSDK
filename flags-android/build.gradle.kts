plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.flags_android"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

}

dependencies {
    api(project(":flags-core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.okhttp)
    implementation(libs.json)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
