package com.harley.mangafox.data.web

import com.harley.mangafox.debug.MangaFoxLog
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cliente HTTP para fuentes de TEMA (HTML). Rate limit
 * conservador: 1 peticion cada 2 segundos (los CMS WordPress
 * son sensibles). Timeout 8 s.
 * Fallos:
 *  - HTTP != 200 -> null (fin de lista, 404, sin resultados).
 *  - Fallo de RED (timeout, caida) -> LANZA excepcion: el
 *    DirectorioOrchestrator la captura y REINTENTA al seguir
 *    bajando (ya no se confunde con fin de lista), y HomeOrchestrator
 *    la captura por seccion. Antes se devolvia null y un timeout
 *    mataba la paginacion para siempre.
 *
 * Soporta GET (getHtml) y POST form-urlencoded (postForm);
 * este ultimo es el transporte del ajax de Madara
 * (wp-admin/admin-ajax.php), que exige el header
 * X-Requested-With: XMLHttpRequest.
 *
 * RECUPERACION DE DOMINIO (DominioVivo, 20-oct): si el host
 * muere (UnknownHost/Connect, NO timeout: un timeout puede ser
 * un rate-ban y mentiria), se consulta UNA vez la pagina de
 * aterrizaje [recoveryUrl] del manifest (siempre viva fuera del
 * dominio caido), se extraen los hosts candidatos que anuncia y
 * se adopta el primero que responda. Mihon/Keiyoushi resuelven
 * igual (domainRecoveryUrl); asi una extension sobrevive a
 * cambios de dominio SIN tocar la apk: el dato vive en el repo.
 */
