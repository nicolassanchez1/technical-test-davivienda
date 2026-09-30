# Especificación de carga de documentos

## Formatos admitidos

Se aceptan `.txt`, `.md`, `.markdown` y `.pdf`. La validación comprueba la extensión, el tamaño
y los bytes de cabecera del archivo, en ese orden.

## Validación por lote

Una carga es todo o nada. Si cualquier archivo del lote incumple una regla, no se almacena
ninguno y la respuesta enumera cada archivo rechazado con su motivo. El usuario corrige el lote
completo en un solo intento en lugar de descubrir el siguiente problema en el siguiente envío.

## Duplicados

La huella del contenido es única. Un archivo ya almacenado se rechaza señalando el documento que
ya lo contiene, para que el usuario lo consulte en vez de guardarlo dos veces.

## Procesamiento asíncrono

La respuesta es inmediata: el documento queda en estado de procesamiento y la indexación
continúa en segundo plano. La interfaz se entera del resultado por una conexión de eventos, sin
preguntar de forma repetida.
