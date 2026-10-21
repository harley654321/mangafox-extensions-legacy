package com.harley.mangafox.logic.detalle

/**
 * Ficha completa de una obra para la pantalla de detalle.
 * Puro: sin Android, sin red, sin UI.
 *
 * Todo campo que una fuente no trae queda en su valor neutro
 * (null / vacio) y la pantalla lo OCULTA: jamas se pinta un
 * "N/A" ni un dato inventado (regla del dueno: cero datos
 * falsos). La portada y el titulo son los de la tarjeta origen
 * (ObraHome) como respaldo si la fuente no repite el dato.
 */
data class ObraDetalle(
    val id: String,
    val titulo: String,
    val portadaUrl: String = "",
    val sinopsis: String = "",
    /** "En curso", "Finalizado", "Activo"... tal cual la fuente. */
    val estado: String? = null,
    val anio: Int? = null,
    /** Nota/valoracion de la obra (p.ej. 8.7). Null = la fuente no puntua. */
    val nota: Float? = null,
    val generos: List<String> = emptyList(),
    val capitulos: List<Capitulo> = emptyList(),
    /** True = la ficha se entrego con lo que hubo: alguna pagina
     *  de capitulos fallo tras sus reintentos (red inestable) y
     *  la APK debe ofrecer reanudar SIN tirar lo ya cargado.
     *  Fuente universal: la extension lo declara, la APK actua. */
    val capitulosIncompletos: Boolean = false
)

/**
 * Un capitulo de la obra. [enlace] es la URL REAL de lectura en
 * la web de la fuente (o su ruta local): la consumira el lector
 * cuando exista; la pantalla de detalle solo la lista.
 */
data class Capitulo(
    /** Indice de lectura en orden ascendente: "1", "2", "0"... */
    val numero: String,
    /** Etiqueta tal cual la fuente: "Capitulo 25". */
    val titulo: String,
    /** Fecha de publicacion ya formateada para mostrar, o null. */
    val fecha: String? = null,
    val enlace: String = ""
)
