package com.harley.mangafox.mfx.mhscans

import com.harley.mangafox.mfx.api.BasePluginMfx
import com.harley.mangafox.mfx.api.ContextoMfx

/**
 * Punto de entrada del .mfx de MHScans (lo nombra el
 * manifest.json del paquete en pluginClassName). Su unica
 * responsabilidad es despertar y registrar la fuente ante el
 * kernel; todo el parseo vive en FuenteMhscans.
 */
class PluginMhscans : BasePluginMfx() {
    override fun activar(contexto: ContextoMfx) {
        contexto.log("activando fuente mhscans")
        registrarFuente(FuenteMhscans(contexto), contexto)
    }
}
