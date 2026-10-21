package com.harley.mangafox.logic.directorio

/**
 * Genero de una fuente para el Directorio.
 * [slug] se usa en los filtros de la fuente (?genre=, etc);
 * [nombre] es lo que se muestra en el chip.
 */
data class Genero(
    val slug: String,
    val nombre: String
)
