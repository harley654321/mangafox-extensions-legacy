// Fabrica MFX. REGLA INVARIABLE: Kotlin de la fabrica == Kotlin
// de la APK, JAMAS mayor. Hoy 2.2.10 = el Kotlin integrado de la
// nueva APK MangaFox (plantilla TemplateIDE, BUILD SUCCESSFUL del
// dueno oct-2026). Historia: con la APK vieja (1.9.24) un plugin
// compilado con 2.x revento en runtime con NoClassDefFoundError
// (SpillingKt, log 01:33 t=+42.0s); por eso estuvo pineada en
// 1.9.24 hasta que la nueva APK existiera. Bytecode mas viejo
// que el runtime siempre es seguro; al reves, no.
plugins { kotlin("jvm") version "2.2.10" apply false }

allprojects {
    repositories { mavenCentral() }
}
