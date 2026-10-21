package com.harley.mangafox.mfx.jeazscans

import com.harley.mangafox.data.parser.Seccion
import com.harley.mangafox.data.web.ClienteWeb
import com.harley.mangafox.debug.MangaFoxLog
import com.harley.mangafox.logic.detalle.Capitulo
import com.harley.mangafox.logic.detalle.ObraDetalle
import com.harley.mangafox.logic.directorio.Genero
import com.harley.mangafox.logic.home.ObraHome
import com.harley.mangafox.logic.lector.PaginasCapitulo
import com.harley.mangafox.mfx.api.ContextoMfx
import com.harley.mangafox.mfx.api.FuenteMfx
import java.math.BigDecimal
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.text.RegexOption.DOT_MATCHES_ALL

/**
 * EXTENSION MFX jeazscans — Jeaz Scans (LectorHub).
 *
 * Parser AUTOCONTENIDO del lector PHP propio de Jeaz Scans,
 * portado del TemaJeaz de la apk vieja (validado en vivo
 * contra el sitio real 20-oct-2026 por el dueno) y re-verificado
 * contra el host vigente lectorhub.j5z.xyz el 21-oct (home con
 * popular-card + release-card, directorio.php con 20 tarjetas
 * por pagina y 59 generos en el form, ajax_search JSON,
 * api_capitulos_manga JSON, lector con data-verify BASE64
 * invertido). Vive FUERA de la apk: si el sitio rediseña, se
 * publica un .mfx nuevo desde el repo sin tocar la app.
 *
 * CERO PAGINAS DOBLES (leccion mhscans log 881): populares,
 * ranking, recientes y cargarSeccion(p1) salen TODOS del MISMO
 * GET del home, compartido por cache TTL + single-flight
 * (Mutex): tres secciones en paralelo = UNA sola peticion.
 * El sitio no tiene vista de ranking: sus populares (mas
 * vistos del home) SON su ranking. Nada inventado.
 *
 * Reglas de red del sitio (evidencia keiyoushi + vieja entry):
 * rateLimit 2 peticiones / 1 s y aterrizaje de recuperacion
 * https://jeazscans.com (anuncia el dominio vigente si
 * lectorhub muere).
 */
class FuenteJeazScans(contexto: ContextoMfx) : FuenteMfx {

    override val id: String = "jeazscans"

    private val web: ClienteWeb = contexto.clienteWeb(BASE, RITMO_MS, RECOVERY)

    // ------------------------------------------------------------ home

    private val cerrojoHome = Mutex()
    private var htmlHome: String? = null
    private var expiraHome = 0L

    /** Home compartido: cache TTL 60 s + single-flight. Tres
     *  secciones en paralelo = UN GET; el cache lo dice todo. */
    private suspend fun home(): String? = cerrojoHome.withLock {
        val ahora = System.currentTimeMillis()
        if (htmlHome != null && ahora < expiraHome) {
            MangaFoxLog.d(ETIQUETA, "home en cache (TTL)")
            return@withLock htmlHome
        }
        val html = web.getHtml("/")
        htmlHome = html
        expiraHome = ahora + TTL_HOME_MS
        MangaFoxLog.d(ETIQUETA, if (html != null)
            "home GET (" + html.length + " B, cache " + TTL_HOME_MS / 1000 + " s)"
        else "home cayo")
        html
    }

    override suspend fun cargarPopulares(): List<ObraHome> =
        desdeHome("popular-card")

    /** El sitio no tiene ranking: sus populares SON su ranking.
     *  Con el cache de home es CERO peticiones extra. */
    override suspend fun cargarRanking(): List<ObraHome> =
        desdeHome("popular-card")

