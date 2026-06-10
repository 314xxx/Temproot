pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    id("com.android.application") version "7.4.4" apply false
    id("org.jetbrains.kotlin.android") version "1.8.20" apply false
}

rootProject.name = "TempRootApp"
include(":app")