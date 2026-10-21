package com.harley.mangafox.mfx.api

import android.content.Context
import com.harley.mangafox.data.web.ClienteWeb
import com.harley.mangafox.debug.MangaFoxLog
import org.json.JSONObject

/**
 * CONTRATO MFX v1 — LA SUPERFICIE CONGELADA DE LA API.
 *
 * Todo lo que una extension .mfx compila contra y ejecuta en
 * runtime es lo que hay en ESTE paquete (mas los modelos de
 * logic/ y la interfaz TemaFuente, ver DISENO-MFX.md). Regla
 * de oro del kernel (dueno 21-oct): este paquete se congela
 * con el lanzamiento de la apk. Cambios que rompan firmas
 * exigen apiVersion 2 y un paquete api.v2 nuevo; JAMAS una
 * edicion silenciosa (autopsia Cloudstream: su apiVersion
 * existe pero no se valida y sus plugins viejos mueren con
 * NoSuchMethodError sin aviso).
 */
const val API_MFX_VERSION: Int = 1

/**
 * Manifest dentro del .mfx (ZIP). Lo genera el pipeline del
 * repo de extensiones y lo lee CargadorMfx via
 * getResourceAsStream — igual que el manifest.json embebido
 * del .cs3 de Cloudstream (PluginManager.kt:613).
 *
 * [apiVersion] se VALIDA de verdad en el kernel: desajuste =
 * rechazo con log, no crash misterioso.
 */
data class ManifiestoMfx(
    /** Id de la extension (ej: "mhscans"). Debe coincidir con
     *  el id del index del repo: es la llave de vinculo. */
    val id: String,
    val nombre: String,
    val version: Int,
    val apiVersion: Int,
    /** Clase que extiende BasePluginMfx, punto de entrada. */
    val pluginClassName: String
) {
    companion object {
        fun desdeJson(texto: String): ManifiestoMfx? = try {
            val j = JSONObject(texto)
            ManifiestoMfx(
                id = j.optString("id"),
                nombre = j.optString("name"),
                version = j.optInt("version", 0),
                apiVersion = j.optInt("apiVersion", 0),
                pluginClassName = j.optString("pluginClassName")
            )
        } catch (e: Exception) {
            MangaFoxLog.e("Mfx.Manifest", "manifest ilegible", e)
            null
        }
    }
}

/**
 * Regalo del kernel a la extension: la maquinaria de la apk
 * que el plugin puede usar. Las extensiones NO traen su propio
 * HTTP: usan el ClienteWeb del kernel (cortesia DominioVivo,
 * cortesia de host y reintentos incluidos). Asi TODA fuente
 * instalada respeta la misma politica de red de la app.
 *
 * [contexto] se expone solo para operaciones seguras que la
 * UI de ajustes de extensiones pueda necesitar (fase 2); el
 * kernel nunca lo restringe hoy (Cloudstream no aisla clases:
 * decision documentada en DISENO-MFX.md, seccion seguridad).
 */
class ContextoMfx internal constructor(
    val contexto: Context,
    private val registrar: (FuenteMfx, String) -> Unit
) {
    /** Cliente HTTP del kernel para el sitio de la fuente. */
    fun clienteWeb(
        baseUrl: String,
        ritmoMs: Long = 0L,
        recoveryUrl: String = ""
    ): ClienteWeb = ClienteWeb(baseUrl, ritmoMs, recoveryUrl)

    /** Log forense con el reloj de sesion de toda la app. */
    fun log(mensaje: String) = MangaFoxLog.d("Mfx.Plugin", mensaje)

    /** Registra la fuente; solo callable desde BasePluginMfx. */
    internal fun registrarFuente(fuente: FuenteMfx, archivo: String) =
        registrar(fuente, archivo)
}

/**
 * Contrato de una FUENTE que vive dentro de un .mfx. Es un
 * TemaFuente completo (home, directorio, busqueda, ficha,
 * lector: la apk no sabe de sitios, sabe pedir) mas la
 * identidad [id] que la vincula con su entrada del index.
 */
interface FuenteMfx : com.harley.mangafox.data.parser.TemaFuente {
    /** Id de la extension que la contiene ("mhscans"...). */
    val id: String
}

/**
 * Punto de entrada del .mfx (el manifest lo nombra en
 * pluginClassName). Espejo del BasePlugin de Cloudstream
 * (registerMainAPI/filename) con una diferencia clave: el
 * registro pasa SIEMPRE por el kernel (ContextoMfx), que es
 * quien sabe desde que archivo se cargo y quien podra
 * retirarlo limpio en el unload.
 */
abstract class BasePluginMfx {
    /** Ruta absoluta del .mfx; la fija el cargador. */
    internal var archivoMfx: String = ""

    /** Coordinar registro de fuentes y recursos propios. */
    open fun activar(contexto: ContextoMfx) {}

    /** Registra una fuente ante el kernel. */
    protected fun registrarFuente(fuente: FuenteMfx, contexto: ContextoMfx) {
        contexto.registrarFuente(fuente, archivoMfx)
    }
}
