---
description: Run one roadmap slice end to end, from branch off main to open PR
argument-hint: <roadmap step number, e.g. 4>
allowed-tools: Bash(git:*), Bash(gh:*), Bash(./mvnw:*), Bash(.\mvnw.cmd:*), Bash(curl.exe:*), Bash(npm:*), Read, Edit, Write, Glob, Grep
---

Current branch: !`git rev-parse --abbrev-ref HEAD`
Working tree:   !`git status --short`
Recent commits: !`git log --oneline -5`
Open PRs:       !`gh pr list --state open`

Roadmap step to build: **$1**

---

Build roadmap step $1 as one feature slice, following the loop below. The branching rules are in
`CLAUDE.md` § *Git workflow* — read them there rather than assuming; they are the reason this
command exists.

## 1. Preflight — stop rather than improvise

- If the working tree above is **not clean**, stop and ask what to do with the changes. Never
  stash, commit, or discard someone else's work to clear the way.
- If an open PR above already covers step $1, say so and stop.
- If an open PR touches the same files this step will touch, name the overlap and ask before
  branching — landing two PRs into the same files is how the merge conflict gets discovered at
  the worst moment.

## 2. Read before writing

- `docs/roadmap.md` — step $1: what it is, its status, and any decisions already recorded for it.
  Decisions marked "not yet confirmed" are **proposals, not permission** — surface them in step 3.
- `docs/spec.md` — the section the roadmap points at. This is the source of truth for fields,
  rules, and the API shape. If the spec is thin or ambiguous where it matters, that is a question
  for the user, not a gap to fill with a guess.

## 3. Plan, then wait

Present a short plan: endpoints and their access rules, the Cypher shape, files to add, the
security cases the spec mandates for this slice, and anything the spec leaves open. Then **stop and
wait for confirmation.** Do not branch or write code before the user answers.

## 4. Branch

```
git checkout main && git pull && git checkout -b <type>/<slug>
```

Fresh off `main`, never off another feature branch.

## 5. Implement

The `new-feature` skill covers the layer rules, the graph-modelling traps, and the test
requirements — follow it. Build bottom-up: domain → repository → service → controller → dto.

**Commit at natural milestones, not only at the end** (e.g. once the repository and service are in
place and compiling, then again after the controller and tests). A long slice can otherwise lose a
lot of work to one bad turn.

## 6. Prove it

- `.\mvnw.cmd -B verify` green — unit tests and integration tests both.
- Boot the app against the local Neo4j and curl the new endpoints, including the 401/403/404
  paths. Compilation and green tests do not catch Neo4j mapping failures at startup.
- If the slice touches `frontend/`, `npm run build` in `frontend/` as well.

Report failures honestly with their output. A red `verify` is a result to show, not a problem to
work around.

## 7. Open the PR

```
gh pr create --base main
```

Body shape, matching the PRs already in this repo:

- **Why** — the problem, in the reviewer's terms.
- **What changed** — by area (backend / frontend / tests), with the non-obvious decisions and the
  reasoning behind them.
- **Reviewer notes** — anything deliberate that looks wrong at a glance, anything deferred and why.
- **Security** — which of the mandated cases (injection, IDOR, soft delete, mass assignment) apply
  to this slice and how each is covered.
- **Tests** — counts and what they actually assert.

Then update the step's status in `docs/roadmap.md` and include it in the PR.

## 8. Watch, report, stop

`gh pr checks --watch`. Report the result — the PR link, what the checks said, what is worth the
reviewer's attention first.

**Then stop.** Do not merge: CODEOWNERS routes every PR to the mentor, and the review is the point
of the loop, not an obstacle in it.
