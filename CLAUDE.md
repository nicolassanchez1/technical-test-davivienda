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
- Never add `Co-Authored-By`, "Generated with Claude Code", `Claude-Session` trailers or session links to commits, PR titles/bodies, code or docs. Any author field (package.json, docs) uses my git identity.
- AI usage is documented only in `docs/ia.md`, because the spec requires it.
- Enforced by the `commit-msg` hook and a CI check that reject those trailers.

## Code style
- 100% English and readable: descriptive names, no abbreviations, small focused functions, early returns, no dead code.
- Comments: only when the "why" isn't obvious, one short line. No commented-out code, no JSDoc that restates the signature, no banner comments.
- API messages, logs and errors in English. The worker stores an `errorCode` (`PDF_NO_TEXT_LAYER`, `UNSUPPORTED_FORMAT`, `CORRUPT_FILE`, `EMPTY_CONTENT`, `PROCESSING_FAILED`) that the UI maps to Spanish copy.
- The only Spanish inside code: status values, which are API contract (`enum DocumentStatus { Processing = 'PROCESANDO', Indexed = 'INDEXADO', Failed = 'ERROR' }`), and UI copy, centralized in `frontend/src/copy/es.ts` (components reference keys, never inline text).
- Spanish docs for the evaluators: `README.md`, `docs/architecture.md`, `docs/ia.md`, using the spec's exact section headings.
- Commits, branches, PRs and this file: English.

## Stack (approved; justify any new dependency in the PR body)
- pnpm workspaces, Node 24 LTS, TypeScript strict everywhere. Workspace scope = repo name.
- `packages/shared`: zod schemas (document metadata, search params), `DocumentStatus`, error codes, SSE event types, API DTOs. Built to ESM + CJS + d.ts (tsdown or tsup).
- `backend/`: NestJS (latest stable). One image, two entrypoints: `main.ts` (HTTP API) and `worker.ts` (BullMQ worker, no HTTP server).
- PostgreSQL 17 (`postgres:17-alpine`) + `unaccent`. `pg` with explicit SQL; migrations with node-pg-migrate. No ORM: the search SQL must stay explicit and EXPLAIN-able.
- Redis 7: BullMQ (`@nestjs/bullmq`) for jobs, Pub/Sub for event fan-out.
- pino (`nestjs-pino`) with request id; `@nestjs/swagger` at `/api/docs`.
- `frontend/`: React + Vite + TypeScript + Tailwind + TanStack Query + React Router; react-dropzone, react-markdown + remark-gfm + rehype-slug (never rehype-raw), react-virtuoso, sonner. Vite dev proxy `/api` → API.
- Tests: backend Jest + @swc/jest, supertest, Testcontainers (Postgres, Redis); frontend Vitest + Testing Library.
- Tooling: ESLint (flat) + Prettier + EditorConfig; husky with `commit-msg` (commitlint + attribution check) and `pre-commit` (lint-staged).
- Compose services: `postgres`, `redis`, `migrate` (one-shot), `api`, `worker`, `web` (nginx serving the SPA and proxying `/api`). Healthchecks + `depends_on` conditions (`service_healthy`, `service_completed_successfully`). Shared uploads volume between `api` and `worker`.
- Config (`.env.example`, validated with zod at boot, fail fast): DATABASE_URL, REDIS_URL, API_PORT, STORAGE_DIR, MAX_FILE_SIZE_MB=20, MAX_FILES_PER_UPLOAD=10, SEARCH_TIMEOUT_MS=900, SEARCH_MAX_PAGE_SIZE=50, WORKER_CONCURRENCY=4, JOB_ATTEMPTS=3, STUCK_PROCESSING_MINUTES=10, SSE_HEARTBEAT_MS=15000, WEB_PORT=8080.

## Backend architecture (lightweight hexagonal)
~~~
backend/src/
  main.ts            # HTTP API bootstrap
  worker.ts          # worker bootstrap, no HTTP
  config/            # env schema and typed config
  common/            # problem-details filter, request id, logging, pagination
  database/          # pg pool, transaction helper, migrations/
  documents/
    domain/          # Document, status transitions, domain errors
    application/     # use cases + ports: DocumentRepository, FileStorage, JobQueue, EventBus, TextExtractor
    infrastructure/  # Postgres repo, local storage, BullMQ queue, Redis event bus, extractors, chunkers
    http/            # controllers, request/response mapping
  search/            # SearchDocuments use case, SearchEngine port, PostgresSearchEngine, http/
  events/            # SSE controller, Redis subscriber → RxJS Subject
~~~
Ports keep the search engine, storage, queue and bus swappable (e.g. OpenSearchAdapter, S3Storage) without touching use cases. Use cases are unit-tested with in-memory fakes.

