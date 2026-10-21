// Fabrica MFX: compila las extensiones .mfx contra la
// superficie API congelada de la apk MangaFox.
rootProject.name = "mangafox-extensions"

include(":api")
include(":mfx-mhscans")
project(":mfx-mhscans").projectDir = file("mfx/mhscans")
