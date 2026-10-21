plugins { kotlin("jvm") }

dependencies {
    // Solo la superficie congelada: la apk resuelve el resto
    // en runtime (classloader padre), estilo Cloudstream.
    compileOnly(files(rootProject.projectDir.resolve("api/build/libs/api.jar")))
    compileOnly(files(rootProject.providers.gradleProperty("androidJar").get()))
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}
kotlin { jvmToolchain(17) }
