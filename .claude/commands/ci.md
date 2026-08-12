---
description: Watch CI on the current PR; on failure, read the logs and propose a fix
allowed-tools: Bash(git:*), Bash(gh:*), Bash(./mvnw:*), Bash(.\mvnw.cmd:*), Read, Edit, Glob, Grep
---

Branch: !`git rev-parse --abbrev-ref HEAD`
Checks: !`gh pr checks 2>&1 | head -20`

---

Watch CI for the pull request on this branch and report the outcome.

1. `gh pr checks --watch` until every job settles. (Blocking wait, no polling loop — this costs
   nothing while it waits.)
2. **All green** → say so with the job names and stop.
3. **Anything red** → `gh run view --log-failed` for the failing job, find the actual failure in the
   log (the first real error, not the last line), and explain what broke and why.
   - If the fix is obvious and small, propose it — show the diff and ask before applying.
   - If it looks like infrastructure rather than the code (Neo4j container failing to start, a
     network timeout pulling the Maven distribution), say so plainly and suggest a re-run instead
     of inventing a code change to chase it.
   - Failed runs upload `target/*-reports/` as an artifact; use it when the log alone is not enough.

Never merge, and never push a fix without showing it first.