    /** Recientes del home: article.release-card con a.release-cover
     *  (aria-label) e img (alt). El anchor YA NO lleva la marca
     *  release-card (log 760: el regex del viejo TemaJeaz daba 0
     *  aqui; el sitio movio la marca al article). Bloque por
     *  article, verificado en vivo 21-oct: 12 recientes. */
    override suspend fun cargarRecientes(): List<ObraHome> {
        val html = home() ?: return emptyList()
        val vistos = mutableSetOf<String>()
        return withContext(Dispatchers.IO) {
            Regex(
                "<article class=\"release-card[^>]*>(.*?)</article>",
                DOT_MATCHES_ALL
            ).findAll(html).mapNotNull { b ->
                val bloque = b.groupValues[1]
                val id = Regex("href=\"manga\\.php\\?id=(\\d+)\"")
                    .find(bloque)?.groupValues?.get(1) ?: return@mapNotNull null
                if (!vistos.add(id)) return@mapNotNull null
                val img = Regex("<img[^>]*>").find(bloque)?.value
                    ?: return@mapNotNull null
                val portada = Regex("src=\"([^\"]+)\"").find(img)
                    ?.groupValues?.get(1)?.let { resolver(it) }
                    ?: return@mapNotNull null
                val titulo = Regex("alt=\"([^\"]*)\"").find(img)
                    ?.groupValues?.get(1)?.trim()?.ifEmpty { null }
                    ?: Regex("aria-label=\"Abrir ([^\"]+)\"").find(bloque)
                        ?.groupValues?.get(1)?.trim()
                    ?: return@mapNotNull null
                val enlace = "/manga.php?id=" + id
                ObraHome(enlace, titulo, portada, enlace = enlace)
            }.toList()
        }.also {
            MangaFoxLog.d(ETIQUETA, "home release-card: " + it.size + " obras")
        }
    }

    /** El home no pagina: pagina 1 = el home; pagina 2+ vacia
     *  honesta (fin inmediato de la cadena, cero peticiones). */
    override suspend fun cargarSeccion(
        seccion: Seccion,
        pagina: Int
    ): List<ObraHome> = when (pagina) {
        1 -> when (seccion) {
            Seccion.RECIENTES -> cargarRecientes()
            Seccion.POPULARES, Seccion.RANKING -> cargarPopulares()
        }
        else -> emptyList()
    }

    /** Tarjetas del home: img (alt = titulo) y enlace
     *  manga.php?id=N (verificado en vivo 21-oct: 24 tarjetas). */
    private suspend fun desdeHome(marca: String): List<ObraHome> {
        val html = home() ?: return emptyList()
        val vistos = mutableSetOf<String>()
        return withContext(Dispatchers.IO) {
            Regex(
                "<a[^>]*href=\"(manga\\.php\\?id=\\d+)\"[^>]*class=\"" + marca +
                    "\"[^>]*>.*?<img[^>]*src=\"([^\"]+)\"[^>]*alt=\"([^\"]*)\"",
                DOT_MATCHES_ALL
            ).findAll(html).mapNotNull { m ->
                val enlace = "/" + m.groupValues[1]
                if (!vistos.add(enlace)) return@mapNotNull null
                val portada = resolver(m.groupValues[2]) ?: return@mapNotNull null
                val titulo = m.groupValues[3].trim().ifEmpty { enlace }
                ObraHome(enlace, titulo, portada, enlace = enlace)
            }.toList()
        }.also {
            MangaFoxLog.d(ETIQUETA, "home " + marca + ": " + it.size + " obras")
        }
    }

    // ------------------------------------------------------------ generos

    private var generosCache: List<Genero>? = null

    /** Generos del form del directorio: input generos[] con el
     *  nombre como value (verificado: 59 en 20 y 21-oct). */
    override suspend fun generos(): List<Genero> {
        generosCache?.let { return it }
        val html = web.getHtml("/directorio.php?page=1") ?: return emptyList()
        val res = mutableListOf<Genero>()
        Regex("name=\"generos\\[\\]\"[^>]*value=\"([^\"]{2,40})\"")
            .findAll(html).forEach {
                val nombre = it.groupValues[1].trim()
                if (nombre.isNotEmpty() && res.none { g -> g.slug == nombre }) {
                    res.add(Genero(nombre, nombre))
                }
            }
        generosCache = res
        MangaFoxLog.d(ETIQUETA, "generos del directorio: " + res.size)
        return res
    }

    // ------------------------------------------------------------ directorio

    override val prefetchAlFinal: Boolean get() = true

    /** 20 tarjetas por pagina; la busqueda es un JSON unico sin
     *  paginar (pagina 1 completa): 0 = fin inmediato honesto. */
    override fun tamanoPaginaPedido(
        generoSlug: String?,
        busqueda: Boolean
    ) = if (busqueda) 0 else 20