class ClienteWeb(
    baseUrl: String,
    /** Ritmo de cortesia del SITIO (ms entre peticiones). Lo
     *  declara el index del repo de extensiones (campo
     *  rateLimit, verificado contra keiyoushi); 0 = default
     *  conservador de 2 s (CMS WordPress sensibles). */
    private val esperaMs: Long = ESPERA_BASE_MS,
    /** Pagina de aterrizaje que anuncia el dominio vigente si
     *  el baseUrl muere (contrato de manifests; "" = sin
     *  recuperacion automatica). */
    private val recoveryUrl: String = ""
) {

    /** Dominio vivo actual: migra tras una recuperacion. */
    @Volatile private var baseViva: String = baseUrl

    suspend fun getHtml(ruta: String): String? = withContext(Dispatchers.IO) {
        respetarEspera()
        try {
            ejecutarGet(ruta)
        } catch (ce: java.util.concurrent.CancellationException) {
            throw ce
        } catch (e: Exception) {
            // Unica reencarnacion por llamada: si no hay
            // recuperacion posible, el fallo es el de siempre.
            if (!esCaidaDeHost(e) || !recuperarDominio()) {
                MangaFoxLog.w(
                    "Data.Web", "fallo de red GET " + ruta + " | " +
                        e.javaClass.simpleName + " (se propaga para reintento)"
                )
                throw e
            }
            MangaFoxLog.w(
                "Data.Vivo", "reintento con dominio recuperado: " +
                    URL(baseViva).host + " ruta=" + ruta
            )
            respetarEspera()
            ejecutarGet(ruta)
        }
    }

    /**
     * POST form-urlencoded (ajax de Madara y similares). Las claves
     * de [params] pueden llevar corchetes anidados (vars[s],
     * vars[tax_query][0][taxonomy], ...): se codifican igual que un
     * formulario de navegador, que es como lo envia el propio CMS.
     */
    suspend fun postForm(ruta: String, params: Map<String, String>): String? =
        withContext(Dispatchers.IO) {
            respetarEspera()
            val cuerpo = StringBuilder()
            params.forEach { (clave, valor) ->
                if (cuerpo.isNotEmpty()) cuerpo.append('&')
                cuerpo.append(URLEncoder.encode(clave, "UTF-8"))
                    .append('=')
                    .append(URLEncoder.encode(valor, "UTF-8"))
            }
            try {
                ejecutarPost(ruta, cuerpo.toString())
            } catch (ce: java.util.concurrent.CancellationException) {
                throw ce
            } catch (e: Exception) {
                if (!esCaidaDeHost(e) || !recuperarDominio()) {
                    MangaFoxLog.w(
                        "Data.Web", "fallo de red POST " + ruta + " | " +
                            e.javaClass.simpleName + " (se propaga para reintento)"
                    )
                    throw e
                }
                MangaFoxLog.w(
                    "Data.Vivo", "reintento POST con dominio recuperado: " +
                        URL(baseViva).host + " ruta=" + ruta
                )
                respetarEspera()
                ejecutarPost(ruta, cuerpo.toString())
            }
        }

    // ------------------------------------------------- transporte puro

    private fun ejecutarGet(ruta: String): String? {
        val t0 = System.currentTimeMillis()
        val conexion = URL(baseViva + ruta).openConnection() as HttpURLConnection
        try {
            conexion.connectTimeout = 8000
            conexion.readTimeout = 8000
            aplicarCabeceras(conexion)
            val codigo = conexion.responseCode
            if (codigo != 200) {
                MangaFoxLog.w("Data.Web", "GET " + ruta + " -> HTTP " + codigo)
                return null
            }
            val cuerpo = conexion.inputStream.use { it.readBytes() }
                .toString(Charsets.UTF_8)
            MangaFoxLog.d(
                "Data.Web", "GET " + ruta + " -> 200 (" + cuerpo.length +
                    " B, " + (System.currentTimeMillis() - t0) + " ms)"
            )
            return cuerpo
        } finally {
            conexion.disconnect()
        }
    }

    private fun ejecutarPost(ruta: String, cuerpo: String): String? {
        val t0 = System.currentTimeMillis()
        val conexion = URL(baseViva + ruta).openConnection() as HttpURLConnection
        try {
            conexion.connectTimeout = 8000
            conexion.readTimeout = 8000
            conexion.requestMethod = "POST"
            conexion.doOutput = true
            aplicarCabeceras(conexion)
            conexion.setRequestProperty(
                "Content-Type", "application/x-www-form-urlencoded"
            )
            conexion.setRequestProperty("X-Requested-With", "XMLHttpRequest")
            conexion.outputStream.use {
                it.write(cuerpo.toByteArray(Charsets.UTF_8))
            }
            val codigo = conexion.responseCode
            if (codigo != 200) {
                MangaFoxLog.w("Data.Web", "POST " + ruta + " -> HTTP " + codigo)
                return null
            }
            val respuesta = conexion.inputStream.use { it.readBytes() }
                .toString(Charsets.UTF_8)
            MangaFoxLog.d(
                "Data.Web", "POST " + ruta + " -> 200 (" + respuesta.length +
                    " B, " + (System.currentTimeMillis() - t0) + " ms)"
            )
            return respuesta
        } finally {
            conexion.disconnect()
        }
    }

    private fun aplicarCabeceras(conexion: HttpURLConnection) {
        conexion.setRequestProperty("User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/126.0")
        conexion.setRequestProperty("Accept-Language", "es-ES,es;q=0.9")
    }

    // ------------------------------------------------- DominioVivo

    /** Caida REAL del host: el DNS ya no responde o la conexion
     *  no se establece. El timeout se excluye a proposito: un
     *  Cloudflare saturado por rate-limit NO es un dominio
     *  muerto y recuperaria contra el mismo sitio. */
    private fun esCaidaDeHost(e: Exception): Boolean =
        e is java.net.UnknownHostException || e is java.net.ConnectException

    /** Consulta la pagina de aterrizaje [recoveryUrl], extrae
     *  los hosts que anuncia y adopta el primero que responda.
     *  Aterriza a la primera: los candidatos suelen apuntar al
     *  mismo dominio nuevo con variantes (www, http/https). */
    private fun recuperarDominio(): Boolean {
        if (recoveryUrl.isEmpty()) return false
        val muerto = URL(baseViva).host
        val aterrizaje = runCatching { descargarDirecto(recoveryUrl) }
            .getOrNull()
        if (aterrizaje == null) {
            MangaFoxLog.w("Data.Vivo", "aterrizaje inalcanzable: " + recoveryUrl)
            return false
        }
        val anfitrionAterrizaje = URL(recoveryUrl).host
        val candidatos = Regex(
            "https?://[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)+"
        ).findAll(aterrizaje).map { URL(it.value).host }.distinct()
            .filter { it != muerto && it != anfitrionAterrizaje }
        for (host in candidatos) {
            if (sondear("https://" + host)) {
                baseViva = "https://" + host
                MangaFoxLog.w(
                    "Data.Vivo", "DOMINIO RECUPERADO: " + muerto +
                        " -> " + host + " (aterrizaje " + anfitrionAterrizaje + ")"
                )
                return true
            }
        }
        MangaFoxLog.w(
            "Data.Vivo", "aterrizaje vivo pero sin dominio nuevo util" +
                " (" + candidatos.count() + " candidatos)"
        )
        return false
    }

    /** GET crudo a un URL ABSOLUTO, sin cortesia (host distinto
     *  y llamada unica de emergencia). */
    private fun descargarDirecto(urlAbsoluta: String): String? {
        val conexion = URL(urlAbsoluta).openConnection() as HttpURLConnection
        try {
            conexion.connectTimeout = 8000
            conexion.readTimeout = 8000
            aplicarCabeceras(conexion)
            if (conexion.responseCode !in 200..399) return null
            return conexion.inputStream.use { it.readBytes() }
                .toString(Charsets.UTF_8)
        } finally {
            conexion.disconnect()
        }
    }

    /** Un dominio candidato vale si su raiz responde (< 500). */
    private fun sondear(baseCandidata: String): Boolean {
        val conexion = URL(baseCandidata).openConnection() as HttpURLConnection
        try {
            conexion.connectTimeout = 6000
            conexion.readTimeout = 6000
            aplicarCabeceras(conexion)
            return conexion.responseCode < 500
        } catch (e: Exception) {
            return false
        } finally {
            conexion.disconnect()
        }
    }

    private suspend fun respetarEspera() {
        // LIMITADOR GLOBAL POR HOST (CortesiaHost): franjas
        // estampadas SOLO AL DISPARAR (autopsia 03:41: reservar
        // antes de dormir dejaba franjas fantasma), compartidas
        // con las PORTADAS del mismo host (incidente mhscans
        // 22:42: la rafaga de iconos quemaba el Cloudflare del
        // sitio y tumbaba TODO su acceso por ~50 s).
        val intervalo = if (esperaMs > 0L) esperaMs else ESPERA_BASE_MS
        CortesiaHost.esperar(URL(baseViva).host, intervalo)
    }

    private companion object {
        /** Cortesia por defecto (ms): CMS WordPress sensibles. */
        const val ESPERA_BASE_MS = 2000L
    }
}
