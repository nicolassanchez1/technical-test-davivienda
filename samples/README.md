# Documentos de ejemplo

Corpus mínimo para probar la aplicación. Cada archivo existe por un motivo concreto.

| Archivo                              | Para qué sirve                                                                      |
| ------------------------------------ | ----------------------------------------------------------------------------------- |
| `guia-arquitectura-buscador.md`      | Markdown con secciones: demuestra la división por encabezados y el índice del visor |
| `manual-operacion-cluster.md`        | Markdown con tabla, otra categoría y otro autor: sirve para probar los filtros      |
| `especificacion-carga-documentos.md` | Markdown, tercera categoría                                                         |
| `notas-despliegue.txt`               | Texto plano en UTF-8                                                                |
| `politica-retencion-latin1.txt`      | Texto plano en **Latin-1**: una lectura ingenua como UTF-8 lo corrompe              |
| `informe-rendimiento.pdf`            | PDF de dos páginas **con** capa de texto: se divide por página                      |
| `plano-escaneado-sin-texto.pdf`      | PDF **sin** capa de texto: termina en `ERROR` con `PDF_NO_TEXT_LAYER`               |

Todos están en español y con tildes, porque la búsqueda es insensible a acentos y eso solo se
demuestra con contenido acentuado.

## Cargarlos

```bash
docker compose up -d      # el stack tiene que estar arriba
pnpm seed
```

Seis quedan en `INDEXADO` y el plano escaneado en `ERROR`, que es el comportamiento correcto:
un PDF sin texto extraíble no se puede indexar y la aplicación lo dice en lugar de fallar en
silencio.

## Búsquedas que demuestran algo

| Consulta                  | Qué demuestra                                    |
| ------------------------- | ------------------------------------------------ |
| `especificacion`          | Encuentra `Especificación` sin escribir la tilde |
| `analisis lexico`         | Igual, sobre dos documentos distintos            |
| `particionado`            | Texto extraído de la segunda página de un PDF    |
| `retencion`               | El archivo Latin-1 se indexó sin corromperse     |
| `"indice invertido"`      | Frase exacta                                     |
| `postgresql -manual`      | Exclusión de un término                          |
| `area de infraestructura` | Los metadatos también son texto buscable         |
