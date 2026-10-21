package com.harley.mangafox.debug

import android.util.Log

/**
 * Logger central del equipo. Infraestructura de observabilidad:
 * no es logica de negocio, solo depuracion.
 *
 * REGISTRO TOTAL (requisito del dueño, 13-oct): TODA linea lleva
 * un reloj de sesion (t=+X.Xs) desde el primer uso de la app, para
 * leer la secuencia completa de un vistazo sin restar epoch a
 * mano: cada accion, respuesta de red, pintado y estado queda
 * cronometrado en una sola linea de tiempo.
 *
 * Convencion de fuentes (tags):
 *  - UI.*        = lo que el USUARIO hace y lo que se DIBUJA
 *                  (gestos, toques, filas pintadas, overlays)
 *  - Orchestr.*  = decisiones y estado interno (VM)
 *  - Data.*      = red, parseo, cache, imagenes
 * Comparar UI.* contra Orchestr.* revela cualquier desfase entre
 * la pantalla y la logica.
 */
object MangaFoxLog {
    private const val TAG = "MangaFox"

    /** Arranque de la sesion de logs (primer uso del logger). */
    private val arranque = System.currentTimeMillis()

    /** Segundos transcurridos desde [arranque], 1 decimal. */
    fun t(): String =
        "t=+" + String.format(
            java.util.Locale.US, "%.1f",
            (System.currentTimeMillis() - arranque) / 1000.0
        ) + "s"

    /** Cronometro para medir duraciones de acciones puntuales. */
    fun ahora(): Long = System.currentTimeMillis()

    fun d(fuente: String, mensaje: String) =
        Log.d(TAG, "[" + fuente + "] (" + t() + ") " + mensaje)

    fun w(fuente: String, mensaje: String) =
        Log.w(TAG, "[" + fuente + "] (" + t() + ") " + mensaje)

    fun e(fuente: String, mensaje: String, error: Throwable? = null) =
        if (error != null) Log.e(TAG, "[" + fuente + "] (" + t() + ") " + mensaje, error)
        else Log.e(TAG, "[" + fuente + "] (" + t() + ") " + mensaje)
}
