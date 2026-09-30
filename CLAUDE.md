# Technical Docs Search & Viewer

## Context
1-day technical test for a Full Stack Senior role, followed by a 30-min defense: 15 min architecture + live demo, 15 min Q&A on design decisions, concurrency and AI usage. Time-boxed: prefer the simplest design that satisfies the spec and can be defended live. The repo is public.

Spec (Spanish, image-only, read visually): `spec/KATA_29_sept_Buscador_y_Visor_Documentos.pdf`. Consult it whenever in doubt; it wins over this file. Gitignored: never commit it.

Goal: upload technical documents (TXT, PDF, Markdown) with metadata → process and index them asynchronously → full-text search (fast, paginated, highlighted) → view them in-app → real-time status notifications.

## Hard requirements (non-negotiable)
- Upload, single and bulk, with metadata: title, author, category, tags, version. REST multipart. Validate format and max size. Respond immediately: `202` + tracking id + initial status `PROCESANDO`.
- Search by keywords and phrases over title, metadata and content, with pagination and highlighted fragments.
- NO `LIKE`. Also forbidden: `ILIKE`, `SIMILAR TO`, regex operators, in-memory filtering. Search = PostgreSQL full-text search (tsvector + GIN). Metadata filters use equality/array operators on indexed columns.
- Latency: the spec says "400 ms – 1000 ms". Interpretation (documented in architecture.md): 1 s is a hard ceiling for p95/p99, not a floor. Never add artificial delay. Enforce it (statement timeout, bounded page size, highlight only the returned page) and prove it (benchmark).
- Viewer: dedicated view with full metadata and structured body, no download needed, smooth rendering of long documents.
- Real-time: the backend pushes status changes to `INDEXADO` / `ERROR`. NO polling of any kind (no setInterval, no TanStack Query `refetchInterval`). Transport: SSE.
- Quality: justified architecture, global exception handling, clean `.env` handling, unit + integration tests for critical backend components.
- Deliverable layout: `backend/`, `frontend/`, `packages/shared/`, `docs/architecture.md` (mandatory), `docs/ia.md` (mandatory), `docker-compose.yml`, `README.md`.
- Evaluators check: README works from a clean clone · both docs exist and are detailed · upload endpoint · search without LIKE · search latency · structured errors and validation · real-time status · reactive UI to upload/search/view · clear UX.

## Authorship (strict)
- I am the only author. Commits use my git identity; never change git config.
- Never add `Co-Authored-By`, "Generated with Claude Code", `Claude-Session` trailers or session links to commits, PR titles/bodies, code or docs. Any author field (pom.xml, package.json, docs) uses my git identity.
- AI usage is documented only in `docs/ia.md`, because the spec requires it.
- Enforced by the `commit-msg` hook and a CI check that reject those trailers.

## Code style
- 100% English and readable: descriptive names, no abbreviations, small focused functions, early returns, no dead code.
- Comments: only when the "why" isn't obvious, one short line. No commented-out code, no Javadoc or JSDoc that restates the signature, no banner comments.
- API messages, logs and errors in English. The worker stores an `errorCode` (`PDF_NO_TEXT_LAYER`, `UNSUPPORTED_FORMAT`, `CORRUPT_FILE`, `EMPTY_CONTENT`, `PROCESSING_FAILED`) that the UI maps to Spanish copy.
- Identifiers are always English. Spanish appears only as *values*: the status wire format, which is API contract, and UI copy centralized in `frontend/src/copy/es.ts` (components reference keys, never inline text).
- Spanish docs for the evaluators: `README.md`, `docs/architecture.md`, `docs/ia.md`, using the spec's exact section headings.
- Commits, branches, PRs and this file: English.

## Stack (approved; justify any new dependency in the PR body)
- Backend: Java 21 LTS, Spring Boot 4.1.x, Maven with wrapper (`./mvnw`). Base package `io.github.nicolassanchez1.technicaltestdavivienda`.
  Boot 4 renamed the starters: `spring-boot-starter-webmvc` replaces `spring-boot-starter-web`, and the single `spring-boot-starter-test` is split into per-module companions (`spring-boot-starter-webmvc-test`, `spring-boot-starter-actuator-test`, …).
  springdoc-openapi 2.8.6 targets Boot 3 but was verified to serve a valid OpenAPI 3.1 document on Boot 4; the `contract` CI job is what keeps that guarantee honest.
