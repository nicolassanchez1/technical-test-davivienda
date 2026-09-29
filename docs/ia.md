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
