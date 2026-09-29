---
name: contract-sync
description: Regenerate the shared TypeScript API contract from the backend OpenAPI document and report any drift. Use after changing a controller, a request or response record, or a Bean Validation constraint, and whenever the contract CI job fails.
---

# contract-sync

`packages/shared` is generated, never hand-edited. The Java DTOs are the single source of
truth; a drifting contract fails CI rather than silently reaching the frontend.

## How it flows

1. `OpenApiContractIT` boots the application, fetches `/api/v3/api-docs`, strips the
   `servers` node — it carries the random test port and would make the output differ on
   every run — and writes `packages/shared/openapi.json`.
2. `openapi-typescript` turns that into `packages/shared/src/api.d.ts`.
3. The `contract` CI job runs both and fails on any diff.

## Regenerate

```bash
export PATH="$HOME/.nvm/versions/node/v24.21.0/bin:$PATH"
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

pnpm contract:generate
git diff --stat -- packages/shared
```

Docker must be running: the integration test that exports the document uses Testcontainers.

## Reading the result

- **No diff** — the committed contract already matches the backend. Nothing to do.
- **A diff you expected** — commit it in the same commit as the backend change that caused
  it, so the two never travel apart.
- **A diff you did not expect** — the backend changed shape without anyone noticing. Find
  out which record or annotation moved before committing.

## Rules

- Never edit `packages/shared/openapi.json` or `packages/shared/src/api.d.ts` by hand. Both
  are in `.prettierignore` and out of ESLint's scope for exactly this reason.
- Never hand-write a TypeScript interface that mirrors a Java record. If the frontend needs
  a type that is missing, the backend contract is what needs fixing.
- An empty `paths` object is correct while no controller exists yet; it fills in as
  endpoints land.
