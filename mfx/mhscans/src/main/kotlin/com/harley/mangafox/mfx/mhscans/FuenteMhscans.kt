package com.harley.mangafox.mfx.mhscans

import com.harley.mangafox.data.parser.MiniHtml
import com.harley.mangafox.data.parser.Seccion
import com.harley.mangafox.data.parser.TemaFuente
import com.harley.mangafox.data.web.ClienteWeb
import com.harley.mangafox.debug.MangaFoxLog
import com.harley.mangafox.logic.detalle.Capitulo
import com.harley.mangafox.logic.detalle.ObraDetalle
import com.harley.mangafox.logic.directorio.Genero
import com.harley.mangafox.logic.home.ObraHome
import com.harley.mangafox.logic.lector.PaginasCapitulo
import com.harley.mangafox.mfx.api.ContextoMfx
import com.harley.mangafox.mfx.api.FuenteMfx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * EXTENSION MFX mhscans (piloto del sistema .mfx).
 *
 * Parser AUTOCONTENIDO del CMS Madara para MHScans, portado del
 * TemaMadara de la apk (validado en vivo contra el sitio real
 * durante octubre-2026: sitemap de generos, ajax96 con page
 * 0-based, eco detectado y calibrado, busqueda ajax, ficha
 * completa con capitulos y lector page-break con referer).
 * Vive FUERA de la apk: si el sitio rediseña, se publica un
 * .mfx nuevo desde el repo de extensiones sin tocar la app.
 *
 * Reglas de red del sitio (evidencia keiyoushi + pruebas):
 * rateLimit 1 peticion / 3 s (Cloudflare propio: una rafaga
 * tumba el acceso ~24 h) y aterrizaje de recuperacion
 * https://mhscans.com (anuncia el dominio vigente).
 */
class FuenteMhscans(contexto: ContextoMfx) : FuenteMfx {

    override val id: String = "mhscans"

    private val web: ClienteWeb = contexto.clienteWeb(BASE, RITMO_MS, RECOVERY)

    // ------------------------------------------------------------ generos

    private var generosCache: List<Genero>? = null

    override suspend fun generos(): List<Genero> {
        generosCache?.let { return it }
        val xml = web.getHtml("/wp-sitemap-taxonomies-wp-manga-genre-1.xml")
        val delSitio = if (xml == null) null else
            Regex("/manga-genre/([a-z0-9_-]+)/")
                .findAll(xml)
                .map { it.groupValues[1] }
                .distinct()
                .map { slug -> Genero(slug, NOMBRES_GENERO[slug] ?: bonito(slug)) }
                .toList()
        val res = if (delSitio.isNullOrEmpty()) GENEROS_RESPALDO else delSitio
        generosCache = res
        MangaFoxLog.d("Mfx.Mhscans", "generos: " + res.size + " (sitio o respaldo)")
        return res
    }

    // ------------------------------------------------------------ directorio

    private var claveListado = ""
    private var tamanoHtmlListado = 0
    private var ecoDetectado = false
    private var primerSlugAjax = ""

    override val prefetchAlFinal: Boolean get() = true

    override fun tamanoPaginaPedido(
        generoSlug: String?,
        busqueda: Boolean
    ) = if (ecoDetectado) tamanoHtmlListado else AJAX_POR_PAGINA

