# Uso de Inteligencia Artificial

> Documento obligatorio segun el apartado 6.2 del enunciado. Se anade una entrada por cada
> fase del desarrollo.

## Herramientas Utilizadas

| Herramienta             | Uso                                                                                                                    |
| ----------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| Claude Code (Anthropic) | Asistente principal en terminal: andamiaje del monorepo, configuracion de tooling, redaccion de tests y documentacion. |

## Casos de Uso de IA

### Fase 0 — Bootstrap del workspace

- Andamiaje del monorepo: workspace de pnpm, TypeScript en modo estricto, ESLint plano,
  Prettier, EditorConfig.
- Hooks de git con husky: `commit-msg` (commitlint + verificacion de autoria) y
  `pre-commit` (lint-staged).
- Esqueleto de Spring Boot con el endpoint de salud y sus tests.
- Configuracion de Spotless con palantir-java-format.
- Pipeline del contrato: `OpenApiContractIT` exporta el documento OpenAPI y
  `openapi-typescript` genera los tipos de `packages/shared`.
- Esqueleto de Vite + React con su test.
- `docker-compose.yml` con PostgreSQL y RabbitMQ, ambos con healthcheck.
- Workflow de CI con los trabajos `quality`, `commits`, `unit`, `contract` y `build`.

### Fase 1 — Cimientos del backend

- `@ConfigurationProperties` validados que detienen el arranque ante un valor incorrecto.
- Origen de datos PostgreSQL con `JdbcClient` y migraciones Flyway.
- Esquema de `documents` y `document_chunks`, con la configuracion de texto `es_unaccent`.
- Filtro de request id con MDC y logging estructurado ECS.
- Manejador global de errores que devuelve `ProblemDetail` (RFC 9457).
- Salud de la aplicacion con base de datos y RabbitMQ.
- Reglas ArchUnit que sostienen las fronteras hexagonales.
- Dockerfile multietapa, JaCoCo y el trabajo de integracion en CI.

Verificaciones empiricas de esta fase, de nuevo antes de construir encima:

- **Testcontainers 2.0.5** (la version que gestiona Spring Boot 4) renombro sus modulos:
  `postgresql` paso a `testcontainers-postgresql` y `junit-jupiter` a
  `testcontainers-junit-jupiter`; ademas hay que importar su BOM y la clase
  `PostgreSQLContainer` dejo de ser generica. Con esos ajustes, `@ServiceConnection`
  funciona sobre Boot 4.
- **Flyway** no arranca solo con `flyway-core` en Boot 4: la autoconfiguracion vive en
  `spring-boot-starter-flyway`. Se detecto porque un test de integracion comprobaba que la
  tabla de historial existiera, no por inspeccion.
- Las reglas de ArchUnit se probaron introduciendo a proposito una clase que las violaba,
  para confirmar que fallan cuando deben en lugar de pasar en vacio.

El stack completo se levanto con `docker compose` y se comprobo de extremo a extremo la
salud con base de datos y RabbitMQ, el eco de `X-Request-Id`, el reemplazo de un valor de
cabecera inseguro y la respuesta `application/problem+json` en una ruta inexistente.

#### Cambio de stack del backend

El `CLAUDE.md` inicial fijaba NestJS como backend. **Cambie esa decision a Java 21 con
Spring Boot**; el `CLAUDE.md` se reescribio para reflejarlo y el backend se migro.

En el momento del cambio no habia codigo de NestJS en ningun commit: el andamiaje existia
solo en el directorio de trabajo, sin subir. Por eso la migracion no necesito reescribir
historia publicada: se descarto ese trabajo sin publicar y la rama se reconstruyo sobre
`main`.

Durante la fase se verificaron de forma empirica dos supuestos del stack, en lugar de
darlos por buenos:

- **NestJS 12 es ESM puro**, lo que obligaba a replantear el runner de tests del backend.
  Fue uno de los motivos para reevaluar el stack.
