# Buscador y Visor de Documentos Técnicos

Aplicación web para cargar documentos técnicos (TXT, PDF, Markdown) con metadatos,
procesarlos e indexarlos de forma asíncrona, buscarlos por texto completo con resaltado y
paginación, y visualizarlos en la aplicación con notificaciones de estado en tiempo real.

> Proyecto en construcción. Hoy funcionan la carga de documentos con metadatos y su consulta;
> la indexación, la búsqueda y las notificaciones en tiempo real llegan en las fases siguientes.

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
| Salud (base de datos y RabbitMQ) | `http://localhost:8081/api/health`      |
| Swagger UI                       | `http://localhost:8081/api/docs`        |
| Documento OpenAPI                | `http://localhost:8081/api/v3/api-docs` |
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

Formatos aceptados: `.txt`, `.md`, `.markdown`, `.pdf`. Se valida la extension, el tamano
y los bytes de cabecera del archivo. Si algun archivo del lote no pasa, **no se guarda
ninguno** y la respuesta es `422` con el detalle por archivo.

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