## Data model
- `documents`: id uuid, title, author, category (`MANUAL | SPECIFICATION | ARCHITECTURE_GUIDE | OTHER`), tags text[], version, original_filename, mime_type, size_bytes, storage_key, sha256 (unique), status, error_code, error_message, page_count, chunk_count, processing_ms, created_at, updated_at, indexed_at. Indexes: status, category, GIN(tags).
- `document_chunks`: id bigserial, document_id (FK, on delete cascade), chunk_index, page, heading, content, search_vector tsvector. GIN(search_vector); unique(document_id, chunk_index).
- Text search config (migration):
~~~sql
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE TEXT SEARCH CONFIGURATION es_unaccent (COPY = spanish);
ALTER TEXT SEARCH CONFIGURATION es_unaccent
  ALTER MAPPING FOR hword, hword_part, word WITH unaccent, spanish_stem;
~~~
- `search_vector` = title (weight A) || tags + category + author (B) || chunk content (C), all with `es_unaccent`.

## Processing pipeline
1. API: validate → store the file as `<uuid>.<ext>` (never the user filename) → INSERT with `PROCESANDO` → enqueue (jobId = documentId) → `202 { items: [{ id, filename, status }] }`, plus a `Location` header for single uploads.
2. Worker, separate process (parsing is CPU-bound and must never block the API event loop): extract → chunk → ONE transaction: delete existing chunks, batch-insert chunks with their tsvector, `UPDATE documents SET status = 'INDEXADO' ... WHERE id = $1 AND status = 'PROCESANDO'` → COMMIT → publish event. Never publish before commit.
3. Extraction: TXT → detect encoding (chardet), decode (iconv-lite). MD → keep raw markdown for the viewer, index stripped text, chunk by headings. PDF → per-page text (pdfjs-dist or unpdf); no extractable text → `ERROR` with `PDF_NO_TEXT_LAYER`.
4. Chunking: PDF per page, MD per h1–h3 section, TXT ~1,500 words at paragraph boundaries, always with a max chunk size. Why: ts_headline re-parses the whole text it receives, a tsvector is capped at 1 MB, and positions saturate at 16,383.
5. Failures: deterministic (corrupt, unsupported, empty) → BullMQ `UnrecoverableError` → `ERROR` + error code. Transient → JOB_ATTEMPTS with exponential backoff, then `ERROR`.
6. On boot, a reconciler re-enqueues documents stuck in `PROCESANDO` longer than STUCK_PROCESSING_MINUTES.
7. Duplicates: sha256 unique; a duplicate file gets a per-file error referencing the existing document id.