    override suspend fun cargarDirectorio(
        pagina: Int,
        generoSlug: String?
    ): List<ObraHome> {
        var ruta = "/directorio.php?page=" + pagina
        if (!generoSlug.isNullOrEmpty()) {
            ruta += "&generos%5B%5D=" + URLEncoder.encode(generoSlug, "UTF-8")
        }
        val html = web.getHtml(ruta) ?: return emptyList()
        return withContext(Dispatchers.IO) { parsearDirectorio(html) }.also {
            MangaFoxLog.d(
                ETIQUETA, "directorio pag=" + pagina +
                    (if (generoSlug != null) " genero=" + generoSlug else "") +
                    ": " + it.size + " obras"
            )
        }
    }

    /** Tarjetas directory-card: 20 por pagina (41 resultados en
     *  el genero Accion -> 20 + 20 + 1). El fin lo confirma el
     *  motor con la pagina vacia. */
    private fun parsearDirectorio(html: String): List<ObraHome> {
        val obras = mutableListOf<ObraHome>()
        Regex(
            "<a[^>]*href=\"/?manga\\.php\\?id=(\\d+)\"[^>]*" +
                "class=\"directory-card\"[^>]*aria-label=\"Abrir ([^\"]+)\"[^>]*>" +
                ".*?<img[^>]*src=\"([^\"]+)\"",
            DOT_MATCHES_ALL
        ).findAll(html).forEach { m ->
            val enlace = "/manga.php?id=" + m.groupValues[1]
            val titulo = m.groupValues[2].trim()
            val portada = resolver(m.groupValues[3]) ?: return@forEach
            if (obras.none { it.id == enlace }) {
                obras.add(ObraHome(enlace, titulo, portada, enlace = enlace))
            }
        }
        return obras
    }

    // ------------------------------------------------------------ busqueda

