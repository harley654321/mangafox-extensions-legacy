plugins { kotlin("jvm") }

dependencies {
    // Solo la superficie congelada: la apk resuelve el resto
    // en runtime (classloader padre), estilo Cloudstream.
    // project() garantiza que :api:jar compile ANTES que esta
    // extension (files() no crea dependencia de tarea y el jar
    // no existia al compilar: corrida 39552041831).
    compileOnly(project(":api"))
    compileOnly(files(rootProject.providers.gradleProperty("androidJar").get()))
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}
kotlin { jvmToolchain(17) }
