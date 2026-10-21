# Repo de extensiones MangaFox (sistema .mfx)

Extensiones de **fuentes de manga** para la app [MangaFox](https://github.com/harley654321/MangaFox).

La APK de MangaFox es un **kernel estable**: no contiene fuentes. Cada fuente es
un **plugin `.mfx`** compilado (ZIP con bytecode Kotlin) y **firmado con ECDSA**;
la APK lo verifica (firma + SHA-256) antes de activarlo. Las extensiones de datos
del sistema antiguo fueron retiradas por completo: este repo vive en el sistema
`.mfx` con una única extensión, **mhscans**.

## Enlace único de importación

```
https://raw.githubusercontent.com/harley654321/mangafox-extensions/main/index.json
```

## Estructura

```
index.json                        ← catálogo: una entrada por extensión .mfx
mfx/<id>/                         ← código fuente del plugin (Kotlin, compila a .mfx)
  ├─ manifest.json                ← id, version, apiVersion, pluginClassName
  ├─ build.gradle.kts             ← módulo del plugin
  └─ src/main/kotlin/.../         ← Fuente*.kt + Plugin*.kt (punto de entrada)
api/                              ← superficie API pública congelada (vendored de la APK)
  └─ com/harley/mangafox/...      ← modelos, parser, ClienteWeb, MangaFoxLog, FuenteMfx
CONTRATO-EXTENSIONES.md           ← contrato obligatorio de cada extensión
scripts/actualizar_index_mfx.py   ← registra hash+firma del .mfx recién construido
builds (rama)                     ← artefactos .mfx publicados (rama builds)
```

## Reglas duras

- **Kotlin 1.9.24 SIEMPRE**: el mismo que la APK. Un Kotlin mayor provoca
  `NoClassDefFoundError` en runtime (referencias a clases internas nuevas que
  el stdlib de la APK no tiene). No se sube esta versión por capricho.
- **apiVersion congelada**: la `api/` de este repo es la superficie pública
  que la APK expone; cambia solo con el CONTRATO, nunca unilateralmente.
- **Firma ECDSA obligatoria**: un .mfx sin firma válida es rechazado por el
  Instalador de la APK.
- Cada cambio de una extensión sube `version` (+1) y registra en `index.json`
  el nuevo `fileHashMfx` + `firmaMfx` con `scripts/actualizar_index_mfx.py`.

## Flujo de trabajo

El dueño compila los `.mfx` con `./gradlew` (wrapper 8.4) en su entorno y
entrega logs; el agente analiza y corrige con evidencia. Sin CI.
