package com.harley.mangafox.logic.lector

/**
 * El capitulo EXISTE pero la fuente lo declara bloqueado para
 * esta app: VIP, pago o descarga deshabilitada (mangacrab trae
 * is_locked / is_vip_chapter / can_download reales en su API;
 * keiyoushi las respeta igual). Dato REAL de la fuente, no un
 * fallo de red: reintentar NO lo arregla.
 *
 * La lanza el TEMA en cargarPaginasCapitulo; el clasificador de
 * fallos la traduce a su escenario honesto y la pantalla muestra
 * el estado SIN boton de reintento (ley 6).
 */
class CapituloBloqueadoException(
    val motivo: String
) : Exception(motivo)
