# SkillAtlas — conventions

Graph platform for mapping a company's knowledge (people ↔ skills ↔ projects ↔ teams).
Stack: **Java 21 + Spring Boot 3 + Spring Data Neo4j**. The database is a graph (Neo4j), not relational.

> **Before starting a task, check the project spec** — [`docs/spec.md`](docs/spec.md), extracted from `SkillAtlas_Dokumentacija_za_praktikante.pdf` (root, authoritative for figures and tables). It is the source of truth for domain fields, business rules, feature scope, and the API catalog. When a request is ambiguous or looks incomplete, read the relevant spec section before writing code.
>
> Build order and what is already done: [`docs/roadmap.md`](docs/roadmap.md).

## Architecture
Feature-based packages under `com.skillatlas.<feature>` (people, skills, projects, teams, finder, graph, mentoring).
Layers within a feature:

- `*Controller` — REST, `/api/v1/...`, input validation, mapping to DTOs. **No Cypher.**
- `*Service` — business rules.
- `*Repository` — **the only place Cypher is allowed.** Spring Data Neo4j interface (+ `@Query` when needed).
- `dto/` — input/output shapes + Bean Validation (`@Valid`, `@NotNull`, `@Min`...).

## Graph modeling (careful!)
- `level` (1–5) is a **property on the `KNOWS` relationship**, not on the `Person` or `Skill` node.
- Nodes: `Person`, `Skill`, `Project`, `Team`. Relationships: `KNOWS`, `WANTS_TO_LEARN`, `WORKED_ON`, `USES`, `MEMBER_OF`, `MENTORS`.
- Do not add nodes/relationships for features you are not building yet.

## Business rules (key)
- **Soft delete**: people are never hard-deleted (`isDeleted` + `deletedAt`). Every read query must include a soft-delete filter.
- Level is an integer 1–5; out of range → validation error (server-side too).
- Import from VacaYAY is **idempotent** — dedup by email (`MERGE`, not `CREATE`).

## Security (not optional)
- **Cypher injection**: always use parameters (`$param`), never string concat. Test: the input `React'}) DETACH DELETE (n) //` through search must not delete anything.
- **IDOR**: ownership check on the server (`id == currentUserId` from the token); a role is not enough.
- **Mass assignment**: bind to a DTO; `role`/`verified` are not set through the profile endpoint.
- Passwords: bcrypt/argon2, never plain, never in logs.
- Neo4j credentials via env (`.env`), never in the repo nor in the agent's context as a plain value.

## Non-functional
- Server-side validation on every write.
- Pagination on every list; graph endpoint with `LIMIT`.
- No N+1 (calling the database inside a loop). Unique constraints on `Person.email` and `Skill.name`.

## Git workflow (branching)
- **Always branch each feature directly off `main`.** One feature = one branch = one PR targeting `main`.
- **Never stack branches** — do not open a branch off another feature branch, and never point a PR's base at anything other than `main` (no "PR into PR", no nested/stacked PRs).
- Keep branches short-lived: merge to `main`, then branch the next feature fresh from the updated `main`.
- Before starting a feature, `git checkout main && git pull` so the new branch starts from the latest `main`.
