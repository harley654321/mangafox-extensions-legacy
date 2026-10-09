# Repo de extensiones MangaFox

Extensiones de **fuentes de manga** para la app [MangaFox](https://github.com/harley654321/MangaFox).

Aquí solo viven **datos**: endpoints, dominios, mapeos y reglas de cada fuente.
Todo el diseño y la experiencia de usuario corre dentro de la app MangaFox.
Una extensión no es una APK ni se instala nada: la app importa este índice por
un único enlace y las fuentes aparecen disponibles dentro de MangaFox.

## Enlace único de importación

La app importa este índice con un solo enlace:

```
https://raw.githubusercontent.com/harley654321/mangafox-extensions/main/index.json
```

## Estructura

```
index.json                                 ← índice: lista todas las extensiones
extensions/<id>/manifest.json              ← manifiesto de cada extensión
```

- `index.json`: catálogo general. Cada entrada apunta al manifiesto de su extensión.
- `manifest.json`: define una fuente: identidad, red (baseUrl, recuperación de
  dominio, rate limits), endpoints y mapeos (estados, tipos).

## Campos del manifiesto (apiVersion 1)

| Campo | Descripción |
|---|---|
| `id` | Identificador único de la fuente (alfanumérico) |
| `name` / `lang` / `version` | Nombre visible, idioma (ISO 639-1), versión de la extensión |
| `description` | Qué ofrece la fuente |
| `contentRating` | `safe` / `mature` |
| `network.baseUrl` | Dominio principal actual |
| `network.domainRecoveryUrl` | Página de respaldo para redescubrir el dominio cuando cambia |
| `network.rateLimits` | Límites de peticiones por host (`main`, `panel`) |
| `content.seriesType` | Filtro de tipo de serie de la API (ej. `comic`) |
| `endpoints.*` | Plantillas de URL con marcadores `{page}`, `{slug}`, `{chapterId}` |
| `statusMap` | Mapeo de ids de estado de la API a estados de MangaFox |

## Cómo agregar una extensión

1. Crea la carpeta `extensions/<id>/` con su `manifest.json`.
2. Registra la entrada correspondiente en `index.json`.
3. La app detecta las novedades al refrescar el índice; no hay que instalar nada.

## Nota

Los dominios de las fuentes pueden cambiar. Cuando eso pasa, se actualiza el
campo `network.baseUrl` en el manifiesto y la app se recupera sola; el resto
de la extensión no cambia.
