package com.harley.mangafox.logic.home

/**
 * Modelo de dominio de una obra/manga para la pantalla Inicio.
 * Puro: sin Android, sin red, sin UI.
 */
data class ObraHome(
    val id: String,
    val titulo: String,
    val portadaUrl: String,
    /** Etiqueta opcional: "Cap. 158", "#3", etc. */
    val etiqueta: String? = null,
    /** Ruta local de la ficha: /series/comic-{slug} */
    val enlace: String = ""
)
