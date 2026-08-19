---
description: Run one roadmap slice end to end, from branch off main to open PR
argument-hint: <roadmap step number, e.g. 4>
allowed-tools: Bash(git:*), Bash(gh:*), Bash(./mvnw:*), Bash(.\mvnw.cmd:*), Bash(curl.exe:*), Bash(npm:*), Read, Edit, Write, Glob, Grep, Agent, Skill
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

Run **`/verify`**. It does the Neo4j preflight (a dead Bolt connection reads exactly like a broken
slice), `.\mvnw.cmd -B verify`, the live endpoints including the 401/403/404 paths, and the
frontend build when the diff touches `frontend/`. It has no `Edit` or `Write`, so it cannot work
its way to green — its result is the result.

Then run the **`spec-checker`** agent on step $1. It reads the spec section and the numbered
*Odluke* lists from this and every earlier step, and reports criterion by criterion:

- *missing* / *deviates* → work, not commentary.
- *contradicts an earlier decision* → stop and fix, or say why the decision no longer holds.
- *spec is silent* → a question for the user. Answer it before the PR; do not settle it silently.

Report failures honestly with their output. A red `verify` is a result to show, not a problem to
work around.

## 7. Open the PR

Fill [`.github/pull_request_template.md`](../../.github/pull_request_template.md) — Why, What
changed, Reviewer notes, Security, Tests, Roadmap — write it to a file, and pass that file:

```
gh pr create --base main --body-file <filled template>
```

**`--body-file`, not `--body`.** Passing `--body` bypasses the template entirely, which is how a
PR ends up missing the Security table nobody noticed was gone.

For the **Security** section, run the **`security-reviewer`** agent. It runs the six checks spec §5
mandates and returns the table in the shape the template expects, with `file:line` evidence for
every "covered". Read its *rejected candidates* section as well — §5 puts the judgement on the
human, so the rejections are yours to confirm, not the agent's to bury.

Then update the step's status in `docs/roadmap.md` and include it in the PR.

## 8. Watch, report, stop

`gh pr checks --watch`. Report the result — the PR link, what the checks said, what is worth the
reviewer's attention first.

**Then stop.** Do not merge: CODEOWNERS routes every PR to the mentor, and the review is the point
of the loop, not an obstacle in it.