    /** ajax_search.php devuelve TODOS los aciertos en un JSON
     *  unico: la pagina 1 es completa y las siguientes vacias
     *  (fin honesto inmediato). */
    override suspend fun buscar(texto: String, pagina: Int): List<ObraHome> {
        val q = texto.trim()
        if (q.isEmpty() || pagina > 1) return emptyList()
        val crudo = web.getHtml(
            "/ajax_search.php?q=" + URLEncoder.encode(q, "UTF-8")
        ) ?: return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching { JSONArray(crudo) }.getOrNull()?.let { raiz ->
                (0 until raiz.length()).mapNotNull { i ->
                    val e = raiz.optJSONObject(i) ?: return@mapNotNull null
                    val id = e.optInt("id", -1).takeIf { it > 0 }
                        ?: return@mapNotNull null
                    val titulo = e.optString("titulo").trim()
                    if (titulo.isEmpty()) return@mapNotNull null
                    val portada = resolver(e.optString("portada")) ?: ""
                    val enlace = "/manga.php?id=" + id
                    ObraHome(enlace, titulo, portada, enlace = enlace)
                }
            } ?: emptyList()
        }.also {
            MangaFoxLog.d(ETIQUETA, "busqueda '" + q + "': " + it.size + " obras")
        }
    }

    // -------------------------------------------------------------- ficha

    override suspend fun cargarDetalle(obra: ObraHome): ObraDetalle? {
        if (obra.enlace.isEmpty()) return null
        val t0 = System.currentTimeMillis()
        val html = web.getHtml(obra.enlace)
        if (html == null) {
            MangaFoxLog.w(ETIQUETA, "ficha sin HTML: " + obra.enlace)
            return null
        }
        val detalle = withContext(Dispatchers.IO) { extraerFicha(html, obra) }
        val capitulos = if (detalle != null) {
            extraerCapitulos(html)
        } else emptyList()
        MangaFoxLog.d(
            ETIQUETA, "ficha '" + (detalle?.titulo ?: obra.titulo) + "': " +
                capitulos.size + " caps (" +
                (System.currentTimeMillis() - t0) + " ms)"
        )
        return detalle?.copy(capitulos = capitulos)
    }

    private fun extraerFicha(html: String, obra: ObraHome): ObraDetalle? {
        val titulo = Regex("class=\"blood-title\"[^>]*>([^<]{2,200})<")
            .find(html)?.groupValues?.get(1)?.trim() ?: obra.titulo
        val portada = Regex(
            "cultivation-panel.{0,400}?src=\"([^\"]+)\"",
            DOT_MATCHES_ALL
        ).find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            ?.let { resolver(it) } ?: obra.portadaUrl
        val sinopsis = Regex(
            "class=\"[^\"]*text-gray-200[^\"]*\"[^>]*>(.*?)</div>",
            DOT_MATCHES_ALL
        ).findAll(html).map { it.groupValues[1] }
            .firstOrNull { it.contains("SINOPSIS", ignoreCase = true) }
            ?.let { texto(it)?.replace(Regex("^SINOPSIS:?\\s*",
                RegexOption.IGNORE_CASE), "") }
            .orEmpty()
        val generos = mutableListOf<String>()
        Regex("directorio\\.php\\?genero=[a-z0-9-]+[^>]*>\\s*([^<]{2,40})<")
            .findAll(html).forEach {
                val g = it.groupValues[1].trim()
                if (g.isNotEmpty() && !generos.contains(g)) generos.add(g)
            }
        val estado = Regex("class=\"status-badge\"[^>]*>\\s*([^<]{2,30})<")
            .find(html)?.groupValues?.get(1)?.trim()?.let { traducirEstado(it) }
        return ObraDetalle(
            id = obra.id,
            titulo = titulo,
            portadaUrl = portada,
            sinopsis = sinopsis,
            estado = estado,
            generos = generos,
            capitulos = emptyList()
        )
    }

    /** Capitulos por la API del sitio (la ficha solo pinta los
     *  primeros). El id de manga sale del script MANGA_ID de la
     *  propia ficha y el slug del lector de sus enlaces /leer/. */
    private suspend fun extraerCapitulos(html: String): List<Capitulo> {
        val idApi = Regex("MANGA_ID\\s*=\\s*(\\d+)").find(html)
            ?.groupValues?.get(1) ?: return emptyList()
        val slug = Regex("href=\"/leer/([a-z0-9-]+)/capitulo-")
            .find(html)?.groupValues?.get(1) ?: ""
        if (slug.isEmpty()) {
            MangaFoxLog.w(ETIQUETA, "ficha sin slug de lector: caps sin enlace")
        }
        val lista = mutableListOf<Capitulo>()
        var offset = 0
        while (true) {
            val crudo = web.getHtml(
                "/api_capitulos_manga.php?manga_id=" + idApi +
                    "&offset=" + offset + "&limit=" + CAPITULOS_POR_LOTE +
                    "&orden=desc"
            )
            if (crudo == null) {
                MangaFoxLog.w(ETIQUETA, "lote de capitulos cayo en offset=" + offset)
                break
            }
            val raiz = runCatching { JSONObject(crudo) }.getOrNull() ?: break
            val lote = raiz.optJSONArray("chapters") ?: break
            for (i in 0 until lote.length()) {
                val c = lote.optJSONObject(i) ?: continue
                if (c.optBoolean("is_locked", false)) continue
                val numero = c.optString("number").trim()
                if (numero.isEmpty()) continue
                val etiqueta = c.optString("title").trim()
                    .ifEmpty { "Capítulo " + numero }
                val fecha = c.optString("published_at").trim()
                    .takeIf { it.isNotEmpty() }
                val enlace = if (slug.isEmpty()) "" else
                    "/leer/" + slug + "/capitulo-" + dosDecimales(numero)
                lista.add(Capitulo(numero, etiqueta, fecha, enlace))
            }
            offset += lote.length()
            if (!raiz.optBoolean("has_more") || lote.length() == 0) break
        }
        MangaFoxLog.d(
            ETIQUETA, "capitulos via API: " + lista.size +
                " (lote " + CAPITULOS_POR_LOTE + ")"
        )
        // La API entrega desc: orden de lectura asc.
        return lista.sortedByDescending { it.numero.toFloatOrNull() ?: 0f }
    }

    /** "341" -> "341.00", "340.5" -> "340.50": el formato EXACTO
     *  de los enlaces que el propio sitio pinta (verificado). */
    private fun dosDecimales(numero: String): String = runCatching {
        BigDecimal(numero).setScale(2).toPlainString()
    }.getOrDefault(numero)

    // ------------------------------------------------------------- lector

    override suspend fun cargarPaginasCapitulo(
        detalle: ObraDetalle,
        capitulo: Capitulo
    ): PaginasCapitulo? {
        if (capitulo.enlace.isEmpty()) return PaginasCapitulo(emptyList())
        val t0 = System.currentTimeMillis()
        val html = web.getHtml(capitulo.enlace)
        if (html == null) {
            MangaFoxLog.w(ETIQUETA, "capitulo sin HTML: " + capitulo.enlace)
            return PaginasCapitulo(emptyList())
        }
        val urls = withContext(Dispatchers.IO) { extraerPaginas(html) }
        MangaFoxLog.d(
            ETIQUETA, "capitulo '" + capitulo.titulo + "': " +
                urls.size + " paginas (" +
                (System.currentTimeMillis() - t0) + " ms)"
        )
        return PaginasCapitulo(
            urls,
            referer = BASE.trimEnd('/') + capitulo.enlace,
            // El endpoint /api/imagen-capitulo responde 403 a
            // peticiones que no declaran aceptar imagenes.
            cabeceras = mapOf(
                "Accept" to "image/avif,image/webp,image/apng,image/*,*/*;q=0.8"
            )
        )
    }

    /** imgs reader-page-image: data-verify (BASE64 invertido) o
     *  data-src/data-sec-src/src; los placeholder data: se
     *  descartan. Orden del DOM = orden de lectura. */
    private fun extraerPaginas(html: String): List<String> {
        val urls = mutableListOf<String>()
        Regex("<img[^>]*reader-page-image[^>]*>").findAll(html).forEach { m ->
            val tag = m.value
            val resuelta = Regex("data-verify=\"([^\"]+)\"").find(tag)
                ?.groupValues?.get(1)?.let { decodificarVerify(it) }
                ?: listOf("data-sec-src", "data-src", "src")
                    .firstNotNullOfOrNull { nombre ->
                        Regex(nombre + "\\s*=\\s*\"([^\"]+)\"").find(tag)
                            ?.groupValues?.get(1)?.trim()
                            ?.takeIf { it.isNotEmpty() && !it.startsWith("data:") }
                    }?.let { resolver(it) }
            if (resuelta != null && !urls.contains(resuelta)) urls.add(resuelta)
        }
        return urls
    }

    /** data-verify: BASE64 con el contenido INVERTIDO (igual que
     *  ragna, verificado en vivo por el dueno). */
    private fun decodificarVerify(valor: String): String? {
        val rellenado = valor + "=".repeat((4 - valor.length % 4) % 4)
        val decodificado = runCatching {
            String(java.util.Base64.getDecoder().decode(rellenado))
        }.getOrNull() ?: return null
        val url = decodificado.reversed().trim()
        return if (url.startsWith("http")) url else resolver(url)
    }

    // ------------------------------------------------------------ comunes

    private fun resolver(url: String?): String? {
        val u = url?.trim() ?: return null
        return when {
            u.startsWith("http") -> u
            u.startsWith("//") -> "https:" + u
            u.startsWith("/") -> BASE.trimEnd('/') + u
            u.isNotEmpty() -> BASE.trimEnd('/') + "/" + u
            else -> null
        }
    }

    private fun traducirEstado(crudo: String): String = when {
        crudo.contains("complet", true) -> "Finalizado"
        crudo.contains("pausa", true) || crudo.contains("hiato", true) -> "En pausa"
        crudo.contains("cancel", true) || crudo.contains("aband", true) -> "Cancelado"
        crudo.contains("curso", true) || crudo.contains("emision", true) ||
            crudo.contains("ongoing", true) -> "En curso"
        else -> crudo
    }

    private fun texto(crudo: String?): String? {
        if (crudo == null) return null
        val plano = Regex("<[^>]+>").replace(crudo, " ")
            .replace("&nbsp;", " ").replace("&amp;", "&")
            .replace("&#8211;", "-").replace("&#8217;", "'")
        val res = plano.trim()
        return res.ifEmpty { null }
    }

    private companion object {
        const val ETIQUETA = "Mfx.Jeaz"
        const val BASE = "https://lectorhub.j5z.xyz"
        const val RECOVERY = "https://jeazscans.com"
        const val RITMO_MS = 500L
        const val TTL_HOME_MS = 60_000L
        const val CAPITULOS_POR_LOTE = 30
    }
}
