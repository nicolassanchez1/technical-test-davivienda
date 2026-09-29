---
name: verify
description: Run every gate this repository enforces — lint, format, typecheck, unit and integration tests, contract drift, the unindexed-scan guard, authorship, and a clean worktree — and report a single pass or fail. Use before pushing, before opening a pull request, and whenever you need to trust the working tree.
---

# verify

Runs the same checks CI runs, locally, so a red pipeline is a surprise rather than a habit.

## Setup

Both toolchains must be on PATH. The login shell resolves an older Node and a Java 17
`JAVA_HOME`, so export them explicitly:

```bash
export PATH="$HOME/.nvm/versions/node/v24.21.0/bin:$PATH"
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

Integration tests need Docker running.

## The gates

Run them in this order and keep going after a failure so the report is complete.

```bash
# 1. Lint: ESLint over TypeScript, Spotless over Java
pnpm lint

# 2. Format
pnpm format:check

# 3. Types
pnpm typecheck

# 4. Unit tests: Surefire plus Vitest, no Docker needed
pnpm test

# 5. Integration tests: Failsafe with Testcontainers, Docker needed
pnpm test:integration

# 6. Contract drift: regenerate from the backend OpenAPI document and diff
pnpm contract:generate
git diff --exit-code -- packages/shared

# 7. Unindexed-scan guard, the same grep CI uses
grep -rniE '\b(i?like|similar[[:space:]]+to)\b' backend/src/main && echo VIOLATION || echo clean

# 8. Authorship over the branch
node scripts/check-attribution.mjs --range main..HEAD

# 9. Conventional Commits over the branch
pnpm exec commitlint --from main --to HEAD

# 10. Nothing stray, nothing secret
git status --short
git ls-files | grep -E '^(spec/|\.env$|\.claude/settings)' && echo LEAK || echo clean
```

## Reporting

State `PASS` or `FAIL` first, then one line per gate. On failure, quote the shortest
decisive line from the output — never paste a whole stack trace. Name the file and line a
reader should open.

Gate 6 failing means `packages/shared` is stale: the fix is to commit the regenerated
contract, not to skip the check. Gate 7 failing is never a formatting problem; it means
either a genuine unindexed scan or the English word "like" leaked into
`backend/src/main`, and both must be removed.