## Search
- `GET /api/search?q=&page=&pageSize=&category=&tags=&author=`; pageSize default 10, max SEARCH_MAX_PAGE_SIZE; cap deep offsets.
- `websearch_to_tsquery('es_unaccent', q)` gives quoted phrases, `or` and `-term`. Empty or stopword-only query → 400 with a clear message.
- Best chunk per document → paginate documents → ts_headline ONLY on the returned page. Apply metadata filters inside `best` via a join on documents:
~~~sql
WITH q AS (SELECT websearch_to_tsquery('es_unaccent', $1) AS query),
best AS (
  SELECT DISTINCT ON (c.document_id)
         c.document_id, c.id AS chunk_id, ts_rank_cd(c.search_vector, q.query) AS rank
  FROM document_chunks c, q
  WHERE c.search_vector @@ q.query
  ORDER BY c.document_id, rank DESC, c.chunk_index
),
page AS (
  SELECT *, count(*) OVER () AS total
  FROM best ORDER BY rank DESC LIMIT $2 OFFSET $3
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
- Run inside a transaction with `SELECT set_config('statement_timeout', $1, true)` (SET doesn't accept bind params), value = SEARCH_TIMEOUT_MS. SQLSTATE 57014 → 503 problem+json.
- Response includes `tookMs`; also send a `Server-Timing` header.

## Real-time (SSE)
- `GET /api/events` (Nest `@Sse`). Event type `document.status`, data `{ documentId, status, errorCode?, occurredAt }`, every event with an `id`. Heartbeat every SSE_HEARTBEAT_MS. Headers: `Cache-Control: no-cache`, `X-Accel-Buffering: no`.
- The worker publishes to Redis channel `documents.status`; every API instance subscribes and forwards to its own clients.
- Frontend: ONE global EventSource opened at app start (never one per document). On event → `queryClient.setQueryData` + toast. On (re)connect → one reconciliation fetch for tracked documents still in `PROCESANDO` (that is not polling).
- nginx for `/api/events`: `proxy_buffering off; proxy_cache off; proxy_http_version 1.1; proxy_set_header Connection ""; proxy_read_timeout 1h;`.

## API
- `POST /api/documents`: multipart `files` (1..MAX_FILES_PER_UPLOAD) + `metadata` (JSON array aligned by index). Validate everything first; any invalid file → 422 with per-file errors and nothing stored. Stream uploads to disk, not memory.
- Validation: extension allowlist (`.txt .md .markdown .pdf`), magic bytes (`%PDF-` for PDF; valid text without NUL bytes for TXT/MD), size ≤ MAX_FILE_SIZE_MB, metadata via the shared zod schema.
- `GET /api/documents` (paginated, filter by status) · `GET /api/documents/:id` · `GET /api/documents/:id/content` (chunks, cursor-paginated, for the viewer) · `GET /api/documents/:id/file` (original, inline) · `GET /api/search` · `GET /api/events` · `GET /api/health` (db + redis).
- Errors: RFC 9457 `application/problem+json` from one global exception filter, including the request id: 400 validation, 404, 409 duplicate, 413 too large, 415 unsupported type, 422 invalid batch, 503 search timeout / dependency down.

## Frontend
- Routes: `/` search · `/upload` · `/documents` (list with live status) · `/documents/:id` viewer (`?q=` highlights terms and scrolls to the matched chunk).
- Upload: multi-file dropzone; per-file metadata table (title prefilled from filename; author/category/version/tags with "apply to all"); client validation with the shared schema; live status badges (PROCESANDO → INDEXADO / ERROR with reason).
- Search: 300 ms debounce, stale requests cancelled via the TanStack Query signal, filters, pagination, "N resultados en X ms", highlighted title and snippet, empty/loading/error states.
- Viewer: metadata panel; body by type (MD rendered with TOC, TXT pre-wrap, PDF per page); virtualized chunks for long documents.
- All user-facing text comes from `src/copy/es.ts`.

## Testing (critical backend components first)
- Unit: upload validation, extractors (fixtures: MD, UTF-8 and Latin-1 TXT, text PDF, image-only PDF), chunkers, status transitions, exception filter mapping, use cases with in-memory fakes.
- Integration with Testcontainers (real Postgres and Redis; never mock FTS): upload → worker → INDEXADO → searchable; ERROR path; accent-insensitive match; phrase search; pagination; highlight sentinels; PROCESANDO docs never returned; SSE event only after commit; statement timeout → 503.
- Frontend: sentinel renderer, SSE hook cache updates, upload form validation.

## Seed & benchmark
- `samples/`: 15–20 realistic technical docs (MD, TXT, PDF), mostly in Spanish to demo accent-insensitive search, including one image-only PDF to demo `ERROR`.
- `pnpm seed` uploads samples through the API; `pnpm seed:bulk` inserts N synthetic documents/chunks directly for latency tests.
- `pnpm bench`: autocannon against `/api/search` with a query set; writes p50/p95/p99 plus `EXPLAIN (ANALYZE, BUFFERS)` output (GIN index scan) to `docs/benchmark.md`.

## CI/CD (GitHub Actions)
- `ci.yml` on `pull_request`, push to `main` and `workflow_dispatch`, concurrency with cancel-in-progress:
  - `quality`: install (pnpm cache), lint, format check, typecheck, and a no-LIKE guard that fails if `\b(i?like|similar\s+to)\b` (case-insensitive) appears in `backend/src`. Consequence: never use the English word "like" in backend code, comments or test names.
  - `commits` (PRs only): commitlint over the PR commit range + reject AI attribution (Claude/Anthropic co-author trailers, "Generated with Claude Code", `Claude-Session:`, claude.ai session links), using the same script as the hook.
  - `unit`: shared, backend and frontend unit tests with coverage.
  - `integration` (from Phase 1): backend integration tests with Testcontainers.
  - `build`: build all workspaces and `docker build` each image without pushing (each image joins in the phase that creates its Dockerfile).
- `cd.yml` on push to `main`, tags `v*` and `workflow_dispatch`:
  - `publish`: build and push `backend` and `web` images to GHCR (tags: sha, latest, semver on tags) with GHA cache.
  - `deploy`: only if `vars.DEPLOY_ENABLED == 'true'`, environment `production`. SSH into a Linux VPS with Docker (secrets `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS`; variable `DEPLOY_PATH`): copy `docker-compose.prod.yml`, log in to GHCR with the job's `GITHUB_TOKEN`, run `IMAGE_TAG=<sha> docker compose pull && docker compose up -d --remove-orphans`, then smoke-test `/api/health` with retries (on failure: print logs and fail the job).
  - The VPS may already host other apps: never bind 80/443, expose only `WEB_PORT`; the README documents a reverse-proxy snippet for a subdomain. The production `.env` lives on the server, never in the repo.

## Git & PR workflow (strict)
- Never push to `main` (only exception: the bootstrap commit of an empty repo). One branch + one PR per phase, created from an up-to-date `main`. Branches: `feat/…`, `chore/…`, `ci/…`, `docs/…`.
- Atomic commits, Conventional Commits: `type(scope): subject`, English, imperative, ≤ 72 chars; body only when the why isn't obvious. Scopes: shared, backend, worker, frontend, infra, ci, docs. Every commit builds and passes lint and its tests; tests ship in the same commit as the code they cover. Stage files explicitly.
- Open PRs with `gh pr create` using `.github/pull_request_template.md`: Summary · Spec coverage (HU / acceptance criteria) · Changes (one line per commit) · How to test · Decisions & trade-offs.
- After opening: `gh pr checks --watch`. Fix failures with `git commit --fixup <sha>`, then `GIT_SEQUENCE_EDITOR=: git rebase -i --autosquash origin/main` and `git push --force-with-lease`. No "fix CI" noise commits.
- When CI is green: STOP, summarize and wait. When I reply `merge`: `gh pr merge --rebase --delete-branch` (never squash: atomic commits must survive), `git switch main && git pull`, then propose the next phase.
- Never commit secrets, `.env`, `spec/` or `.claude/settings.local.json`.

## Docs
- `docs/architecture.md` (Spanish) with the spec's sections: "Diagrama de Arquitectura / Flujo" (Mermaid: Frontend, API, Worker, PostgreSQL FTS, Redis, SSE), "Justificación de Decisiones", "Estrategia de Tiempo Real", "Escalabilidad"; plus "Interpretación del SLA (400 ms – 1 s)", "Resultados de benchmark", "Trade-offs y fuera de alcance".
- `docs/ia.md` (Spanish): "Herramientas Utilizadas", "Casos de Uso de IA", "Prompts Clave", "Validación Humana". Append an entry in every PR (phase, key prompts, what was generated) and leave "Validación humana: TODO" for me. Never invent human validation.
- `README.md` (Spanish): prerequisites, quick start (`cp .env.example .env && docker compose up --build`), URLs, seed, tests, benchmark, deploy, troubleshooting, structure. Must work from a clean clone.

## Commands (root)
`pnpm dev` · `pnpm lint` · `pnpm format:check` · `pnpm typecheck` · `pnpm test` · `pnpm test:integration` · `pnpm build` · `pnpm seed` · `pnpm seed:bulk` · `pnpm bench`

## Pitfalls (rejected in review)
ts_headline over all matches · any LIKE/ILIKE/regex fallback · processing inside the HTTP request or the API process · in-memory queues · polling or one EventSource per document · publishing before commit · mocking Postgres in search tests · rendering snippets or markdown as raw HTML · translating status values · inline Spanish in components · artificial latency to "reach" 400 ms · SET with bind params · AI attribution anywhere.

## Roadmap (one PR per phase)
0. `chore/bootstrap`: pnpm workspace, TS strict, lint/format tooling, husky hooks (commitlint + attribution check), `.gitignore` (incl. `spec/`, `.claude/settings.local.json`), `.env.example`, `packages/shared` with `DocumentStatus` + test, NestJS skeleton with `/api/health` + test, Vite React skeleton + test, compose (postgres + redis with healthchecks), `ci.yml` (quality, commits, unit, build), PR template, docs skeletons.
1. `feat/backend-foundation`: env validation, pg pool, migrations + `migrate` service, problem-details filter, request id + pino, Swagger, health (db + redis), backend Dockerfile, CI integration job.
2. `feat/document-upload`: HU-01 upload endpoint, validation, storage, repository, enqueue, list/detail/content endpoints, tests.
3. `feat/document-processing`: worker, extractors, chunkers, transactional indexing, ERROR path with error codes, event publish, reconciler, tests.
4. `feat/search`: HU-02 search endpoint, filters, timeout, tookMs + Server-Timing, integration tests.
5. `feat/realtime-events`: HU-04 SSE endpoint, Redis fan-out, heartbeat, tests.
6. `feat/frontend-upload`: app shell, copy file, API client, global SSE provider, upload page, documents list with live status, web Dockerfile + nginx.
7. `feat/frontend-search-viewer`: search page (HU-02) and viewer (HU-03), tests.
8. `chore/seed-and-benchmark`: samples, seeds, benchmark, EXPLAIN output.
9. `ci/cd-pipeline`: `cd.yml` (GHCR publish + gated SSH deploy), `docker-compose.prod.yml`, README deploy section (server setup, secrets, reverse-proxy snippet).
10. `docs/final`: architecture.md, ia.md and README final; clean-clone verification.

## Out of scope (state it in architecture.md)
Auth, OCR, multi-tenancy, editing/deleting documents, i18n.

## Definition of done (every PR)
Lint, typecheck and tests green locally and in CI · docs updated if behavior or commands changed · `docs/ia.md` entry added · PR body maps changes to spec criteria · no AI attribution anywhere.
