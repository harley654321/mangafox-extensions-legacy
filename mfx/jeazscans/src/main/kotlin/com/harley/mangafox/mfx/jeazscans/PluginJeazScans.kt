package com.harley.mangafox.mfx.jeazscans

import com.harley.mangafox.mfx.api.BasePluginMfx
import com.harley.mangafox.mfx.api.ContextoMfx

/**
 * Punto de entrada del .mfx de Jeaz Scans (lo nombra el
 * manifest.json en pluginClassName). Unica responsabilidad:
 * despertar y registrar la fuente ante el kernel; todo el
 * parseo vive en FuenteJeazScans.
 */
class PluginJeazScans : BasePluginMfx() {
    override fun activar(contexto: ContextoMfx) {
        contexto.log("activando fuente jeazscans")
        registrarFuente(FuenteJeazScans(contexto), contexto)
    }
}
