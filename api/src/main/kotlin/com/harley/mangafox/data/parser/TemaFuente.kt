package com.harley.mangafox.data.parser

import com.harley.mangafox.logic.detalle.ObraDetalle
import com.harley.mangafox.logic.home.ObraHome
import com.harley.mangafox.logic.directorio.Genero
import com.harley.mangafox.logic.detalle.Capitulo
import com.harley.mangafox.logic.lector.PaginasCapitulo

/**
 * Secciones de Home con panel expandible (spec dueno 17:08).
 */
enum class Seccion { RECIENTES, POPULARES, RANKING }

/**
 * Contrato de un TEMA de fuente. Los temas se implementan UNA
 * vez en la app; cualquier extension cuyo manifest declare
 * "theme": "<este tema>" funciona sin tocar mas codigo.
 * Hoy: "madara". Manana: los temas custom de las fuentes restantes.
 */
interface TemaFuente {
    suspend fun cargarRecientes(): List<ObraHome>
    suspend fun cargarPopulares(): List<ObraHome>
    suspend fun cargarRanking(): List<ObraHome>

    /** Generos que la fuente sabe filtrar (Directorio). Default: ninguno. */
    suspend fun generos(): List<Genero> = emptyList()

    /** Pagina [pagina] del directorio, opcionalmente filtrada por
     *  [generoSlug]. Default: sin soporte (directorio vacio). */
    suspend fun cargarDirectorio(
        pagina: Int,
        generoSlug: String?
    ): List<ObraHome> = emptyList()

    /** Busqueda de [texto], paginada. Default: sin soporte. */
    suspend fun buscar(texto: String, pagina: Int): List<ObraHome> = emptyList()

    /** Pagina [pagina] (1-based) del listado de SECCION de Home
     *  (paneles expandibles infinitos, spec dueno 17:08). La
     *  pagina 1 es la que ya pinto la seccion horizontal. Default:
     *  SIN paginacion — cualquier extension nueva funciona sin
     *  romperse: su panel muestra lo inicial y declara fin
     *  honesto (pagina vacia). */
    suspend fun cargarSeccion(
        seccion: Seccion,
        pagina: Int
    ): List<ObraHome> = emptyList()

    /** Ficha completa (sinopsis, estado, generos, capitulos...)
     *  de [obra]: cada tema lee obra.enlace (ruta/URL que ya
     *  viaja en la tarjeta) y puede respaldarse en obra.id y
     *  obra.portadaUrl. Rutas y parseo viven en cada TEMA: la
     *  APK queda universal y una extension nueva con tema
     *  existente la gana gratis. Default: null = la fuente no
     *  trae ficha todavia y la pantalla muestra su estado
     *  honesto. */
    suspend fun cargarDetalle(obra: ObraHome): ObraDetalle? = null

    /** Paginas de un capitulo para el LECTOR (spec 19-oct).
     *  Contrato igual que cargarDetalle:
     *  - null = el tema aun no trae lector: estado honesto propio,
     *    determinista, JAMAS un falso error de red.
     *  - Lista VACIA = el capitulo no tiene paginas hoy: estado
     *    honesto propio ("sin paginas"), tampoco error.
     *  - Fallo de RED -> LANZA (reintento real en pantalla).
     *  - Capitulo BLOQUEADO/VIP de la fuente -> lanza
     *    CapituloBloqueadoException (sin reintento, la verdad).
     *  Cada tema lee capitulo.enlace (URL REAL de la fuente) y
     *  se respalda en detalle.id; declara referer/cabeceras que
     *  sus imagenes exigen (verificado en vivo por tema). */
    suspend fun cargarPaginasCapitulo(
        detalle: ObraDetalle,
        capitulo: Capitulo
    ): PaginasCapitulo? = null

    /** Obras que esta fuente PIDE por llamada en la vista indicada
     *  (0 = no lo declara: el motor no aplica fin temprano y
     *  confirma con pagina vacia). El motor NUNCA lo aprende por
     *  observacion (autopsia 13:51: el aprendizaje cruzaba vistas
     *  — todos local de 96 mato los generos de Olympus de 15/pag
     *  en su propia pagina 1). */
    fun tamanoPaginaPedido(
        generoSlug: String?,
        busqueda: Boolean
    ): Int = 0

    /** True si conviene encadenar lotes hasta el FINAL REAL de la
     *  lista en cuanto el usuario entra (fuentes rapidas con
     *  paginas chicas: API 15/peticion => el fling clava al
     *  usuario en el borde de goma de cada lote). Fuentes con
     *  rate limit agresivo lo dejan en false (solo prefetch por
     *  cercania al borde). Default: false. */
    val prefetchAlFinal: Boolean get() = false
}
