// Root build file. Configuración común a todos los módulos.

plugins {
    // Versión del plugin Kotlin disponible para los submódulos sin aplicarla aquí.
    kotlin("jvm") version "2.0.21" apply false
}

allprojects {
    repositories {
        mavenCentral()
        google()
    }
}
