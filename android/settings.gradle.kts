// android/settings.gradle.kts

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") }
        // A reliable mirror for the now-defunct JCenter repository.
        maven { url = uri("https://repo.jfrog.org/artifactory/jcenter-cache") }
    }
}

dependencyResolutionManagement {
    // This is the recommended mode for new projects. It ensures all repositories
    // are declared in this single file, preventing conflicts.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        // A reliable mirror for the now-defunct JCenter repository.
        maven { url = uri("https://repo.jfrog.org/artifactory/jcenter-cache") }
    }
}

rootProject.name = "multilevel"
include(":app")
include(":models")