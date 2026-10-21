plugins { kotlin("jvm") }

// La superficie API congelada se COPIA aqui desde el checkout
// de MangaFox; nunca se duplica a mano. El android.jar viene de
// la resolucion central de la raiz (-PandroidJar > ANDROID_HOME
// > local.properties).
dependencies {
    compileOnly(files(rootProject.extra["androidJar"] as File))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}
kotlin { jvmToolchain(17) }