- PostgreSQL 17 (`postgres:17-alpine`) + `unaccent`.
- RabbitMQ 4 (`rabbitmq:4-management-alpine`) + Spring AMQP for jobs and for status event fan-out.
- Frontend: React + Vite + TypeScript + Tailwind + TanStack Query + React Router; react-dropzone, react-markdown + remark-gfm + rehype-slug (never rehype-raw), react-virtuoso, sonner. Vite dev proxy `/api` → API.
- `packages/shared`: TypeScript API contract generated from the backend OpenAPI document. No hand-written DTOs.
- pnpm workspaces for the TypeScript side (`packages/*`, `frontend`), Node 24 LTS, TypeScript strict. Workspace scope = repo name. The backend is a Maven module, not a pnpm workspace.
- Tooling: ESLint (flat) + Prettier + EditorConfig for TypeScript; Spotless + palantir-java-format for Java; husky with `commit-msg` (commitlint + attribution check) and `pre-commit` (lint-staged).
- Compose services: `postgres`, `rabbitmq`, `api`, `worker`, `web` (nginx serving the SPA and proxying `/api`). Healthchecks + `depends_on` conditions. Shared uploads volume between `api` and `worker`.

## Backend stack detail
- Spring MVC with virtual threads (`spring.threads.virtual.enabled=true`). One jar, two processes selected by profile: `api` (HTTP + SSE) and `worker` (`spring.main.web-application-type=none`, queue consumers only).
- `JdbcClient` with explicit SQL. No JPA/Hibernate: the search SQL must stay explicit and EXPLAIN-able. Flyway migrations in `src/main/resources/db/migration`, run by `api` only.
- RabbitMQ + Spring AMQP:
  - Durable queue `documents.process` (prefetch 1, concurrency up to `APP_WORKER_CONCURRENCY`) with dead-letter queue `documents.process.dlq`.
  - Retry only transient errors: 3 attempts, exponential backoff. Deterministic errors (corrupt, unsupported, empty, no text layer) throw a non-retryable exception; the recoverer marks the document `ERROR` with its error code and dead-letters the message.
  - Fanout exchange `documents.status`: the worker publishes status events; each API instance binds an exclusive auto-delete queue and forwards to its own SSE clients.
  - Publish only after commit (`@TransactionalEventListener(phase = AFTER_COMMIT)`): the job from the API, the status event from the worker. Otherwise the worker can receive a job whose row isn't committed yet.
  - On `api` startup, a reconciler re-publishes documents stuck in `PROCESANDO` longer than `APP_STUCK_PROCESSING_MINUTES`.
- Extraction: Apache PDFBox 3 (per-page text), juniversalchardet (TXT encoding), commonmark-java (MD sections and plain text).
- SSE: `SseEmitter` registry, `@Scheduled` heartbeat comment every `APP_SSE_HEARTBEAT_MS`, emitters removed on error or timeout, header `X-Accel-Buffering: no`.
- Errors: one `@RestControllerAdvice` returning `ProblemDetail` (RFC 9457) with the request id; `MaxUploadSizeExceededException` → 413; SQLSTATE 57014 → 503.
- Config: `@ConfigurationProperties` records under `app.*`, `@Validated`, fail fast at boot.
- Observability: structured JSON logging with the request id in MDC; Actuator health (db + rabbit) mapped to `/api/health`; springdoc-openapi with Swagger UI at `/api/docs`.
- Status contract: `enum DocumentStatus { PROCESSING("PROCESANDO"), INDEXED("INDEXADO"), FAILED("ERROR") }` serialized with `@JsonValue`; the database stores the contract value.
- Java style: records for DTOs and value objects, constructor injection only, no Lombok, `Optional` only as a return type, small final classes.

