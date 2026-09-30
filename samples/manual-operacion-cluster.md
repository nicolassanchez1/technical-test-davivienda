# Manual de operación del clúster

Procedimientos de operación diaria para el clúster de documentación técnica.

## Respaldo y restauración

El respaldo de la base de datos se ejecuta cada noche. La restauración exige detener primero
el worker, porque una indexación a medias deja fragmentos huérfanos.

## Balanceo de carga

La API no guarda estado salvo las conexiones abiertas de eventos, así que escala horizontalmente
sin configuración adicional. El worker escala por separado: la extracción de un PDF consume CPU
y no debe competir con los hilos que atienden peticiones.

## Registros de auditoría

Los registros de auditoría se conservan durante noventa días. Cada petición lleva un
identificador propio que aparece también en los mensajes de error, de modo que un reporte de
usuario se rastrea hasta la línea exacta del registro.
