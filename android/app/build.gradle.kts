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
        versionCode = 5
        versionName = "1.1.1d"

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

// --- THIS IS THE FIX ---
// The old `com.wang.avi:library` is a transitive dependency brought in by another
// library. It was hosted on the now-defunct JCenter and is known to cause
// reflective serialization crashes with modern tools. We are forcing Gradle
// to replace it with a modern, well-maintained alternative (`Android-SpinKit`).
// Enhanced dependency resolution strategy
configurations.all {
    resolutionStrategy {
        // Force specific Moshi versions to ensure compatibility
        force("com.squareup.moshi:moshi:1.15.0")
        force("com.squareup.moshi:moshi-kotlin:1.15.0")

        // Original substitution for obsolete library
        dependencySubstitution {
            substitute(module("com.wang.avi:library:2.1.3"))
                .using(module("com.github.ybq:Android-SpinKit:1.4.0"))
                .because("The com.wang.avi:library is obsolete and causes reflective serialization crashes with the Click SDK's internal Moshi dependency.")
        }

        // Ensure Kotlin reflect is available for Moshi
        eachDependency {
            if (requested.group == "org.jetbrains.kotlin" && requested.name == "kotlin-reflect") {
                useVersion("1.9.22") // Use your project's Kotlin version
            }
        }
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
    implementation(libs.rxjava)
    implementation(libs.moshi.kotlin)
    implementation(libs.vosk.android)
    implementation(libs.androidx.play.billing)

    implementation(libs.android.msdk)
}