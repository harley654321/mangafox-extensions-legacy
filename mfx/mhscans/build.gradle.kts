import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

plugins { kotlin("jvm") }

dependencies {
    // Solo la superficie congelada: la apk resuelve el resto en
    // runtime (classloader padre), estilo Cloudstream. project()
    // garantiza que :api:jar compile ANTES que esta extension
    // (files() no crea dependencia de tarea y el jar no existia
    // al compilar: corrida 39552041831).
    compileOnly(project(":api"))
    compileOnly(files(rootProject.extra["androidJar"] as File))
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}
kotlin { jvmToolchain(17) }

// ============================================================
// ENSAMBLADO LOCAL DEL .mfx — reemplaza al CI eliminado
// (commit 324d8d2: compila solo el dueno). Pipeline identico al
// que estaba en .github/workflows/build-mfx.yml, pero 100% JDK,
// sin openssl ni python: corre en AndroidIDE con un comando.
//
//   ./gradlew :mfx-mhscans:construirMfx \
//       -PllaveMfx=/ruta/llave-mfx-privada-pkcs8.pem
//
// Pasos: :api:jar + jar -> d8 (DEX, min-api 26 = minSdk de la
// nueva APK) -> zip(manifest.json + classes.dex) -> sha256 ->
// firma ECDSA P-256 (la que la APK verifica contra la clave
// pinneada en ClavesMfx) -> index.json actualizado.
//
// La firma usa SHA256withECDSA en DER, EXACTAMENTE lo que
// VerificadorMfx.autentico() valida en la APK (verificado en la
// autopsia: openssl dgst -sha256 -sign produce el mismo DER que
// java.security.Signature).
// ============================================================
val URL_BASE_INDEX =
    "https://raw.githubusercontent.com/harley654321/mangafox-extensions/builds/mfx/"

