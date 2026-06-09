// Módulo :core — lógica de juego pura en Kotlin, JVM, sin dependencias de Android.
// Se importa tal cual desde el módulo :app cuando llegue Fase 2.

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    application
}

application {
    // Entry point of the runnable demo (./gradlew :core:run).
    // The Kotlin file `TilewardenDemo.kt` with a top-level `main()`
    // compiles to a class named `TilewardenDemoKt`.
    mainClass.set("com.tilewarden.core.TilewardenDemoKt")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