## Backend layout
~~~
backend/
  pom.xml, mvnw, Dockerfile   # multi-stage: Temurin 21 JDK build -> JRE runtime
  src/main/java/io/github/nicolassanchez1/technicaltestdavivienda/
    Application.java
    shared/            # config properties, problem details, request id filter, pagination
    documents/
      domain/          # Document, DocumentStatus, ErrorCode, domain exceptions
      application/     # use cases + ports: DocumentRepository, FileStorage, JobPublisher, StatusEventPublisher, TextExtractor
      infrastructure/  # JDBC repository, local storage, RabbitMQ adapters, extractors, chunkers
      web/             # controllers, request/response records
    search/            # SearchDocuments use case, SearchEngine port, PostgresSearchEngine, web/
    events/            # SSE emitter registry, status listener, heartbeat
  src/main/resources/  # application.yml, application-api.yml, application-worker.yml, db/migration/
~~~
Ports keep the search engine, storage, queue and event bus swappable (e.g. OpenSearchAdapter, S3Storage) without touching use cases. Use cases are unit-tested with in-memory fakes. ArchUnit enforces the boundaries.

## Shared contract
- `packages/shared` = TypeScript API contract generated with `openapi-typescript` from the backend OpenAPI document. Source of truth: the Java DTOs.
- `OpenApiContractIT` writes `/v3/api-docs` to `packages/shared/openapi.json`; `pnpm contract:generate` regenerates `packages/shared/src/api.d.ts`.
- The frontend imports its API types from there and keeps local zod schemas for form validation that mirror the Bean Validation constraints.

## Data model
- `documents`: id uuid, title, author, category (`MANUAL | SPECIFICATION | ARCHITECTURE_GUIDE | OTHER`), tags text[], version, original_filename, mime_type, size_bytes, storage_key, sha256 (unique), status, error_code, error_message, page_count, chunk_count, processing_ms, created_at, updated_at, indexed_at. Indexes: status, category, GIN(tags).
- `document_chunks`: id bigserial, document_id (FK, on delete cascade), chunk_index, page, heading, content, search_vector tsvector. GIN(search_vector); unique(document_id, chunk_index).
- Text search config (Flyway migration):
~~~sql
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE TEXT SEARCH CONFIGURATION es_unaccent (COPY = spanish);
ALTER TEXT SEARCH CONFIGURATION es_unaccent
  ALTER MAPPING FOR hword, hword_part, word WITH unaccent, spanish_stem;
~~~
- `search_vector` = title (weight A) || tags + category + author (B) || chunk content (C), all with `es_unaccent`.

## Processing pipeline
1. API: validate → store the file as `<uuid>.<ext>` (never the user filename) → INSERT with `PROCESANDO` → publish the job after commit → `202 { items: [{ id, filename, status }] }`, plus a `Location` header for single uploads.
2. Worker, separate process (parsing is CPU-bound and must never compete with API request threads): extract → chunk → ONE transaction: delete existing chunks, insert every chunk with its tsvector in one set-based statement, `UPDATE documents SET status = 'INDEXADO' ... WHERE id = ? AND status = 'PROCESANDO'` → COMMIT → publish the status event. Never publish before commit.
3. Extraction: TXT → strict UTF-8 decode first (it validates itself), juniversalchardet only when that fails, UTF-8 as the last resort. MD → index stripped text, chunk by headings (commonmark-java); the viewer renders the raw markdown it fetches from `GET /api/documents/:id/file`, so extraction does not carry a second copy. PDF → per-page text (PDFBox 3); no extractable text → `ERROR` with `PDF_NO_TEXT_LAYER`.
4. Chunking: PDF per page, MD per h1–h3 section, TXT ~1,500 words at paragraph boundaries, always with a max chunk size. Why: ts_headline re-parses the whole text it receives, a tsvector is capped at 1 MB, and positions saturate at 16,383.
5. Failures: deterministic (corrupt, unsupported, empty) → non-retryable exception → `ERROR` + error code + dead-letter. Transient → 3 attempts with exponential backoff, then `ERROR`.
6. On `api` boot, a reconciler re-publishes documents stuck in `PROCESANDO` longer than `APP_STUCK_PROCESSING_MINUTES`.
7. Duplicates: sha256 unique; a duplicate file gets a per-file error referencing the existing document id.

