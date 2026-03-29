pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://chaquo.com/maven") }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    // Changing to PREFER_PROJECT to allow app-level repository definitions if needed,
    // though we'll keep them here for now.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://chaquo.com/maven") }
        maven { url = uri("https://oss.sonatype.org/content/repositories/releases") }
    }
}

rootProject.name = "Sopo"
include(":app")