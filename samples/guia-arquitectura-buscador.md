# Guía de arquitectura del buscador

Este documento describe cómo se resuelve la búsqueda de texto completo sin recurrir a
consultas no indexadas.

## Índice invertido

El índice invertido se construye durante el análisis léxico. Cada fragmento de un documento
guarda su propio vector de búsqueda, de modo que una consulta nunca recorre la tabla completa.

La configuración de texto elimina las tildes antes de aplicar el lematizador en español, así
que `especificacion` encuentra `Especificación` sin que el usuario tenga que escribir el acento.

## Ponderación de resultados

Los términos no pesan lo mismo según dónde aparecen:

| Peso | Origen                       |
| ---- | ---------------------------- |
| A    | Título del documento         |
| B    | Etiquetas, categoría y autor |
| C    | Contenido del fragmento      |

Un término que solo aparece en el título sigue posicionando el documento, y una etiqueta
encuentra documentos cuyo cuerpo jamás menciona esa palabra.

## Resaltado de coincidencias

El resaltado se calcula únicamente sobre la página devuelta. Calcularlo sobre todas las
coincidencias es la forma más común de exceder el presupuesto de latencia, porque cada
fragmento se vuelve a analizar carácter por carácter.