## Search
- `GET /api/search?q=&page=&pageSize=&category=&tags=&author=`; pageSize default 10, max `APP_SEARCH_MAX_PAGE_SIZE`; cap deep offsets.
- `websearch_to_tsquery('es_unaccent', q)` gives quoted phrases, `or` and `-term`. Empty or stopword-only query → 400 with a clear message.
- Best chunk per document → paginate documents → ts_headline ONLY on the returned page. Apply metadata filters inside `best` via a join on documents:
~~~sql
WITH q AS (SELECT websearch_to_tsquery('es_unaccent', :query) AS query),
best AS (
  SELECT DISTINCT ON (c.document_id)
         c.document_id, c.id AS chunk_id, ts_rank_cd(c.search_vector, q.query) AS rank
  FROM document_chunks c, q
  WHERE c.search_vector @@ q.query
  ORDER BY c.document_id, rank DESC, c.chunk_index
),
page AS (
  SELECT *, count(*) OVER () AS total
  FROM best ORDER BY rank DESC LIMIT :limit OFFSET :offset
)
SELECT d.*, p.rank, p.total, ch.chunk_index, ch.page,
       ts_headline('es_unaccent', ch.content, q.query,
         'StartSel=⟦, StopSel=⟧, MaxFragments=2, MaxWords=25, MinWords=10') AS snippet
