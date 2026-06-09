// Root build file. Declares plugin versions used by submodules.
// Repositories live in settings.gradle.kts (modern style, enforced by
// FAIL_ON_PROJECT_REPOS).

plugins {
    kotlin("jvm")             version "2.0.21" apply false
    kotlin("android")         version "2.0.21" apply false
    kotlin("plugin.compose")  version "2.0.21" apply false
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library")     version "8.7.3" apply false
}
