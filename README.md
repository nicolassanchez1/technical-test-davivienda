# Buscador y Visor de Documentos Técnicos

Aplicación web para cargar documentos técnicos (TXT, PDF, Markdown) con metadatos,
procesarlos e indexarlos de forma asíncrona, buscarlos por texto completo con resaltado y
paginación, y visualizarlos en la aplicación con notificaciones de estado en tiempo real.

> Proyecto en construcción. El backend está completo y la interfaz ya permite cargar documentos
> y seguir su estado en vivo. El buscador y el visor llegan en la fase siguiente.

## Requisitos previos

- Java 21 (Temurin o equivalente)
- Node.js 24 y pnpm 10 o superior
- Docker y Docker Compose

## Arranque

```bash
cp .env.example .env
docker compose up -d --build   # PostgreSQL, RabbitMQ y la API
pnpm install
```

La API queda en `http://localhost:8081/api`:

| Recurso                          | URL                                     |
| -------------------------------- | --------------------------------------- |
| Aplicación web                   | `http://localhost:8080`                 |
| Salud (base de datos y RabbitMQ) | `http://localhost:8081/api/health`      |
| Swagger UI                       | `http://localhost:8081/api/docs`        |
| Documento OpenAPI                | `http://localhost:8081/api/v3/api-docs` |
| Eventos de estado (SSE)          | `http://localhost:8081/api/events`      |
| Consola de RabbitMQ              | `http://localhost:15672`                |

| Comando                  | Qué hace                                                     |
| ------------------------ | ------------------------------------------------------------ |
| `pnpm lint`              | ESLint sobre TypeScript y Spotless sobre Java                |
| `pnpm format:check`      | Comprueba el formato con Prettier                            |
| `pnpm typecheck`         | Comprueba los tipos de TypeScript                            |
| `pnpm test`              | Tests unitarios de backend y frontend                        |
| `pnpm test:integration`  | Tests de integración del backend (Failsafe)                  |
| `pnpm build`             | Empaqueta el backend y construye el frontend                 |
| `pnpm contract:generate` | Regenera el contrato TypeScript desde el OpenAPI del backend |

El frontend de desarrollo arranca con
`pnpm --filter @technical-test-davivienda/frontend dev` y redirige `/api` al backend.

Cada respuesta lleva una cabecera `X-Request-Id`; los errores se devuelven como
`application/problem+json` (RFC 9457) e incluyen ese mismo identificador.

## Carga de documentos (HU-01)

La carga es multipart y responde de inmediato: el documento queda en `PROCESANDO` y la
indexacion continua en segundo plano.

```bash
curl -i -X POST http://localhost:8081/api/documents \
  -F 'files=@guia.md' \
  -F 'metadata=[{"title":"Guia","author":"Equipo","category":"MANUAL","tags":["infra"],"version":"1.0"}]'
```

Responde `202 Accepted` con un identificador de seguimiento por archivo y, cuando se sube
un solo archivo, una cabecera `Location`.

| Endpoint                          | Que hace                                                                   |
| --------------------------------- | -------------------------------------------------------------------------- |
| `POST /api/documents`             | Carga individual o masiva con metadatos                                    |
| `GET /api/documents`              | Lista paginada, filtrable por `status` (`PROCESANDO`, `INDEXADO`, `ERROR`) |
| `GET /api/documents/{id}`         | Detalle con todos los metadatos                                            |
| `GET /api/documents/{id}/content` | Cuerpo del documento por fragmentos, paginado por cursor                   |
| `GET /api/documents/{id}/file`    | Archivo original, servido inline                                           |

Tras la carga, el worker extrae el texto, lo divide en fragmentos y los indexa. El documento
pasa a `INDEXADO`, o a `ERROR` con un código que explica por qué: un PDF sin capa de texto
termina en `PDF_NO_TEXT_LAYER`, por ejemplo. La API y el worker son el mismo jar con distinto
perfil: el worker no levanta servidor HTTP, porque analizar un PDF consume CPU y no debe
competir con los hilos que atienden peticiones.

Formatos aceptados: `.txt`, `.md`, `.markdown`, `.pdf`. Se valida la extension, el tamano
y los bytes de cabecera del archivo. Si algun archivo del lote no pasa, **no se guarda
ninguno** y la respuesta es `422` con el detalle por archivo.

## Búsqueda (HU-02)

```bash
curl -s 'http://localhost:8081/api/search?q=especificacion+tecnica'
curl -s 'http://localhost:8081/api/search?q="balanceo+de+carga"'   # frase exacta
curl -s 'http://localhost:8081/api/search?q=postgresql+-respaldo'  # excluir un término
curl -s 'http://localhost:8081/api/search?q=indice&category=MANUAL&tags=infra&author=Equipo'
```

| Parámetro                    | Para qué                                                                |
| ---------------------------- | ----------------------------------------------------------------------- |
| `q`                          | Términos o frases entre comillas; `-término` excluye                    |
| `page`, `pageSize`           | Paginación; `pageSize` por defecto 10 y tope `APP_SEARCH_MAX_PAGE_SIZE` |
| `category`, `author`, `tags` | Filtros por metadatos                                                   |

La búsqueda no distingue tildes: `especificacion tecnica` encuentra `Especificación técnica`.
Cada resultado trae el fragmento que coincidió con los términos marcados entre `⟦` y `⟧`, más
el título resaltado igual; el frontend los convierte en `<mark>` sin interpretar HTML. La
respuesta incluye `tookMs` y una cabecera `Server-Timing`.

Solo aparecen documentos `INDEXADO`. Una consulta sin términos buscables responde `400` con
un mensaje accionable, y una búsqueda que excede su presupuesto de tiempo responde `503`.

## Notificaciones en tiempo real (HU-04)

El backend avisa cuando un documento termina de procesarse. No hay _polling_: el cliente abre
una conexión y espera.

```bash
curl -N http://localhost:8081/api/events
```

```
:stream-open

id:ef7cfc57-…-1790779571356
event:document.status
data:{"documentId":"ef7cfc57-…","status":"INDEXADO","occurredAt":"…"}
```

Un documento que falla llega con su motivo, por ejemplo
`{"status":"ERROR","errorCode":"PDF_NO_TEXT_LAYER"}`. Cada `APP_SSE_HEARTBEAT_MS` viaja un
comentario de latido para que ningún intermediario corte una conexión ociosa.

## Interfaz

La aplicación se sirve en `http://localhost:8080`, con nginx delante: entrega la SPA y reenvía
`/api` a la API. Para `/api/events` desactiva el buffering, porque un proxy que retiene el stream
deja las notificaciones en tiempo real sin efecto y sin ningún error que lo delate.

| Ruta         | Qué hace                                                                        |
| ------------ | ------------------------------------------------------------------------------- |
| `/upload`    | Carga individual o masiva, con los metadatos por archivo                        |
| `/documents` | Lista paginada, filtrable por estado, que cambia sola al indexarse un documento |

La interfaz abre **una sola** conexión de eventos al arrancar, no una por documento, y no consulta
en intervalos: el estado cambia cuando el backend lo anuncia.

## Estructura

```
backend/          API y worker (Java 21, Spring Boot, Maven)
frontend/         SPA (React, Vite, TypeScript)
packages/shared/  Contrato TypeScript generado desde el OpenAPI del backend
docs/             Documentación obligatoria
```

## Documentación

- [`docs/architecture.md`](./docs/architecture.md) — arquitectura y decisiones técnicas.
- [`docs/ia.md`](./docs/ia.md) — uso de inteligencia artificial durante el desarrollo.