- **springdoc-openapi 2.8.6 esta construido contra Spring Boot 3**, pero se comprobo que
  sirve un documento OpenAPI 3.1 valido sobre **Spring Boot 4.1.1**. Esa comprobacion es lo
  que permitio quedarse en la ultima version estable de Spring Boot en vez de bajar a una
  linea 3.5 ya fuera de soporte. El trabajo `contract` de CI es lo que mantiene honesta esa
  garantia.
- **PostgreSQL 17 con `unaccent` y la configuracion `es_unaccent`**: se valido contra la
  imagen real que "especificacion tecnica" (sin tildes) encuentra "Especificación técnica".

### Herramientas propias del repositorio

Para que el trabajo con IA fuera repetible y no dependiera de lo que un asistente recuerde
en cada sesion, las reglas del proyecto se empaquetaron como agentes y skills versionados
en `.claude/`:

| Tipo   | Nombre              | Para que sirve                                                                                          |
| ------ | ------------------- | ------------------------------------------------------------------------------------------------------- |
| Agente | `backend-engineer`  | Funcionalidad de Spring Boot: casos de uso, puertos, adaptadores JDBC, controladores y sus tests.       |
| Agente | `search-engineer`   | Camino de busqueda en PostgreSQL: `tsvector`, GIN, ranking, resaltado, `statement_timeout` y `EXPLAIN`. |
| Agente | `frontend-engineer` | SPA de React: paginas, hooks, cliente de API, conexion SSE unica y visor.                               |
| Agente | `spec-auditor`      | Solo lectura: audita una rama contra el enunciado y reporta huecos.                                     |
| Skill  | `verify`            | Ejecuta en local todas las puertas que ejecuta CI y devuelve un unico veredicto.                        |
| Skill  | `phase`             | Lleva una fase del roadmap de principio a fin con el flujo de git y PR.                                 |
| Skill  | `contract-sync`     | Regenera el contrato TypeScript desde el OpenAPI y reporta desviaciones.                                |

Cada agente lleva escritas las restricciones que no se pueden negociar (sin `LIKE`, sin
JPA, sin Lombok, sin inyeccion por campo, sin polling, sin `dangerouslySetInnerHTML`, sin
texto en espanol dentro de los componentes), de modo que la restriccion viaja con la tarea
en lugar de repetirse en cada prompt.

Los agentes implementan y reportan; la sesion principal revisa, prepara y commitea. Ningun
agente hace commits.

## Prompts Clave

| Objetivo            | Prompt (resumido)                                                                                                                                       | Refinamiento aplicado                                                                                                   |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| Definir el proyecto | Documento inicial con requisitos duros, stack aprobado, flujo de git y roadmap por fases, usado como memoria persistente del repositorio (`CLAUDE.md`). | Se convirtio en la fuente de verdad del proyecto; cada decision que cambia se actualiza en el mismo PR.                 |
| Leer el enunciado   | "El PDF del enunciado es solo imagen: leelo visualmente y consultalo ante cualquier duda; si contradice al `CLAUDE.md`, gana el PDF."                   | Evita que el asistente invente requisitos: el enunciado queda por encima de la memoria del proyecto.                    |
| Cambio de stack     | "El backend debe ser Java 21 + Spring Boot, no NestJS. Reescribe `CLAUDE.md` y elimina toda referencia obsoleta."                                       | Se pidio ademas hacer `grep` de los terminos obsoletos sobre `CLAUDE.md`, README, docs y CI para no dejar restos.       |
| Autoria             | "Nunca anadas `Co-Authored-By`, firmas de herramienta ni enlaces de sesion a commits, PR, codigo o documentacion."                                      | Se reforzo con un hook `commit-msg` y un trabajo de CI que rechazan esos patrones, en vez de confiar en la instruccion. |
| Latencia            | "El enunciado dice 400 ms - 1000 ms: interpretalo como techo duro, nunca anadas retardo artificial."                                                    | La interpretacion quedo documentada en `architecture.md` para poder defenderla en la sustentacion.                      |

## Validación Humana

**TODO**