    override suspend fun cargarDirectorio(
        pagina: Int,
        generoSlug: String?
    ): List<ObraHome> {
        val clave = generoSlug ?: "todos"
        if (pagina <= 1 || clave != claveListado) {
            claveListado = clave
            tamanoHtmlListado = 0
            ecoDetectado = false
            primerSlugAjax = ""
            val ajax = try {
                listarAjax96(generoSlug, 0)
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                MangaFoxLog.w("Mfx.Mhscans", "lote ajax cayo, calzo HTML: " + e.message)
                emptyList()
            }
            if (ajax.isNotEmpty()) {
                primerSlugAjax = ajax[0].id
                MangaFoxLog.d("Mfx.Mhscans", "lote 1 via ajax: " + ajax.size + " obras")
                return ajax
            }
            val lista = parsear(web.getHtml(rutaListado(generoSlug, 1)))
            tamanoHtmlListado = lista.size
            ecoDetectado = true
            return lista
        }
        if (!ecoDetectado) {
            val ajax = listarAjax96(generoSlug, pagina - 1)
            if (ajax.isEmpty()) return emptyList()
            if (pagina == 2 && ajax[0].id == primerSlugAjax) {
                ecoDetectado = true
                MangaFoxLog.w("Mfx.Mhscans", "eco ajax: calibro HTML")
            } else {
                MangaFoxLog.d("Mfx.Mhscans", "lote " + pagina + " via ajax: " + ajax.size)
                return ajax
            }
        }
        if (tamanoHtmlListado == 0) {
            tamanoHtmlListado = parsear(web.getHtml(rutaListado(generoSlug, 1))).size
        }
        val paginasCubiertas = if (tamanoHtmlListado > 0)
            (AJAX_POR_PAGINA + tamanoHtmlListado - 1) / tamanoHtmlListado else 1
        return parsear(web.getHtml(rutaListado(generoSlug, paginasCubiertas + pagina - 1)))
    }

    private fun rutaListado(generoSlug: String?, pagina: Int): String = when {
        generoSlug == null && pagina <= 1 -> "/manga/"
        generoSlug == null -> "/manga/page/$pagina/"
        pagina <= 1 -> "/manga-genre/$generoSlug/"
        else -> "/manga-genre/$generoSlug/page/$pagina/"
    }

    /** Lote admin-ajax de 96 obras, page 0-based (validado en vivo
     *  en MHScans: page SI pagina; vars[paged] es ignorado). */
    private suspend fun listarAjax96(
        generoSlug: String?,
        page: Int
    ): List<ObraHome> {
        val params = ajaxBase().toMutableMap()
        params["vars[posts_per_page]"] = AJAX_POR_PAGINA.toString()
        params["page"] = page.toString()
        params["vars[paged]"] = (page + 1).toString()
        if (generoSlug != null) {
            params["vars[tax_query][0][taxonomy]"] = "wp-manga-genre"
            params["vars[tax_query][0][field]"] = "slug"
            params["vars[tax_query][0][terms]"] = generoSlug
        }
        val html = web.postForm("/wp-admin/admin-ajax.php", params)
            ?: throw IllegalStateException("ajax sin respuesta")
        return parsear(html)
    }

    override suspend fun buscar(texto: String, pagina: Int): List<ObraHome> {
        val q = texto.trim()
        if (q.isEmpty()) return emptyList()
        val params = ajaxBase().toMutableMap()
        params["vars[s]"] = q
        params["page"] = (pagina - 1).toString()
        params["vars[paged]"] = pagina.toString()
        val html = web.postForm("/wp-admin/admin-ajax.php", params) ?: return emptyList()
        return parsear(html)
    }

    private fun ajaxBase(): Map<String, String> = linkedMapOf(
        "action" to "madara_load_more",
        "template" to "madara-core/content/content-archive",
        "vars[paged]" to "1",
        "vars[template]" to "archive",
        "vars[posts_per_page]" to "50",
        "vars[post_type]" to "wp-manga",
        "vars[post_status]" to "publish",
        "vars[manga_archives_item_layout]" to "big_thumbnail"
    )

    // ------------------------------------------------------------ home

    override suspend fun cargarRecientes(): List<ObraHome> =
        parsear(web.getHtml("/manga/?m_orderby=latest"))

    override suspend fun cargarPopulares(): List<ObraHome> =
        parsear(web.getHtml("/manga/?m_orderby=views"))

    // RANKING = rating REAL (decision del dueno 21-oct: views era un
    // disfraz de Populares y duplicaba sus paginas al sitio; el sitio
    // soporta m_orderby=rating, lista distinta verificada en vivo).
    override suspend fun cargarRanking(): List<ObraHome> =
        parsear(web.getHtml("/manga/?m_orderby=rating"))

