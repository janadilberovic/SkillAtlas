---
description: Prove a change works — Neo4j preflight, mvnw verify, live endpoints, frontend build
allowed-tools: Bash(./mvnw:*), Bash(.\mvnw.cmd:*), Bash(curl.exe:*), Bash(npm:*), Bash(git:*), Bash(docker compose ps:*), Read, Glob, Grep
---

Branch:        !`git rev-parse --abbrev-ref HEAD`
Changed files: !`git diff --name-only main...HEAD | head -30`
Neo4j (7474):  !`curl.exe -s -o /dev/null -w "%{http_code}" --max-time 3 http://localhost:7474 2>&1`
App (8080):    !`curl.exe -s --max-time 3 http://localhost:8080/actuator/health 2>&1 | head -c 200`

---

Prove the current change actually works. Report what happened — this command never edits anything,
so a red result is the answer, not a problem to route around.

## 1. Preflight — is there a database?

The Neo4j line above should be `200`. If it is `000` or empty, **stop before running `verify`**: a
dead Bolt connection makes every integration test fail in a way that reads like broken code, and
the next twenty minutes go into diagnosing a slice that is fine.

Start it (`docker compose up -d neo4j`, or Neo4j Desktop) and say so, rather than reporting the
resulting red suite as a test failure.

## 2. `.\mvnw.cmd -B verify`

Both stages, and they mean different things:

- **surefire** runs `*Test` — pure Mockito, no database. Fast.
- **failsafe** runs `*IT extends AbstractNeo4jIT` — real Cypher against the local Neo4j.

Note that the ITs run against **whatever local database is configured**, which may hold real data.
They force `skillatlas.seed.enabled=false` and clean up after themselves, but a failure that leaves
fixtures behind is worth mentioning in the report.

## 3. Exercise the endpoints for real

Compilation and green tests do not catch everything:

- Neo4j entity mapping and derived-query parsing are validated at **startup**, not at compile time.
- Authorization only shows under a live request.

If the app line above is already `"status":"UP"`, use that instance. Otherwise boot it (the
`backend` entry in `.claude/launch.json`, or `.\mvnw.cmd -q spring-boot:run` in a background job)
and wait for `Started SkillatlasApplication`.

Log in as the seeded admin to get a token:

```
curl.exe -s -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d "{\"email\":\"admin@skillatlas.dev\",\"password\":\"Password123!\"}"
```

Then curl every endpoint the diff touched, and **explicitly** the paths that are easy to assume:

| Case | Expected |
|---|---|
| no `Authorization` header | 401 |
| MEMBER token on an admin-only endpoint | 403 |
| someone else's resource on an ownership-checked endpoint | 403 |
| unknown or soft-deleted id | 404 |
| `?size=100000` on a list | capped at 100, not the whole table |

## 4. Frontend, if the diff touches it

`npm run build` in `frontend/`. It defaults to the production configuration, so it runs the full
AOT `strictTemplates` typecheck and the bundle budgets — a template error that `ng serve` tolerates
fails here. There is no test framework in `frontend/`; this build is the whole gate.

## 5. Report

State plainly what passed, what failed, and paste the actual failure output. If something was not
run — no database, no frontend change, endpoint not reachable — say which and why, rather than
leaving it implied.
