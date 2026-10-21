// Fabrica MFX. REGLA: Kotlin 1.9.24 = el de la APK
// (stddev mismatch provoca NoClassDefFoundError SpillingKt,
// log 01:33 t=+42.0s). El CI corre ./gradlew (wrapper 8.4).
plugins { kotlin("jvm") version "1.9.24" apply false }

allprojects {
    repositories { mavenCentral() }
}
