package com.harley.mangafox.logic.lector

/**
 * Paginas de un capitulo listas para el motor de descarga del
 * lector. Puro: sin Android, sin red, sin UI.
 *
 * Lo devuelve el TEMA (contrato cargarPaginasCapitulo) con las
 * URLs REALES verificadas de la fuente y el transporte que esas
 * imagenes exigen:
 *  - [referer]: cabecera Referer que el servidor pide para
 *    servirlas (manhwaweb responde 403 sin ella; madara la
 *    quiere como cortesia de lectura; ambas VERIFICADAS en vivo).
 *  - [cabeceras]: cabeceras extra por imagen (mangacrab exige
 *    "fansy: <token>" cuando el capitulo declara security
 *    habilitada; sin ella Cloudflare responde 403 — verificado
 *    en vivo 19-oct, log de la investigacion).
 */
data class PaginasCapitulo(
    val urls: List<String>,
    val referer: String? = null,
    val cabeceras: Map<String, String> = emptyMap()
)
