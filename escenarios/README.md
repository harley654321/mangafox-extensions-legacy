# Escenarios de prueba (extensiones de test)

Seis extensiones 100% datos (status "test", theme olympus) que reproducen
cada escenario de error de forma REAL y controlada en toda la APK.
Selecciona una en el selector de fuentes y Home/Directorio/Detalle la
viven al completo. El motor Olympus esta abierto por theme desde
FabricaFuentes (APK).

| Extension | Que reproduce | Como |
|---|---|---|
| escenario-sinred | SIN_CONEXION | Dominio .invalid (RFC 2606): DNS jamas resuelve |
| escenario-timeout | TIEMPO_AGOTADO | IP TEST-NET-1 192.0.2.1 (RFC 5737): connect congelado |
| escenario-servidor | SERVIDOR (404) | Endpoints ausentes en el CDN: 404 real |
| escenario-basura | INESPERADO | 200 con HTML en vez de JSON (cuerpo no-JSON) |
| escenario-vacio | Vacio honesto (SIN RESULTADOS) | 200 con listas vacias |
| escenario-sano | Control sano + errores en Detalle | Datos validos de 4 obras |

escenario-sano: obras A y B responden ficha 200 (los capitulos fallan
404 -> PARCIAL HONESTO: pie de caps incompletos con Reintentar);
obra-rota-c NO trae ficha (404 -> pantalla SERVIDOR en Detalle);
obra-basura-d trae ficha HTML (INESPERADO en Detalle).

Limitaciones declaradas (colision POSIX archivo/{slug} vs
directorio/{slug}/):
- Los capitulos (/api/series/{slug}/chapters) NO se sirven: se convierte
  en la prueba real del parcial honesto (log 586).
- La ruta de genero (/api/series?genres=...) queda 404: al tocar un chip
  en escenario-sano se ve el aborto honesto de la cadena (vacio).
- Las paginas del lector (api/capitulo/...) NO se sirven: al abrir un
  capitulo se prueba el estado de fallo del lector.
- escenario-timeout: cada peticion consume sus 3 intentos x 5 s
  (politica unica); la pantalla de error llega a los ~17 s.
