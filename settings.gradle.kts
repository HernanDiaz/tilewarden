// Resolver repositories for plugin lookups (AGP, Kotlin, Compose plugin).
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

// Resolver repositories for project dependencies. Modern style: only declare
// repos here, never per-module (FAIL_ON_PROJECT_REPOS enforces it).
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// Auto-download JDKs declared via toolchain {} blocks.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "Tilewarden"

include(":core")
include(":app")
