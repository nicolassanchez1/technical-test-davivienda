---
name: phase
description: Execute one roadmap phase end to end under this repository's Git and pull request workflow — branch, atomic commits, full verification, push, pull request, and stop when CI is green. Use when starting or resuming a numbered phase from the roadmap in CLAUDE.md.
---

# phase

One phase, one branch, one pull request. The roadmap lives at the end of `CLAUDE.md`.

## Before touching anything

```bash
git switch main && git pull --ff-only
git status --short          # must be empty
```

Read the phase entry in `CLAUDE.md` and the matching acceptance criteria in the spec PDF.
Show a plan of at most ten lines: the branch name and one line per intended commit. Then
proceed.

## Branch

`feat/…`, `chore/…`, `ci/…`, `docs/…` or `refactor/…`, always cut from an up-to-date
`main`.

## Commits

Conventional Commits, English, imperative, subject at most 72 characters, lower case. Scope
is one of `shared`, `backend`, `worker`, `frontend`, `infra`, `ci`, `docs`, or omitted.
Add a body only when the reason is not obvious from the subject.

Rules that matter more than they look:

- **Stage explicitly.** Never `git add -A` or `git add .`. The spec PDF and the local agent
  settings must never be committed.
- **Every commit builds and passes its own tests.** Tests ship in the same commit as the
  code they cover.
- **No AI attribution** in any commit message, code comment, document, or pull request
  body. The `commit-msg` hook and a CI job both reject it.
- Delegate implementation to the agent that owns the area — `backend-engineer`,
  `search-engineer`, `frontend-engineer` — then review, stage and commit yourself.

## Verify before pushing

Run the `verify` skill. Do not push on a failing gate.

## Pull request

```bash
git push -u origin <branch>
gh pr create --base main --head <branch> --title "<type(scope): subject>" --body-file <file>
```

The body follows `.github/pull_request_template.md`: Summary, Spec coverage mapping each
change to a user story or checklist item, Changes with one line per commit, How to test,
and Decisions and trade-offs. Justify every new dependency there. Consider running the
`spec-auditor` agent over the branch first and folding its findings in.

## CI

```bash
gh pr checks <number> --watch
```

Fix a failure with `git commit --fixup <sha>`, then:

```bash
GIT_SEQUENCE_EDITOR=: git rebase -i --autosquash origin/main
git push --force-with-lease
```

Never add a "fix CI" commit.

## Stop

When every check passes, **stop**. Report the pull request URL, the commit list, the CI
status, and anything the user must do by hand. Wait for them to reply `merge`. Only then:

```bash
gh pr merge <number> --rebase --delete-branch   # never squash: atomic commits must survive
git switch main && git pull --ff-only && git fetch --prune
```

Then propose the next phase.