    override suspend fun cargarSeccion(
        seccion: Seccion,
        pagina: Int
    ): List<ObraHome> {
        val (base, pag) = when (seccion) {
            Seccion.RECIENTES -> "/manga/?m_orderby=latest" to pagina
            Seccion.POPULARES -> "/manga/?m_orderby=views" to pagina
            Seccion.RANKING -> "/manga/?m_orderby=rating" to pagina
        }
        val ruta = if (pag <= 1) base
        else base.replace("/manga/", "/manga/page/" + pag + "/")
        return parsear(web.getHtml(ruta))
    }

    // ------------------------------------------------------------ parseo listado

    private suspend fun parsear(html: String?): List<ObraHome> =
        withContext(Dispatchers.IO) {
            if (html == null) return@withContext emptyList()
            val marcaBloque = if (html.contains("page-item-detail")) "page-item-detail"
                              else "c-tabs-item__content"
            val obras = MiniHtml.bloques(html, marcaBloque).mapNotNull { bloque ->
                val (href, titulo) = extraerEnlace(bloque) ?: return@mapNotNull null
                ObraHome(
                    id = MiniHtml.slugDe(href),
                    titulo = titulo.trim(),
                    portadaUrl = MiniHtml.imagen(bloque) ?: "",
                    etiqueta = null,
                    enlace = href
                )
            }.filter { obra ->
                val enSlug = obra.id.lowercase().contains("mhscans")
                val enTitulo = obra.titulo.lowercase().contains("mhscans")
                !(enSlug || enTitulo)
            }.distinctBy { it.id }
            MangaFoxLog.d("Mfx.Mhscans", "parseados " + obras.size + " items")
            obras
        }

    private fun extraerEnlace(bloque: String): Pair<String, String>? {
        val (href, tituloAttr) = MiniHtml.enlaceYTitulo(bloque)
        if (href != null && !tituloAttr.isNullOrBlank()) return href to tituloAttr
        Regex(
            "<h[1-4][^>]*>.{0,300}?<a\\s[^>]*href=\"([^\"]+)\"[^>]*>\\s*([^<]+?)\\s*</a>",
            RegexOption.DOT_MATCHES_ALL
        ).find(bloque)?.let { return it.groupValues[1] to it.groupValues[2] }
        if (href != null) {
            val texto = Regex(">\\s*([^<>]+)\\s*</a>").find(bloque)?.groupValues?.get(1)
            if (!texto.isNullOrBlank()) return href to texto
        }
        return null
    }

    // ------------------------------------------------------------ ficha

    override suspend fun cargarDetalle(obra: ObraHome): ObraDetalle? {
        val enlace = obra.enlace
        if (enlace.isBlank()) return null
        val ruta = if (enlace.startsWith("http")) {
            try { java.net.URI(enlace).path ?: return null }
            catch (e: Exception) { return null }
        } else enlace
        val t0 = System.currentTimeMillis()
        val html = web.getHtml(ruta) ?: run {
            MangaFoxLog.w("Mfx.Mhscans", "ficha sin HTML: " + ruta)
            return null
        }
        return withContext(Dispatchers.IO) { extraerFicha(html, obra) }.also {
            MangaFoxLog.d("Mfx.Mhscans", "ficha '" + obra.titulo + "': " +
                (it?.capitulos?.size ?: 0) + " caps (" +
                (System.currentTimeMillis() - t0) + " ms)")
        }
    }