FROM page p
JOIN documents d        ON d.id  = p.document_id
JOIN document_chunks ch ON ch.id = p.chunk_id
CROSS JOIN q
ORDER BY p.rank DESC;
~~~
- Title highlight: ts_headline on `d.title` with `HighlightAll=true` and the same sentinels.
- Sentinels ⟦ ⟧ become `<mark>` React nodes on the frontend. Never `dangerouslySetInnerHTML` for snippets or document content.
- Run inside a transaction with `SELECT set_config('statement_timeout', ?, true)` (SET doesn't accept bind parameters), value = `APP_SEARCH_TIMEOUT_MS`. SQLSTATE 57014 → 503 problem+json.
- Response includes `tookMs`; also send a `Server-Timing` header.

## Real-time (SSE)
- `GET /api/events` returns an `SseEmitter`. Event type `document.status`, data `{ documentId, status, errorCode?, occurredAt }`, every event with an `id`. Heartbeat comment every `APP_SSE_HEARTBEAT_MS`. Headers: `Cache-Control: no-cache`, `X-Accel-Buffering: no`.
- The worker publishes to the `documents.status` fanout exchange; every API instance binds an exclusive auto-delete queue and forwards to its own clients.
- Frontend: ONE global EventSource opened at app start (never one per document). On event → `queryClient.setQueryData` + toast. On (re)connect → one reconciliation fetch for tracked documents still in `PROCESANDO` (that is not polling).
- nginx for `/api/events`: `proxy_buffering off; proxy_cache off; proxy_http_version 1.1; proxy_set_header Connection ""; proxy_read_timeout 1h;`.

## API
- `POST /api/documents`: multipart `files` (1..`APP_MAX_FILES_PER_UPLOAD`) + `metadata` (JSON array aligned by index). Validate everything first; any invalid file → 422 with per-file errors and nothing stored. Stream uploads to disk, not memory.
- Validation: extension allowlist (`.txt .md .markdown .pdf`), magic bytes (`%PDF-` for PDF; TXT/MD must carry no binary control byte in the inspected prefix, so Latin-1 text still passes), size ≤ `APP_MAX_FILE_SIZE_MB`, metadata via Bean Validation. Servlet upload limits are derived from those two settings, never configured separately.
- `GET /api/documents` (paginated, filter by status) · `GET /api/documents/:id` · `GET /api/documents/:id/content` (chunks, cursor-paginated, for the viewer) · `GET /api/documents/:id/file` (original, inline) · `GET /api/search` · `GET /api/events` · `GET /api/health` (db + rabbit).
- Errors: RFC 9457 `application/problem+json` from one `@RestControllerAdvice`, including the request id: 400 validation, 404, 413 too large, 415 unsupported type, 422 invalid batch (a duplicate upload lands here as a per-file `UNIQUE_CHECKSUM` error carrying `existingDocumentId`), 409 only for the concurrent-insert race on the unique index, 503 search timeout / dependency down.

## Frontend
- Routes: `/` search · `/upload` · `/documents` (list with live status) · `/documents/:id` viewer (`?q=` highlights terms and scrolls to the matched chunk).
- Upload: multi-file dropzone; per-file metadata table (title prefilled from filename; author/category/version/tags with "apply to all"); client validation mirroring the Bean Validation constraints; live status badges (PROCESANDO → INDEXADO / ERROR with reason).
- Search: 300 ms debounce, stale requests cancelled via the TanStack Query signal, filters, pagination, "N resultados en X ms", highlighted title and snippet, empty/loading/error states.
- Viewer: metadata panel; body by type (MD rendered with TOC, TXT pre-wrap, PDF per page); virtualized chunks for long documents.
- All user-facing text comes from `src/copy/es.ts`.

## Testing (critical backend components first)
- Backend unit (`*Test`, Surefire): JUnit 5 + AssertJ + Mockito. Upload validation, extractors (fixtures: MD, UTF-8 and Latin-1 TXT, text PDF, image-only PDF), chunkers, status transitions, exception handler mapping, use cases with in-memory fakes, MockMvc slices. ArchUnit rules enforcing the hexagonal boundaries.
- Backend integration (`*IT`, Failsafe): Testcontainers (real Postgres and RabbitMQ) with `@ServiceConnection`, Awaitility for async flows; never mock FTS. Upload → worker → INDEXADO → searchable; ERROR path; accent-insensitive match; phrase search; pagination; highlight sentinels; PROCESANDO docs never returned; SSE event only after commit; statement timeout → 503. Coverage with JaCoCo.
- Frontend: Vitest + Testing Library. Sentinel renderer, SSE hook cache updates, upload form validation.

## Seed & benchmark
- `samples/`: 15–20 realistic technical docs (MD, TXT, PDF), mostly in Spanish to demo accent-insensitive search, including one image-only PDF to demo `ERROR`.
- `pnpm seed` uploads samples through the API. `pnpm seed:bulk` runs a SQL script (`generate_series`) inside the postgres container to insert N synthetic documents/chunks for latency tests.
- `pnpm bench`: autocannon against `/api/search` with a query set; writes p50/p95/p99 plus `EXPLAIN (ANALYZE, BUFFERS)` output (GIN index scan) to `docs/benchmark.md`.

## CI/CD (GitHub Actions)
- `ci.yml` on `pull_request`, push to `main` and `workflow_dispatch`, concurrency with cancel-in-progress:
  - `quality`: pnpm install (cached), ESLint, Prettier check, TypeScript typecheck, `./mvnw -B spotless:check`, and a no-LIKE guard that fails if `\b(i?like|similar\s+to)\b` (case-insensitive) appears in `backend/src/main` (Java and SQL). Consequence: never use the English word "like" in backend code, comments or test names.
  - `commits` (PRs only): commitlint over the PR commit range + reject AI attribution (Claude/Anthropic co-author trailers, "Generated with Claude Code", `Claude-Session:`, claude.ai session links), using the same script as the hook.
  - `unit`: `./mvnw -B test` (Temurin 21, Maven cache) and frontend Vitest with coverage.
  - `integration` (from Phase 1): `./mvnw -B verify` with Failsafe + Testcontainers.
  - `contract`: regenerate `packages/shared` from the backend OpenAPI document and fail on any diff.
  - `build`: `./mvnw -B package`, build the frontend, and `docker build` each image without pushing (each image joins in the phase that creates its Dockerfile).
- `cd.yml` on push to `main`, tags `v*` and `workflow_dispatch`:
  - `publish`: build and push `backend` and `web` images to GHCR (tags: sha, latest, semver on tags) with GHA cache.
  - `deploy`: only if `vars.DEPLOY_ENABLED == 'true'`, environment `production`. SSH into a Linux VPS with Docker (secrets `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS`; variable `DEPLOY_PATH`): copy `docker-compose.prod.yml`, log in to GHCR with the job's `GITHUB_TOKEN`, run `IMAGE_TAG=<sha> docker compose pull && docker compose up -d --remove-orphans`, then smoke-test `/api/health` with retries (on failure: print logs and fail the job).
  - The VPS may already host other apps: never bind 80/443, expose only `WEB_PORT`; the README documents a reverse-proxy snippet for a subdomain. The production `.env` lives on the server, never in the repo.

## Config
`.env.example`, validated at boot, fail fast:
- Postgres: `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.
- RabbitMQ: `RABBITMQ_DEFAULT_USER`, `RABBITMQ_DEFAULT_PASS`, `SPRING_RABBITMQ_HOST`, `SPRING_RABBITMQ_PORT`, `SPRING_RABBITMQ_USERNAME`, `SPRING_RABBITMQ_PASSWORD`.
- Application: `APP_STORAGE_DIR`, `APP_MAX_FILE_SIZE_MB=20`, `APP_MAX_FILES_PER_UPLOAD=10`, `APP_SEARCH_TIMEOUT_MS=900`, `APP_SEARCH_MAX_PAGE_SIZE=50`, `APP_WORKER_CONCURRENCY=4`, `APP_STUCK_PROCESSING_MINUTES=10`, `APP_SSE_HEARTBEAT_MS=15000`.
- Ports: `API_PORT=8081`, `WEB_PORT=8080`.

## Agent toolkit
Development is driven through repository-scoped agents and skills under `.claude/`, committed so the workflow is reproducible by anyone who clones the repo.
- Agents: `backend-engineer` (Spring Boot features), `search-engineer` (PostgreSQL full-text path and latency), `frontend-engineer` (React SPA), `spec-auditor` (read-only audit of a branch against the spec).
- Skills: `verify` (every local gate CI also runs), `phase` (one roadmap phase end to end under the git and PR workflow), `contract-sync` (regenerate the shared TypeScript contract and report drift).
- Agents implement and report; the main session reviews, stages and commits. Agents never commit.
- `.claude/settings.local.json` stays gitignored; the agents and skills do not.

## Git & PR workflow (strict)
- Never push to `main` (only exception: the bootstrap commit of an empty repo). One branch + one PR per phase, created from an up-to-date `main`. Branches: `feat/…`, `chore/…`, `ci/…`, `docs/…`, `refactor/…`.
- Atomic commits, Conventional Commits: `type(scope): subject`, English, imperative, ≤ 72 chars; body only when the why isn't obvious. Scopes: shared, backend, worker, frontend, infra, ci, docs. Every commit builds and passes lint and its tests; tests ship in the same commit as the code they cover. Stage files explicitly.
- Open PRs with `gh pr create` using `.github/pull_request_template.md`: Summary · Spec coverage (HU / acceptance criteria) · Changes (one line per commit) · How to test · Decisions & trade-offs.
- After opening: `gh pr checks --watch`. Fix failures with `git commit --fixup <sha>`, then `GIT_SEQUENCE_EDITOR=: git rebase -i --autosquash origin/main` and `git push --force-with-lease`. No "fix CI" noise commits.
- When CI is green: STOP, summarize and wait. When I reply `merge`: `gh pr merge --rebase --delete-branch` (never squash: atomic commits must survive), `git switch main && git pull`, then propose the next phase.
- Never commit secrets, `.env`, `spec/` or `.claude/settings.local.json`.

## Docs
- `docs/architecture.md` (Spanish) with the spec's sections: "Diagrama de Arquitectura / Flujo" (Mermaid: Frontend, API, Worker, PostgreSQL FTS, RabbitMQ, SSE), "Justificación de Decisiones", "Estrategia de Tiempo Real", "Escalabilidad"; plus "Interpretación del SLA (400 ms – 1 s)", "Resultados de benchmark", "Trade-offs y fuera de alcance".
- `docs/ia.md` (Spanish): "Herramientas Utilizadas", "Casos de Uso de IA", "Prompts Clave", "Validación Humana". Append an entry in every PR (phase, key prompts, what was generated) and leave "Validación humana: TODO" for me. Never invent human validation.
- `README.md` (Spanish): prerequisites, quick start (`cp .env.example .env && docker compose up --build`), URLs, seed, tests, benchmark, deploy, troubleshooting, structure. Must work from a clean clone.

## Commands (root)
`pnpm dev` · `pnpm lint` · `pnpm format:check` · `pnpm typecheck` · `pnpm test` · `pnpm test:integration` · `pnpm build` · `pnpm contract:generate` · `pnpm seed` · `pnpm seed:bulk` · `pnpm bench`.
Root pnpm scripts orchestrate both stacks and call `./backend/mvnw` for the backend.

## Pitfalls (rejected in review)
ts_headline over all matches · any LIKE/ILIKE/regex fallback · processing inside the HTTP request or the API process · in-memory queues, `@Async` or bare executors as the job queue · polling or one EventSource per document · publishing messages inside the transaction · mocking Postgres in search tests · rendering snippets or markdown as raw HTML · translating status wire values · Spanish enum constant names · inline Spanish in components · artificial latency to "reach" 400 ms · SET with bind parameters · field injection · Lombok · JPA/Hibernate for search · AI attribution anywhere.

## Roadmap (one PR per phase)
0. `chore/bootstrap`: pnpm workspace, TS strict, lint/format tooling, husky hooks (commitlint + attribution check), `.gitignore` (incl. `spec/`, `.claude/settings.local.json`, `target/`), `.env.example`, Spring Boot skeleton with `/api/health` + test, Spotless, `packages/shared` generated from OpenAPI, Vite React skeleton + test, compose (postgres + rabbitmq with healthchecks), `ci.yml` (quality, commits, unit, contract, build), PR template, docs skeletons.
1. `feat/backend-foundation`: `@ConfigurationProperties`, `JdbcClient`, Flyway migrations, ProblemDetail advice, request id + structured logging, springdoc, health (db + rabbit), backend Dockerfile, ArchUnit, JaCoCo, CI integration job.
2. `feat/document-upload`: HU-01 upload endpoint, validation, storage, repository, the RabbitMQ topology (queue plus dead-letter exchange and queue) and job publish after commit, list/detail/content/file endpoints, tests.
3. `feat/document-processing`: worker profile, RabbitMQ consumers, extractors, chunkers, transactional indexing, ERROR path with error codes and dead-lettering, status event publish, reconciler, tests.
4. `feat/search`: HU-02 search endpoint, filters, statement timeout, tookMs + Server-Timing, integration tests.
5. `feat/realtime-events`: HU-04 SSE endpoint, RabbitMQ fanout, heartbeat, tests.
6. `feat/frontend-upload`: app shell, copy file, API client, global SSE provider, upload page, documents list with live status, web Dockerfile + nginx.
7. `feat/frontend-search-viewer`: search page (HU-02) and viewer (HU-03), tests.
8. `chore/seed-and-benchmark`: samples, seeds, benchmark, EXPLAIN output.
9. `ci/cd-pipeline`: `cd.yml` (GHCR publish + gated SSH deploy), `docker-compose.prod.yml`, README deploy section (server setup, secrets, reverse-proxy snippet).
10. `docs/final`: architecture.md, ia.md and README final; clean-clone verification.

## Out of scope (state it in architecture.md)
Auth, OCR, multi-tenancy, editing/deleting documents, i18n.

## Definition of done (every PR)
Lint, typecheck and tests green locally and in CI · docs updated if behavior or commands changed · `docs/ia.md` entry added · PR body maps changes to spec criteria · no AI attribution anywhere.
