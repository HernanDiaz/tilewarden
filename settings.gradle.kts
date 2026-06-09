// Plugin de auto-descarga de JDKs por si en el futuro algún módulo declara una
// toolchain específica que el sistema no tenga instalada.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "Tilewarden"

include(":core")
// :app se añadirá en la Fase 2
