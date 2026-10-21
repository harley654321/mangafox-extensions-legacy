import org.gradle.api.GradleException
import java.util.Properties

// Fabrica MFX. REGLA INVARIANTABLE: Kotlin de la fabrica == Kotlin
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

// ============================================================
// RESOLUCION DEL SDK ANDROID (para android.jar y d8).
// Orden de busqueda, sin adivinar:
//   1. -PandroidJar=/ruta/android.jar y -Pd8=/ruta/d8 (explicito)
//   2. Variable de entorno ANDROID_HOME
//   3. local.properties del propio repo (sdk.dir), como los
//      proyectos de AndroidIDE (generado por el IDE, no commit)
// Los modulos la heredan via rootProject.extra. Errores claros,
// nunca fallos mudos.
// ============================================================
val sdkAndroid: File = run {
    val explicita = providers.gradleProperty("sdkAndroid").orNull
    val porEnv = System.getenv("ANDROID_HOME")
    val porLocalProperties = File(rootDir, "local.properties").let { f ->
        if (f.isFile) {
            Properties().apply { f.inputStream().use { load(it) } }
                .getProperty("sdk.dir")
        } else null
    }
    File(explicita ?: porEnv ?: porLocalProperties ?: throw GradleException(
        "No se encontro el SDK de Android. Pasa -PsdkAndroid=/ruta/sdk, " +
        "define ANDROID_HOME, o crea local.properties con sdk.dir=..."
    ))
}.let { f ->
    if (!f.isDirectory) throw GradleException("El SDK no existe o no es directorio: $f")
    f
}

val androidJarResuelto: File = providers.gradleProperty("androidJar").orNull?.let { File(it) } ?: run {
    val plataformas = File(sdkAndroid, "platforms").listFiles()
        ?.filter { it.isDirectory && it.name.startsWith("android-") }
        ?: emptyList()
    val masAlta = plataformas.maxByOrNull {
        it.name.removePrefix("android-").substringBefore('.').toIntOrNull() ?: 0
    } ?: throw GradleException(
        "No hay plataformas en ${File(sdkAndroid, "platforms")}. " +
        "Instala un platform desde el SDK manager."
    )
    val jar = File(masAlta, "android.jar")
    if (!jar.isFile) throw GradleException("Falta android.jar en $masAlta")
    jar
}

// Herencia para :api y :mfx-mhscans (evaluado en la raiz ANTES de
// que cualquier modulo se configure: Gradle evalua la raiz primero).
allprojects {
    extra["sdkAndroid"] = sdkAndroid
    extra["androidJar"] = androidJarResuelto
}
