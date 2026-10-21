package com.harley.mangafox.data.parser

/**
 * Mini extractor HTML. SIN dependencias (nada de Jsoup):
 * suficiente para el marcado predecible de los CMS de scanlation.
 * Los selectores viven en cada Tema, no aqui: esta clase solo
 * corta y extrae.
 */
object MiniHtml {

    /**
     * Corta [html] en bloques que empiezan donde aparece [marca]
     * (ej. "page-item-detail") y terminan donde empieza el siguiente
     * bloque o un tope de seguridad de 5000 caracteres.
     */
    fun bloques(html: String, marca: String): List<String> {
        val inicios = mutableListOf<Int>()
        var i = html.indexOf(marca)
        while (i >= 0) {
            inicios.add(i)
            i = html.indexOf(marca, i + marca.length)
        }
        return inicios.mapIndexed { n, ini ->
            val fin = if (n + 1 < inicios.size) inicios[n + 1]
                      else minOf(html.length, ini + 5000)
            html.substring(ini, fin)
        }
    }

    /** Primer valor del atributo [nombre] en el bloque, o null. */
    fun atributo(bloque: String, nombre: String): String? {
        val regex = Regex(nombre + "\\s*=\\s*\"([^\"]*)\"")
        return regex.find(bloque)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }
    }

    /** href y title del primer <a ...> del bloque. */
    fun enlaceYTitulo(bloque: String): Pair<String?, String?> {
        val a = Regex("<a\\s[^>]*>").find(bloque)?.value ?: return null to null
        return atributo(a, "href") to atributo(a, "title")
    }

    /** Primera imagen util: prioriza data-src; ignora URIs data:. */
    fun imagen(bloque: String): String? {
        val candidatas = Regex("(?:data-src|src)\\s*=\\s*\"([^\"]+)\"")
            .findAll(bloque).map { it.groupValues[1] }.toList()
        return candidatas.firstOrNull { !it.startsWith("data:") }
    }

    /** Ultimo segmento de la ruta de una URL: el slug. */
    fun slugDe(url: String): String {
        val limpio = url.trimEnd('/')
        return limpio.substringAfterLast('/')
    }
}
