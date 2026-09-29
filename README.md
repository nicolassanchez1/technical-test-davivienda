# Buscador y Visor de Documentos Técnicos

Aplicación web para cargar documentos técnicos (TXT, PDF, Markdown) con metadatos,
procesarlos e indexarlos de forma asíncrona, buscarlos por texto completo con resaltado y
paginación, y visualizarlos en la aplicación con notificaciones de estado en tiempo real.

> Proyecto en construcción. Esta sección crece en cada fase; hoy el repositorio contiene el
> esqueleto del backend, del frontend y de la infraestructura.

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