tasks.register("construirMfx") {
    group = "mfx"
    description =
        "Ensambla mhscans.mfx (jar -> dex -> zip), lo firma ECDSA y actualiza index.json"
    dependsOn(":api:jar", "jar")

    doLast {
        val manifestF = File(projectDir, "manifest.json")
        val manifest = JsonSlurper().parse(manifestF) as Map<*, *>
        val id = manifest["id"] as? String
            ?: throw GradleException("manifest.json sin campo 'id'")
        val version = manifest["version"] as? Int
            ?: throw GradleException("manifest.json sin campo 'version' (entero)")

        // --- 1) El jar de la extension (producido por dependsOn) ---
        val dirJars = File(buildDir, "libs")
        val jarExt = dirJars.listFiles()?.filter { it.name.endsWith(".jar") }
            ?: throw GradleException("No hay jar en $dirJars")
        if (jarExt.size != 1) {
            throw GradleException("Se esperaba 1 jar en $dirJars, hay ${jarExt.size}")
        }
        val apiJar = File(rootProject.projectDir, "api/build/libs/api.jar")
        if (!apiJar.isFile) {
            throw GradleException("Falta api/build/libs/api.jar; ejecuta :api:jar")
        }

        // --- 2) D8: bytecode JVM -> DEX (runtime de la APK) ---
        val sdk = rootProject.extra["sdkAndroid"] as File
        val androidJar = rootProject.extra["androidJar"] as File
        val d8 = providers.gradleProperty("d8").orNull?.let { File(it) }
            ?: File(sdk, "build-tools").listFiles()
                ?.filter { it.isDirectory }
                ?.maxByOrNull { it.name }
                ?.resolve("d8")
            ?: throw GradleException(
                "No se encontro d8 en ${File(sdk, "build-tools")}. " +
                    "Instala build-tools o pasa -Pd8=/ruta/d8."
            )
        if (!d8.canExecute()) throw GradleException("d8 no ejecutable: $d8")

        val work = File(buildDir, "mfx/dexwork")
        work.deleteRecursively()
        work.mkdirs()
        project.exec {
            commandLine(
                d8.absolutePath, "--release", "--min-api", "26",
                "--lib", androidJar.absolutePath,
                "--classpath", apiJar.absolutePath,
                jarExt[0].absolutePath,
                "--output", work.absolutePath
            )
        }
        val dex = File(work, "classes.dex")
        if (!dex.isFile) throw GradleException("d8 no produjo classes.dex en $work")

        // --- 3) Empaquetado: ZIP con manifest.json + classes.dex ---
        val salida = File(buildDir, "mfx").apply { mkdirs() }
        val mfx = File(salida, "$id.mfx")
        ZipOutputStream(mfx.outputStream().buffered()).use { z ->
            z.putNextEntry(ZipEntry("manifest.json"))
            manifestF.inputStream().use { it.copyTo(z) }
            z.closeEntry()
            z.putNextEntry(ZipEntry("classes.dex"))
            dex.inputStream().use { it.copyTo(z) }
            z.closeEntry()
        }

        // --- 4) SHA-256 (capa 1 de VerificadorMfx en la APK) ---
        val hash = MessageDigest.getInstance("SHA-256").run {
            update(mfx.readBytes())
            digest().joinToString("") { "%02x".format(it) }
        }

        // --- 5) Firma ECDSA P-256 (capa 2 de VerificadorMfx) ---
        val rutaLlave = providers.gradleProperty("llaveMfx").orNull
            ?: System.getenv("MFX_LLAVE_PRIVADA")
            ?: throw GradleException(
                "Falta la llave privada de firma. Pasa " +
                    "-PllaveMfx=/ruta/llave-mfx-privada-pkcs8.pem o define " +
                    "MFX_LLAVE_PRIVADA con la ruta del PEM PKCS#8."
            )
        val pem = File(rutaLlave).readText()
        if (!pem.contains("BEGIN PRIVATE KEY")) {
            throw GradleException(
                "La llave debe ser PEM PKCS#8 (-----BEGIN PRIVATE KEY-----). " +
                    "Convierte la vieja SEC1 con: " +
                    "openssl pkcs8 -topk8 -nocrypt -in llave-vieja.pem -out llave-pkcs8.pem"
            )
        }
        val llave = KeyFactory.getInstance("EC").generatePrivate(
            PKCS8EncodedKeySpec(
                Base64.getMimeDecoder().decode(
                    pem.replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replace(Regex("\\s"), "")
                )
            )
        )
        val firmaB64 = Base64.getEncoder().encodeToString(
            Signature.getInstance("SHA256withECDSA").run {
                initSign(llave)
                update(mfx.readBytes())
                sign()
            }
        )

        // --- 5b) AUTO-VERIFICACION contra la llave publica ---
        // La misma puerta que VerificadorMfx en la APK: si la firma
        // no verifica contra la clave publica del repo, NO se toca
        // index.json ni se publica nada. Cero confianza en uno mismo.
        val publicaPem = File(rootProject.projectDir, "firmas/llave-publica.pem")
        if (!publicaPem.isFile) {
            throw GradleException(
                "Falta firmas/llave-publica.pem: sin auto-verificacion no se publica."
            )
        }
        val publica = KeyFactory.getInstance("EC").generatePublic(
            X509EncodedKeySpec(
                Base64.getMimeDecoder().decode(
                    publicaPem.readText()
                        .replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "")
                        .replace(Regex("\\s"), "")
                )
            )
        )
        val firmaValida = Signature.getInstance("SHA256withECDSA").run {
            initVerify(publica)
            update(mfx.readBytes())
            verify(Base64.getDecoder().decode(firmaB64))
        }
        if (!firmaValida) {
            throw GradleException(
                "La firma NO verifica contra firmas/llave-publica.pem: " +
                    "el .mfx NO se publica. Revisa la llave privada."
            )
        }
        println("firma verificada contra la llave publica del repo")

        // --- 6) index.json: registrar hash + firma + version ---
        val indexF = File(rootProject.projectDir, "index.json")
        val index = JsonSlurper().parse(indexF) as MutableMap<*, *>
        @Suppress("UNCHECKED_CAST")
        val entradas = index["extensions"] as MutableList<MutableMap<String, Any>>
        var tocado = false
        for (entrada in entradas) {
            if (entrada["id"] == id) {
                entrada["urlMfx"] = URL_BASE_INDEX + "$id.mfx"
                entrada["fileHashMfx"] = hash
                entrada["firmaMfx"] = firmaB64
                entrada["versionMfx"] = version
                tocado = true
            }
        }
        if (!tocado) {
            throw GradleException("La extension '$id' no esta registrada en index.json")
        }
        indexF.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(index)) + "\n")

        // --- 7) Resumen y pasos de publicacion ---
        println("==================================================")
        println("MFX LISTO: " + mfx.absolutePath)
        println("extension: $id v$version")
        println("sha256:    $hash")
        println("firmaMfx:  $firmaB64")
        println("index.json actualizado (versionMfx=$version)")
        println("")
        println("Publica cuando este listo:")
        println("  1) git add index.json && " +
            "git commit -m 'build(mfx): $id v$version publicado' && git push")
        println("  2) sube " + mfx.absolutePath + " a la rama builds como " +
            "mfx/$id.mfx (reemplaza el commit vigente de builds)")
        println("==================================================")
    }
}