    private fun extraerFicha(html: String, obra: ObraHome): ObraDetalle? {
        val titulo = texto(
            Regex(
                "<div class=\"post-title\"[^>]*>(.{0,600}?)<h[1-4][^>]*>(.*?)</h[1-4]>",
                RegexOption.DOT_MATCHES_ALL
            ).find(html)?.groupValues?.get(2)
        ) ?: return null
        val sinopsis = texto(
            Regex(
                "<div[^>]*class=\"[^\"]*summary__content[^\"]*\"[^>]*>(.*?)</div>",
                RegexOption.DOT_MATCHES_ALL
            ).find(html)?.groupValues?.get(1)
        ).orEmpty()
        val portada = Regex(
            "class=\"summary_image\".{0,400}?(?:data-src|src)\\s*=\\s*\"([^\"]+)\"",
            RegexOption.DOT_MATCHES_ALL
        ).find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            ?: obra.portadaUrl
        var estado: String? = null
        var anio: Int? = null
        var generos: List<String> = emptyList()
        Regex(
            "<div class=\"post-content_item\">\\s*<div class=\"summary-heading\">" +
                ".{0,120}?<h5>([^<]*)</h5>.*?<div class=\"summary-content\">(.*?)</div>",
            RegexOption.DOT_MATCHES_ALL
        ).findAll(html).forEach { m ->
            val clave = m.groupValues[1].trim().lowercase()
            val valor = texto(m.groupValues[2]) ?: return@forEach
            when {
                clave.contains("genre") -> generos = valor.split(',')
                    .map { it.trim() }.filter { it.isNotEmpty() }
                clave.contains("status") -> estado = traducirEstado(valor)
                clave.contains("release") -> anio = valor.toIntOrNull()
            }
        }
        val capitulos = extraerCapitulos(html)
        MangaFoxLog.d("Mfx.Mhscans", "ficha parseada: " + capitulos.size + " caps")
        return ObraDetalle(
            id = obra.id,
            titulo = titulo,
            portadaUrl = portada,
            sinopsis = sinopsis,
            estado = estado,
            anio = anio,
            generos = generos,
            capitulos = capitulos
        )
    }

    private fun extraerCapitulos(html: String): List<Capitulo> {
        val lista = mutableListOf<Capitulo>()
        Regex(
            "<li class=\"wp-manga-chapter[^\"]*\"[^>]*>(.*?)</li>",
            RegexOption.DOT_MATCHES_ALL
        ).findAll(html).forEach { li ->
            val bloque = li.groupValues[1]
            val href = Regex("<a\\s[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>",
                RegexOption.DOT_MATCHES_ALL).find(bloque) ?: return@forEach
            val url = href.groupValues[1].trim()
            val etiqueta = texto(href.groupValues[2]) ?: return@forEach
            if (url.isEmpty() || url == "#" || etiqueta.isEmpty()) return@forEach
            val fecha = texto(
                Regex("chapter-release-date\">\\s*<i[^>]*>([^<]*)</i>")
                    .find(bloque)?.groupValues?.get(1)
            )
            val numero = etiqueta.substringAfterLast(' ')
                .takeIf { it.isNotEmpty() } ?: etiqueta
            lista.add(Capitulo(numero, etiqueta, fecha, url))
        }
        return lista.asReversed()
    }

    // ------------------------------------------------------------ lector

    override suspend fun cargarPaginasCapitulo(
        detalle: ObraDetalle,
        capitulo: Capitulo
    ): PaginasCapitulo? {
        val enlace = capitulo.enlace
        if (enlace.isBlank()) return PaginasCapitulo(emptyList())
        val ruta = if (enlace.startsWith("http")) {
            try { java.net.URI(enlace).path ?: return PaginasCapitulo(emptyList()) }
            catch (e: Exception) { return PaginasCapitulo(emptyList()) }
        } else enlace
        val html = web.getHtml(ruta) ?: run {
            MangaFoxLog.w("Mfx.Mhscans", "capitulo sin HTML: " + ruta)
            return PaginasCapitulo(emptyList())
        }
        val urls = withContext(Dispatchers.IO) { extraerPaginas(html) }
        MangaFoxLog.d("Mfx.Mhscans", "capitulo '" + capitulo.titulo + "': " +
            urls.size + " paginas")
        return PaginasCapitulo(urls, referer = BASE.trimEnd('/') + ruta)
    }

    private fun extraerPaginas(html: String): List<String> {
        val urls = mutableListOf<String>()
        for (bloque in MiniHtml.bloques(html, "page-break")) {
            val img = Regex("<img\\s[^>]*>").find(bloque)?.value ?: continue
            val url = urlDeImagen(img) ?: continue
            val resuelta = resolver(url)
            if (!urls.contains(resuelta)) urls.add(resuelta)
        }
        if (urls.isNotEmpty()) return urls
        if (!html.contains("reading-content")) return urls
        for (img in Regex("<img\\s[^>]*>").findAll(html)) {
            val url = urlDeImagen(img.value) ?: continue
            val resuelta = resolver(url)
            if (!urls.contains(resuelta)) urls.add(resuelta)
        }
        return urls
    }

