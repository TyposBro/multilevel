// {PATH_TO_PROJECT}/app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.google.services)
}

android {
    namespace = "org.milliytechnology.spiko"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.milliytechnology.spiko"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.1d"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // DEBUG Keystore
    signingConfigs {
        getByName("debug") {
            // Point to the debug keystore you committed to your project.
            storeFile = file("keystores/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    // IMPORTANT: Add this packagingOptions block. This is needed because
    // the Vosk .aar and other libraries might have conflicting native lib files.
    // This tells Gradle to pick the first one it finds, which is standard practice.
    packaging {
        resources.pickFirsts.add("**/libc++_shared.so")
        resources.pickFirsts.add("**/libkaldi_jni.so") // This might be needed depending on the Vosk version
    }
}

// THIS IS THE NEW AND CRITICAL BLOCK
configurations.all {
    resolutionStrategy.dependencySubstitution {
        // Find the problematic dependency
        substitute(module("com.wang.avi:library:2.1.3"))
            // And replace it with a modern, available alternative
            .using(module("com.github.ybq:Android-SpinKit:1.4.0"))
            .because("com.wang.avi:library is on the deprecated JCenter and is causing network resolution issues.")
    }
}

dependencies {
    // Vosk STT Engine
    implementation(project(":models"))

    // Core Android & Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.material)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.appcompat)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // ViewModels
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
    implementation(libs.okhttp.sse)

    // Secure Storage
    implementation(libs.androidx.security.crypto)

    // Room (Local Database)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.benchmark.common)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)

    // Dagger Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Firebase Analytics
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)

    // Google  Sign-In
    implementation(libs.play.services.auth)

    // Built-in Browser
    implementation(libs.androidx.browser)

    // Jetpack DataStore for Theme Preferences
    implementation(libs.androidx.datastore.preferences)

    // Coil for Image Loading
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Swipe
    implementation(libs.swipeablecard)

    implementation(libs.vosk.android)
    implementation(libs.androidx.play.billing)

    implementation(libs.android.msdk)
}