// Root build file. Configuración común a todos los módulos.
// En Fase 2 se añadirá el plugin de Android aquí.

plugins {
    // Plugins declarados aquí pero no aplicados; los aplican los submódulos.
}

allprojects {
    repositories {
        mavenCentral()
        google()
    }
}