    private fun urlDeImagen(img: String): String? {
        for (nombre in listOf(
            "data-src", "data-lazy-src", "data-lzl-src",
            "data-cfsrc", "data-manga-src", "srcset", "src"
        )) {
            val valor = Regex("[\\s']" + nombre + "\\s*=\\s*\"([^\"]*)\"")
                .find(img)?.groupValues?.get(1)?.trim() ?: continue
            if (valor.isEmpty() || valor.startsWith("data:")) continue
            if (nombre == "srcset") return descriptorMayor(valor)
            return valor
        }
        return null
    }

    private fun descriptorMayor(srcset: String): String? {
        var mejor: String? = null
        var mejorDesc = -1
        for (candidato in srcset.split(',')) {
            val partes = candidato.trim().split(Regex("\\s+"))
            val url = partes.firstOrNull() ?: continue
            if (url.isEmpty()) continue
            val desc = partes.getOrNull(1)?.trimEnd('w', 'h')?.toIntOrNull() ?: 0
            if (desc >= mejorDesc) { mejor = url; mejorDesc = desc }
        }
        return mejor
    }

    private fun resolver(url: String): String = when {
        url.startsWith("http") -> url
        url.startsWith("//") -> "https:" + url
        url.startsWith("/") -> BASE.trimEnd('/') + url
        else -> url
    }

    // ------------------------------------------------------------ comunes

    private fun traducirEstado(crudo: String): String = when (crudo.lowercase()) {
        "ongoing" -> "En curso"
        "on going" -> "En curso"
        "completed" -> "Finalizado"
        "end" -> "Finalizado"
        "hiatus" -> "En pausa"
        "on hold" -> "En pausa"
        "canceled", "cancelled" -> "Cancelado"
        "dropped" -> "Abandonado"
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

    private fun bonito(slug: String): String =
        slug.replace('-', ' ').split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ") { palabra ->
                palabra.replaceFirstChar { c -> c.uppercaseChar() }
            }

    private companion object {
        const val BASE = "https://mhscans.com"
        const val RITMO_MS = 3000L
        const val RECOVERY = "https://mhscans.com"
        const val AJAX_POR_PAGINA = 96

        /** Nombres visibles de los slugs de genero de MHScans. */
        val NOMBRES_GENERO: Map<String, String> = mapOf(
            "accion" to "Acción",
            "artes-marciales" to "Artes marciales",
            "aventura" to "Aventura",
            "ciencia-ficcion" to "Ciencia ficción",
            "comedia" to "Comedia",
            "comic" to "Cómic",
            "cooks" to "Infidelidad",
            "deporte" to "Deporte",
            "drama_" to "Drama",
            "fantasia" to "Fantasía",
            "historia" to "Historia",
            "horror" to "Horror",
            "josei_" to "Josei",
            "live-action" to "Live action",
            "manga" to "Manga",
            "manhua" to "Manhua",
            "manhwa_" to "Manhwa",
            "mecha" to "Mecha",
            "misterio" to "Misterio",
            "one-shot" to "One shot",
            "psicologico" to "Psicológico",
            "recuerdos-de-la-vida" to "Recuerdos de la vida",
            "romance" to "Romance",
            "seinen" to "Seinen",
            "shoujo" to "Shoujo",
            "shounen" to "Shounen",
            "supernatural" to "Supernatural",
            "vida-escolar" to "Vida escolar"
        )

        /** Respaldo honesto si el sitemap cayera: los mismos
         *  generos oficiales del sitio como slugs. */
        val GENEROS_RESPALDO: List<Genero> = NOMBRES_GENERO.entries
            .map { (slug, nombre) -> Genero(slug, nombre) }
    }
}
