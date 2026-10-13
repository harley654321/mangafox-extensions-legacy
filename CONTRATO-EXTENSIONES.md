# CONTRATO DE EXTENSIONES MANGAFOX (v1)

La APK es un **organismo congelado**: una vez finalizada, no se toca.
Toda extensión nueva es **100% datos** en este repo y la APK congelada
la hace funcionar por sí sola, como un camaleón. Este documento es el
contrato que toda extensión debe cumplir para funcionar PERFECTA.

Una extensión que no cumpla este contrato es un cuerpo extraño:
no funciona NI debe funcionar (las extensiones de Aniyomi/Mihon/u
otras APKs no sirven aquí; el formato es propio y exclusivo).

## Reglas de oro

1. Solo DATOS, cero diseño, cero código: toda la experiencia
   corre dentro de la APK.
2. La extensión DESCRIBE; la APK INTERPRETA. El manifest declara
   la realidad de la fuente; la APK habla el idioma del contrato.
3. Cortesía con la fuente SIEMPRE: rateLimits obligatorios y
   honestos. Una extensión que martillea un sitio se degrada a
   status "broken" en el índice.
4. contentRating: "safe" por defecto. Fuentes con material adulto
   lo declaran ("adult") y el manifest debe traer el filtro que
   excluya adultos de las vistas generales (ej. ManhwaWeb:
   `erotico=no` server-side).
5. Todo cambio de manifest sube `version` (+1). El índice manda.

## Estructura del repo

```
index.json                      <- catálogo único (apiVersion 1)
extensions/<id>/manifest.json   <- la extensión misma
```

## index.json: campos por extensión

| Campo     | Regla                                              |
|-----------|----------------------------------------------------|
| `id`      | slug único, minúsculas, jamás cambia               |
| `name`    | nombre visible                                     |
| `lang`    | idioma (es, en, ...)                               |
| `version` | entero, +1 en cada cambio                          |
| `apiVersion` | debe calzar con la APK (hoy: 1)                 |
| `status`  | "broken" | "alpha" | "beta" | "stable"            |
| `manifest`| ruta al manifest.json de la extensión             |
| `icon`    | URL del icono (webp/jpg/png, NO favicon .ico)      |
| `baseUrl` | dominio público de la fuente                       |
| `theme`   | tema de parser que habla (ver abajo)               |
| `apiUrl`  | SOLO fuentes mixtas: backend JSON propio           |

## manifest.json: campos

```json
{
  "id": "...", "name": "...", "lang": "es",
  "version": 1, "apiVersion": 1,
  "contentRating": "safe",
  "status": "beta",
  "network": {
    "baseUrl": "https://...",
    "domainRecoveryUrl": "https://...",   // respaldo si el dominio muere
    "rateLimits": [ { "host": "main", "requests": 1, "seconds": 3 } ]
  },
  "content": { "seriesType": "comic" },
  "theme": "..."
}
```

Obligatorios: `network.baseUrl`, `network.rateLimits`, `theme`,
`contentRating`. Los rateLimits se expresan por host (`main`,
`panel`, `api`); la APK los respeta mecánicamente.

## Temas disponibles (el corazón del contrato)

La APK trae los TEMAS quemados (parsers genéricos). Una fuente
nueva que pertenezca a una de estas familias funciona al
instante declarando su `theme`:

| Tema              | Familia                       | Qué hereda de la APK |
|-------------------|-------------------------------|----------------------|
| `madara`          | WordPress Madara (HTML+ajax)  | ajax96 por lote, eco-detección con calibración HTML, prefetch al final |
| `mangacrab`       | API JSON WordPress propia     | per_page=50, géneros por slug |
| `manhwaweb`       | API JSON backend Railway      | 30 items/pág, filtro erotico=no, búsqueda server-side |
| `olympusscanlation` | id dedicado (API propia)   | catálogo local 96, búsqueda 20, géneros API 15 |

Regla de pertenencia: la fuente debe responder EXACTAMENTE como
su familia (mismos endpoints y estructuras). Si la página es de
una familia nueva, NO se fuerza: se espera a que el dueño diseñe
su tema (la APK congelada solo habla los idiomas registrados).

## PENDIENTE (pase final de motor, antes de congelar)

`pageSize` se mudará del código del tema AL MANIFEST, por vista:

```json
"pageSize": { "directorio": 96, "genero": 15, "busqueda": 20 }
```

Con esa mudanza, cualquier fuente futura declara sus tamaños
reales en datos y el motor aplica cortos y regla de fracción con
el número correcto. Hasta ese pase, los tamaños viven en el tema
de la APK (medidos y verificados en vivo).

## Lo que la APK garantiza a toda extensión conforme

Paginación por cadena única con ventana deslizante, rate limiter
por host, reintentos 3x, página-1 corta y regla de fracción (<=
75% pedido = fin sin confirmar), footer honesto Cargando/Fin,
caché de sesión por vista, esqueletos 1:1 y registro total en
logcat con reloj de sesión para autopsias.

## Checklist para una extensión nueva

- [ ] La fuente pertenece a una familia con tema registrado
- [ ] Dominio estable + domainRecoveryUrl de respaldo
- [ ] rateLimits reales medidos con cortesía (pedir permiso al dueño si hay duda)
- [ ] Icono webp/jpg/png en vivo (nada de .ico)
- [ ] contentRating correcto + filtro de adultos si aplica
- [ ] Sin diseño, sin código, sin binarios: solo el manifest
- [ ] version=1, status="alpha", subida al índice y probada en la APK congelada
