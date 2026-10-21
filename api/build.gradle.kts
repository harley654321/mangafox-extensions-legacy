plugins { kotlin("jvm") }

// La superficie API congelada se COPIA aqui desde el checkout
// de MangaFox (workflow build-mfx); nunca se duplica a mano.
dependencies {
    compileOnly(files(rootProject.providers.gradleProperty("androidJar").get()))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}
kotlin { jvmToolchain(17) }
