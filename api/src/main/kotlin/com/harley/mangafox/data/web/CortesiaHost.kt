package com.harley.mangafox.data.web

import com.harley.mangafox.debug.MangaFoxLog
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/**
 * LIMITADOR GLOBAL POR HOST (autopsia 14:26 + incidente mhscans
 * 22:42): la cortesia es del SITIO, no de quien la pida. PAGINAS
 * y PORTADAS comparten la MISMA franja por host: si la fuente
 * exige 1 peticion cada N ms (ej. mhscans 1/3 s, verificado en
 * keiyoushi/extensions-source), la rafaga de portadas de un Home
 * ya no puede quemar al Cloudflare del sitio y tumbar TODO el
 * acceso del dispositivo por ~50 s (SocketTimeoutException en
 * cada ficha y directorio mientras el resto de fuentes seguia
 * sano: evidencia del log 22:31).
 *
 * Franjas espaciadas por INICIO de peticion; estampadas dentro
 * del lock SOLO AL DISPARAR (autopsia 03:41: reservar antes de
 * dormir dejaba franjas fantasma). Cancelar durante la espera
 * no deja rastro.
 */
object CortesiaHost {

    private val CANDADO = Any()
    private val ultimas = HashMap<String, Long>()

    /** Espera la franja de [host] espaciada [intervaloMs].
     *  Intervalo <= 0 = sin cortesia (host sin ritmo declarado). */
    suspend fun esperar(host: String, intervaloMs: Long) {
        if (intervaloMs <= 0L || host.isEmpty()) return
        while (true) {
            val espera = synchronized(CANDADO) {
                val ahora = System.currentTimeMillis()
                val libre = (ultimas[host] ?: 0L) + intervaloMs
                if (libre <= ahora) {
                    ultimas[host] = ahora
                    0L
                } else {
                    libre - ahora
                }
            }
            if (espera == 0L) break
            MangaFoxLog.d("Data.Web",
                "rate [" + host + "]: espera " + espera + " ms")
            delay(espera)
        }
        currentCoroutineContext().ensureActive()
    }
}
